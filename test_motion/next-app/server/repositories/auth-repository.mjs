import { createHash, randomBytes, scrypt, timingSafeEqual } from "node:crypto"
import { promisify } from "node:util"

import { query, withTransaction } from "../db/mysql.mjs"

const scryptAsync = promisify(scrypt)
const SCRYPT_N = 16384
const SCRYPT_R = 8
const SCRYPT_P = 1
const SCRYPT_KEY_LENGTH = 64
const SCRYPT_MAX_MEM = 64 * 1024 * 1024
const SESSION_DAYS = Number(process.env.AUTH_SESSION_DAYS ?? 30)
const DAY_MS = 24 * 60 * 60 * 1000

function httpError(statusCode, message) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function normalizeString(value) {
  return typeof value === "string" ? value.trim() : ""
}

function normalizeEmail(value) {
  return normalizeString(value).toLowerCase()
}

function clip(value, maxLength) {
  return typeof value === "string" && value.trim()
    ? value.slice(0, maxLength)
    : null
}

function serializeUser(row) {
  return {
    avatarUrl: row.avatar_url ?? null,
    createdAt: row.created_at,
    email: row.email ?? null,
    id: Number(row.id),
    lastLoginAt: row.last_login_at ?? null,
    phone: row.phone ?? null,
    status: Number(row.status),
    username: row.username,
  }
}

async function findUserById(id) {
  const rows = await query(
    `SELECT id, username, phone, email, avatar_url, status, last_login_at, created_at
     FROM users
     WHERE id = ?
     LIMIT 1`,
    [id]
  )

  return rows[0] ? serializeUser(rows[0]) : null
}

async function findUserForLogin(identifier) {
  const rows = await query(
    `SELECT id, username, phone, email, password_hash, avatar_url, status, last_login_at, created_at
     FROM users
     WHERE username = ? OR email = ? OR phone = ?
     LIMIT 1`,
    [identifier, identifier, identifier]
  )

  return rows[0] ?? null
}

async function createSession(connection, userId) {
  const token = randomBytes(32).toString("base64url")
  const tokenHash = createHash("sha256").update(token).digest("hex")
  const expiresAt = new Date(Date.now() + SESSION_DAYS * DAY_MS)

  await connection.execute(
    `INSERT INTO user_sessions (user_id, token_hash, expires_at)
     VALUES (?, ?, ?)`,
    [userId, tokenHash, expiresAt]
  )

  return token
}

async function createPasswordHash(password) {
  const salt = randomBytes(16).toString("base64url")
  const derivedKey = await deriveScryptKey(password, Buffer.from(salt, "base64url"))

  return [
    "scrypt",
    SCRYPT_N,
    SCRYPT_R,
    SCRYPT_P,
    salt,
    Buffer.from(derivedKey).toString("base64url"),
  ].join("$")
}

async function deriveScryptKey(password, salt, keyLength = SCRYPT_KEY_LENGTH) {
  return scryptAsync(password, salt, keyLength, {
    N: SCRYPT_N,
    maxmem: SCRYPT_MAX_MEM,
    p: SCRYPT_P,
    r: SCRYPT_R,
  })
}

async function verifyPassword(password, passwordHash) {
  const [scheme, nValue, rValue, pValue, saltValue, hashValue] =
    String(passwordHash).split("$")

  if (scheme !== "scrypt" || !saltValue || !hashValue) {
    return { matches: false, needsRehash: false }
  }

  const expectedKey = Buffer.from(hashValue, "base64url")
  const scryptOptions = {
    N: Number(nValue),
    maxmem: SCRYPT_MAX_MEM,
    p: Number(pValue),
    r: Number(rValue),
  }

  const actualKey = await scryptAsync(
    password,
    Buffer.from(saltValue, "base64url"),
    expectedKey.length,
    scryptOptions
  )

  if (
    actualKey.length === expectedKey.length &&
    timingSafeEqual(actualKey, expectedKey)
  ) {
    return { matches: true, needsRehash: false }
  }

  const legacyAndroidKey = await scryptAsync(
    password,
    saltValue,
    expectedKey.length,
    scryptOptions
  )

  return {
    matches:
      legacyAndroidKey.length === expectedKey.length &&
      timingSafeEqual(legacyAndroidKey, expectedKey),
    needsRehash: true,
  }
}

export async function loginUser(payload, context = {}) {
  const identifier = normalizeString(payload?.identifier)
  const password = normalizeString(payload?.password)

  if (!identifier || !password) {
    throw httpError(400, "Please enter your account and password.")
  }

  const userRow = await findUserForLogin(identifier)
  if (!userRow) {
    throw httpError(401, "Account or password is incorrect.")
  }

  if (Number(userRow.status) !== 1) {
    throw httpError(403, "This account has been disabled.")
  }

  const passwordVerification = await verifyPassword(password, userRow.password_hash)
  if (!passwordVerification.matches) {
    throw httpError(401, "Account or password is incorrect.")
  }

  const token = await withTransaction(async (connection) => {
    const sessionToken = await createSession(connection, userRow.id)
    if (passwordVerification.needsRehash) {
      await connection.execute(
        "UPDATE users SET password_hash = ? WHERE id = ?",
        [await createPasswordHash(password), userRow.id]
      )
    }
    await connection.execute(
      "UPDATE users SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
      [userRow.id]
    )
    await connection.execute(
      `INSERT INTO user_login_logs (user_id, login_ip, device_info)
       VALUES (?, ?, ?)`,
      [userRow.id, clip(context.ip, 45), clip(context.userAgent, 255)]
    )
    return sessionToken
  })

  return {
    token,
    user: (await findUserById(userRow.id)) ?? serializeUser(userRow),
  }
}

export async function registerUser(payload, context = {}) {
  const username = normalizeString(payload?.username)
  const email = normalizeEmail(payload?.email)
  const password = normalizeString(payload?.password)

  if (!username) {
    throw httpError(400, "Please enter a username.")
  }

  if (!email) {
    throw httpError(400, "Please enter an email address.")
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw httpError(400, "Please enter a valid email address.")
  }

  if (password.length < 6) {
    throw httpError(400, "Password must be at least 6 characters.")
  }

  const passwordHash = await createPasswordHash(password)

  try {
    const { token, userId } = await withTransaction(async (connection) => {
      const [result] = await connection.execute(
        `INSERT INTO users (username, email, password_hash, status, last_login_at)
         VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP(3))`,
        [username, email, passwordHash]
      )
      const token = await createSession(connection, result.insertId)
      await connection.execute(
        `INSERT INTO user_login_logs (user_id, login_ip, device_info)
         VALUES (?, ?, ?)`,
        [result.insertId, clip(context.ip, 45), clip(context.userAgent, 255)]
      )

      return { token, userId: result.insertId }
    })

    return {
      token,
      user: await findUserById(userId),
    }
  } catch (error) {
    if (error?.code === "ER_DUP_ENTRY") {
      throw httpError(409, "Username or email already exists.")
    }

    throw error
  }
}

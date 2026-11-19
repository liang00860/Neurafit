import mysql from "mysql2/promise"

const DEFAULT_DATABASE = "neurafit_db"

let pool

export function getDatabaseName() {
  return process.env.DB_NAME || DEFAULT_DATABASE
}

export function getConnectionConfig({ includeDatabase = true } = {}) {
  const config = {
    charset: "utf8mb4",
    connectTimeout: Number(process.env.DB_CONNECT_TIMEOUT ?? 10000),
    host: process.env.DB_HOST,
    password: process.env.DB_PASSWORD,
    port: Number(process.env.DB_PORT ?? 3306),
    user: process.env.DB_USER,
    waitForConnections: true,
  }

  if (includeDatabase) {
    config.database = getDatabaseName()
  }

  for (const [key, value] of Object.entries({
    DB_HOST: config.host,
    DB_PASSWORD: config.password,
    DB_USER: config.user,
  })) {
    if (!value) {
      throw new Error(`${key} is required`)
    }
  }

  return config
}

export function getPool() {
  if (!pool) {
    pool = mysql.createPool({
      ...getConnectionConfig(),
      connectionLimit: Number(process.env.DB_CONNECTION_LIMIT ?? 10),
      multipleStatements: false,
    })
  }

  return pool
}

export async function query(sql, params = []) {
  const [rows] = await getPool().execute(sql, params)
  return rows
}

export async function withTransaction(callback) {
  const connection = await getPool().getConnection()
  try {
    await connection.beginTransaction()
    const result = await callback(connection)
    await connection.commit()
    return result
  } catch (error) {
    await connection.rollback()
    throw error
  } finally {
    connection.release()
  }
}

export async function closePool() {
  if (pool) {
    await pool.end()
    pool = undefined
  }
}

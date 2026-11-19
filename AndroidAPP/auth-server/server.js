const cors = require("cors");
const crypto = require("crypto");
const express = require("express");
const mysql = require("mysql2/promise");
const multer = require("multer");
const { promisify } = require("util");

require("dotenv").config();

const scryptAsync = promisify(crypto.scrypt);
const TOKEN_TTL_DAYS = Number.parseInt(process.env.TOKEN_TTL_DAYS ?? "30", 10);
const SESSION_TTL_MS = TOKEN_TTL_DAYS * 24 * 60 * 60 * 1000;
const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const SCRYPT_KEY_LENGTH = 64;
const SCRYPT_MAX_MEM = 64 * 1024 * 1024;
const SQUAT_KCAL_PER_REP = 0.6;
const PUSH_UP_KCAL_PER_REP = 0.7;
const NUTRITION_PHOTO_MAX_BYTES = 8 * 1024 * 1024;
const TRAINING_PLAN_DAYS = 7;
const app = express();
const nutritionUpload = multer({
  storage: multer.memoryStorage(),
  limits: {
    fileSize: NUTRITION_PHOTO_MAX_BYTES,
    files: 1,
  },
});

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: Number.parseInt(process.env.DB_PORT ?? "3306", 10),
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  charset: "utf8mb4",
});

// #region debug-point A:remote-reporting
const DEBUG_SERVER_URL = "http://127.0.0.1:7777/event";
const DEBUG_SESSION_ID = "home-training-summary";

function reportDebugEvent(hypothesisId, msg, data = {}, runId = "pre-fix") {
  fetch(DEBUG_SERVER_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: DEBUG_SESSION_ID,
      runId,
      hypothesisId,
      location: "auth-server/server.js",
      msg: `[DEBUG] ${msg}`,
      data,
      ts: Date.now(),
    }),
  }).catch(() => {});
}
// #endregion

// #region debug-point A:db-login-reporting
function reportDbLoginDebugEvent(hypothesisId, msg, data = {}, runId = "pre-fix") {
  const fs = require("fs");
  const debugEnvPath = "../.dbg/db-login-failure.env";
  let serverUrl = "http://127.0.0.1:7777/event";
  let sessionId = "db-login-failure";
  try {
    const content = fs.readFileSync(debugEnvPath, "utf8");
    serverUrl = content.match(/DEBUG_SERVER_URL=(.+)/)?.[1] ?? serverUrl;
    sessionId = content.match(/DEBUG_SESSION_ID=(.+)/)?.[1] ?? sessionId;
  } catch {}
  fetch(serverUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId,
      runId,
      hypothesisId,
      location: "auth-server/server.js",
      msg: `[DEBUG] ${msg}`,
      data,
      ts: Date.now(),
    }),
  }).catch(() => {});
}
// #endregion

app.use(cors());
app.use(express.json());

app.get("/health", async (_req, res) => {
  try {
    await pool.query("SELECT 1");
    // #region debug-point A:health-ok
    reportDbLoginDebugEvent("A", "health check db ping ok", {
      dbHost: process.env.DB_HOST ?? null,
      dbName: process.env.DB_NAME ?? null,
      dbUser: process.env.DB_USER ?? null,
    });
    // #endregion
    res.json({ ok: true });
  } catch (error) {
    // #region debug-point A:health-failed
    reportDbLoginDebugEvent("A", "health check db ping failed", {
      code: error?.code ?? null,
      message: error?.message ?? null,
    });
    // #endregion
    console.error("Health check failed:", error);
    res.status(500).json({ ok: false });
  }
});

app.post("/api/training-sessions/sync", requireAuth, async (req, res) => {
  const rawSessions = Array.isArray(req.body?.sessions) ? req.body.sessions : [];
  const sessions = rawSessions
    .map(normalizeTrainingSessionPayload)
    .filter(Boolean);

  if (sessions.length === 0) {
    return res.status(400).json({ error: "Please provide at least one training session." });
  }

  let connection;
  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    for (const session of sessions) {
      await connection.execute(
        `
          INSERT INTO user_training_sessions (
            user_id,
            client_training_id,
            action_name,
            training_started_at,
            duration_ms,
            rep_count,
            average_score
          )
          VALUES (?, ?, ?, ?, ?, ?, ?)
          ON DUPLICATE KEY UPDATE
            action_name = VALUES(action_name),
            training_started_at = VALUES(training_started_at),
            duration_ms = VALUES(duration_ms),
            rep_count = VALUES(rep_count),
            average_score = VALUES(average_score)
        `,
        [
          req.authUser.id,
          session.clientTrainingId,
          session.actionName,
          session.trainingStartedAt,
          session.durationMs,
          session.repCount,
          session.averageScore,
        ],
      );
    }

    await refreshUserTrainingStats(req.authUser.id, connection);

    await connection.commit();
    return res.json({ syncedCount: sessions.length });
  } catch (error) {
    if (connection) {
      await connection.rollback();
    }
    console.error("Training session sync failed:", error);
    return res.status(500).json({ error: "Training history sync is temporarily unavailable." });
  } finally {
    connection?.release();
  }
});

app.post("/api/step-records/sync", requireAuth, async (req, res) => {
  const rawRecords = Array.isArray(req.body?.records) ? req.body.records : [];
  const records = rawRecords
    .map(normalizeStepRecordPayload)
    .filter(Boolean);

  if (records.length === 0) {
    return res.status(400).json({ error: "Please provide at least one valid step record." });
  }

  let connection;
  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    for (const record of records) {
      await connection.execute(
        `
          INSERT INTO user_daily_steps (
            user_id,
            step_date,
            step_count,
            data_source,
            synced_at
          )
          VALUES (?, ?, ?, ?, UTC_TIMESTAMP())
          ON DUPLICATE KEY UPDATE
            step_count = VALUES(step_count),
            data_source = VALUES(data_source),
            synced_at = VALUES(synced_at)
        `,
        [
          req.authUser.id,
          record.stepDate,
          record.stepCount,
          record.dataSource,
        ],
      );
    }

    await connection.commit();
    return res.json({ syncedCount: records.length });
  } catch (error) {
    if (connection) {
      await connection.rollback();
    }
    console.error("Step record sync failed:", error);
    return res.status(500).json({ error: "Step record sync is temporarily unavailable." });
  } finally {
    connection?.release();
  }
});

app.get("/api/training-stats/summary", requireAuth, async (req, res) => {
  try {
    await refreshUserTrainingStats(req.authUser.id);
    const [rows] = await pool.execute(
      `
        SELECT
          s.total_duration_ms AS totalTrainingTimeMs,
          s.today_duration_ms AS todayTrainingTimeMs,
          s.total_calories_kcal AS kcalBurn,
          s.session_count AS sessionCount,
          COALESCE(MAX(ts.duration_ms), 0) AS longestSessionDurationMs,
          COALESCE(SUM(CASE
            WHEN ts.training_started_at >= (UTC_TIMESTAMP() - INTERVAL 7 DAY) THEN ts.duration_ms
            ELSE 0
          END), 0) AS last7DaysDurationMs,
          COALESCE(SUM(CASE
            WHEN ts.training_started_at < (UTC_TIMESTAMP() - INTERVAL 7 DAY)
             AND ts.training_started_at >= (UTC_TIMESTAMP() - INTERVAL 14 DAY) THEN ts.duration_ms
            ELSE 0
          END), 0) AS previous7DaysDurationMs
        FROM user_training_stats s
        LEFT JOIN user_training_sessions ts
          ON ts.user_id = s.user_id
        WHERE s.user_id = ?
        GROUP BY s.user_id, s.total_duration_ms, s.today_duration_ms, s.total_calories_kcal, s.session_count
      `,
      [req.authUser.id],
    );

    const summary = rows[0] ?? {};
    const totalTrainingTimeMs = Number(summary.totalTrainingTimeMs ?? 0);
    const todayTrainingTimeMs = Number(summary.todayTrainingTimeMs ?? 0);
    const kcalBurn = Number(summary.kcalBurn ?? 0);
    const sessionCount = Number(summary.sessionCount ?? 0);
    const longestSessionDurationMs = Number(summary.longestSessionDurationMs ?? 0);
    const last7DaysDurationMs = Number(summary.last7DaysDurationMs ?? 0);
    const previous7DaysDurationMs = Number(summary.previous7DaysDurationMs ?? 0);

    return res.json({
      totalDurationMs: totalTrainingTimeMs,
      totalTrainingTimeMs,
      todayTrainingTimeMs,
      totalCaloriesKcal: kcalBurn,
      kcalBurn,
      sessionCount,
      longestSessionDurationMs,
      deltaFromLastWeekMs: last7DaysDurationMs - previous7DaysDurationMs,
    });
  } catch (error) {
    // #region debug-point B:summary-error
    reportDebugEvent("B", "training summary failed", {
      userId: req.authUser?.id ?? null,
      code: error?.code ?? null,
      sqlMessage: error?.sqlMessage ?? null,
      message: error?.message ?? null,
    });
    // #endregion
    console.error("Training stats summary failed:", error);
    return res.status(500).json({ error: "Training statistics are temporarily unavailable." });
  }
});

app.get("/api/dashboard/welcome-advice", requireAuth, async (req, res) => {
  try {
    return res.json({
      aiAdvice: await recommendDashboardWelcomeAdvice(req.authUser.id),
    });
  } catch (error) {
    console.error("Dashboard welcome advice failed:", error);
    return res.status(500).json({ error: "Dashboard welcome advice is temporarily unavailable." });
  }
});

app.post("/api/training-plans/recommend", requireAuth, async (req, res) => {
  try {
    return res.json({
      plan: await recommendTrainingPlan(req.authUser.id, req.body ?? {}),
    });
  } catch (error) {
    console.error("Training plan recommendation failed:", error);
    return res.status(500).json({ error: "AI training plan service is temporarily unavailable." });
  }
});

app.post("/api/training-plans/adopt", requireAuth, async (req, res) => {
  if (!req.body?.plan || typeof req.body.plan !== "object") {
    return res.status(400).json({ error: "Please provide a valid training plan." });
  }

  try {
    return res.status(201).json({
      plan: await adoptTrainingPlan(req.authUser.id, req.body.plan),
    });
  } catch (error) {
    console.error("Training plan adoption failed:", error);
    return res.status(500).json({ error: "Training plan upload is temporarily unavailable." });
  }
});

app.get("/api/training-plans/current", requireAuth, async (req, res) => {
  try {
    return res.json({
      plan: await getCurrentTrainingPlan(req.authUser.id),
    });
  } catch (error) {
    console.error("Current training plan failed:", error);
    return res.status(500).json({ error: "Training plan is temporarily unavailable." });
  }
});

app.post("/api/nutrition-records", requireAuth, uploadNutritionPhoto, async (req, res) => {
  const record = normalizeNutritionRecordPayload(req.body, req.file);
  if (!record) {
    return res.status(400).json({ error: "Please provide a valid nutrition scan record." });
  }

  try {
    const [result] = await pool.execute(
      `
        INSERT INTO user_nutrition_records (
          user_id,
          client_scan_id,
          food_name,
          food_label,
          confidence,
          serving_grams,
          calories_kcal,
          protein_grams,
          dietary_fiber_grams,
          carbs_grams,
          fat_grams,
          analysis_summary,
          data_source,
          item_summary,
          photo_filename,
          photo_mime_type,
          photo_size_bytes
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          id = LAST_INSERT_ID(id),
          food_name = VALUES(food_name),
          food_label = VALUES(food_label),
          confidence = VALUES(confidence),
          serving_grams = VALUES(serving_grams),
          calories_kcal = VALUES(calories_kcal),
          protein_grams = VALUES(protein_grams),
          dietary_fiber_grams = VALUES(dietary_fiber_grams),
          carbs_grams = VALUES(carbs_grams),
          fat_grams = VALUES(fat_grams),
          analysis_summary = VALUES(analysis_summary),
          data_source = VALUES(data_source),
          item_summary = VALUES(item_summary),
          photo_filename = VALUES(photo_filename),
          photo_mime_type = VALUES(photo_mime_type),
          photo_size_bytes = VALUES(photo_size_bytes)
      `,
      [
        req.authUser.id,
        record.scanId,
        record.foodName,
        record.foodLabel,
        record.confidence,
        record.servingGrams,
        record.caloriesKcal,
        record.proteinGrams,
        record.dietaryFiberGrams,
        record.carbsGrams,
        record.fatGrams,
        record.analysisSummary,
        record.dataSource,
        record.itemSummary,
        record.photoFilename,
        record.photoMimeType,
        record.photoSizeBytes,
      ],
    );

    return res.status(201).json({
      record: {
        id: Number(result.insertId),
        scanId: record.scanId,
      },
    });
  } catch (error) {
    console.error("Nutrition record save failed:", error);
    return res.status(500).json({ error: "Nutrition record save is temporarily unavailable." });
  }
});

app.post("/api/selected-recipes", requireAuth, async (req, res) => {
  const recipe = normalizeSelectedRecipePayload(req.body);
  if (!recipe) {
    return res.status(400).json({ error: "Please provide a valid selected recipe." });
  }

  try {
    const [result] = await pool.execute(
      `
        INSERT INTO user_selected_recipes (
          user_id,
          client_recipe_id,
          title,
          image_url,
          prep_time_minutes,
          calories_kcal,
          carbs_grams,
          protein_grams,
          fiber_grams,
          ingredients,
          steps
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          id = LAST_INSERT_ID(id),
          title = VALUES(title),
          image_url = VALUES(image_url),
          prep_time_minutes = VALUES(prep_time_minutes),
          calories_kcal = VALUES(calories_kcal),
          carbs_grams = VALUES(carbs_grams),
          protein_grams = VALUES(protein_grams),
          fiber_grams = VALUES(fiber_grams),
          ingredients = VALUES(ingredients),
          steps = VALUES(steps)
      `,
      [
        req.authUser.id,
        recipe.recipeId,
        recipe.title,
        recipe.imageUrl,
        recipe.prepTimeMinutes,
        recipe.caloriesKcal,
        recipe.carbsGrams,
        recipe.proteinGrams,
        recipe.fiberGrams,
        recipe.ingredients,
        recipe.steps,
      ],
    );

    return res.status(201).json({
      recipe: {
        id: Number(result.insertId),
        recipeId: recipe.recipeId,
      },
    });
  } catch (error) {
    console.error("Selected recipe save failed:", error);
    return res.status(500).json({ error: "Selected recipe save is temporarily unavailable." });
  }
});

app.post("/api/login", async (req, res) => {
  const identifier = normalizeString(req.body?.identifier);
  const password = normalizeString(req.body?.password);

  // #region debug-point D:login-request
  reportDbLoginDebugEvent("D", "login request received", {
    hasIdentifier: Boolean(identifier),
    identifierLength: identifier.length,
    hasPassword: Boolean(password),
    bodyKeys: Object.keys(req.body ?? {}),
  });
  // #endregion

  if (!identifier || !password) {
    return res.status(400).json({ error: "Please enter your account and password." });
  }

  try {
    const [rows] = await pool.execute(
      `
        SELECT id, username, email, phone, password_hash, status
        FROM users
        WHERE username = ? OR email = ? OR phone = ?
        LIMIT 1
      `,
      [identifier, identifier, identifier],
    );

    // #region debug-point C:login-query-result
    reportDbLoginDebugEvent("C", "login user query completed", {
      identifier,
      matchedUserCount: rows.length,
      firstUserStatus: rows[0]?.status ?? null,
    });
    // #endregion

    const user = rows[0];
    if (!user) {
      return res.status(401).json({ error: "Account or password is incorrect." });
    }

    if (Number(user.status) !== 1) {
      return res.status(403).json({ error: "This account has been disabled." });
    }

    const passwordVerification = await verifyPassword(password, user.password_hash);
    if (!passwordVerification.matches) {
      return res.status(401).json({ error: "Account or password is incorrect." });
    }

    if (passwordVerification.needsRehash) {
      await pool.execute(
        "UPDATE users SET password_hash = ? WHERE id = ?",
        [await hashPassword(password), user.id],
      );
    }

    const session = await createSession(user, req);
    return res.json(session);
  } catch (error) {
    // #region debug-point B:login-failed
    reportDbLoginDebugEvent("B", "login failed", {
      identifier,
      code: error?.code ?? null,
      sqlMessage: error?.sqlMessage ?? null,
      message: error?.message ?? null,
    });
    // #endregion
    console.error("Login failed:", error);
    return res.status(500).json({ error: "Login service is temporarily unavailable." });
  }
});

app.post("/api/register", async (req, res) => {
  const username = normalizeString(req.body?.username);
  const email = normalizeString(req.body?.email);
  const password = normalizeString(req.body?.password);

  if (!username) {
    return res.status(400).json({ error: "Please enter a username." });
  }
  if (!email) {
    return res.status(400).json({ error: "Please enter an email address." });
  }
  if (!isValidEmail(email)) {
    return res.status(400).json({ error: "Please enter a valid email address." });
  }
  if (!password || password.length < 6) {
    return res.status(400).json({ error: "Password must be at least 6 characters." });
  }

  let connection;
  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    const [existingRows] = await connection.execute(
      `
        SELECT id
        FROM users
        WHERE username = ? OR email = ?
        LIMIT 1
      `,
      [username, email],
    );

    if (existingRows.length > 0) {
      await connection.rollback();
      return res.status(409).json({ error: "Username or email already exists." });
    }

    const passwordHash = await hashPassword(password);
    const [insertResult] = await connection.execute(
      `
        INSERT INTO users (username, email, password_hash, status)
        VALUES (?, ?, ?, 1)
      `,
      [username, email, passwordHash],
    );

    const user = {
      id: Number(insertResult.insertId),
      username,
      email,
    };

    const sessionResponse = await createSession(user, req, connection);
    await connection.commit();
    return res.status(201).json(sessionResponse);
  } catch (error) {
    if (connection) {
      await connection.rollback();
    }
    console.error("Register failed:", error);
    return res.status(500).json({ error: "Registration service is temporarily unavailable." });
  } finally {
    connection?.release();
  }
});

async function createSession(user, req, connection = pool) {
  const rawToken = crypto.randomBytes(32).toString("hex");
  const tokenHash = crypto.createHash("sha256").update(rawToken).digest("hex");
  const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
  const clientIp = getClientIp(req);
  const deviceInfo = normalizeUserAgent(req.get("user-agent"));

  await connection.execute(
    `
      INSERT INTO user_sessions (user_id, token_hash, expires_at)
      VALUES (?, ?, ?)
    `,
    [user.id, tokenHash, toMysqlDateTime(expiresAt)],
  );

  await connection.execute(
    `
      UPDATE users
      SET last_login_at = NOW()
      WHERE id = ?
    `,
    [user.id],
  );

  await connection.execute(
    `
      INSERT INTO user_login_logs (user_id, login_ip, device_info)
      VALUES (?, ?, ?)
    `,
    [user.id, clientIp, deviceInfo],
  );

  return {
    token: rawToken,
    user: {
      id: Number(user.id),
      username: user.username,
      email: user.email ?? null,
    },
  };
}

async function requireAuth(req, res, next) {
  const token = extractBearerToken(req.get("authorization"));
  if (!token) {
    return res.status(401).json({ error: "Please log in first." });
  }

  try {
    const tokenHash = crypto.createHash("sha256").update(token).digest("hex");
    const [rows] = await pool.execute(
      `
        SELECT u.id, u.username, u.email
        FROM user_sessions s
        INNER JOIN users u ON u.id = s.user_id
        WHERE s.token_hash = ?
          AND s.expires_at > NOW()
          AND u.status = 1
        LIMIT 1
      `,
      [tokenHash],
    );

    const user = rows[0];
    if (!user) {
      return res.status(401).json({ error: "Your login session has expired." });
    }

    req.authUser = {
      id: Number(user.id),
      username: user.username,
      email: user.email ?? null,
    };
    return next();
  } catch (error) {
    console.error("Authorization failed:", error);
    return res.status(500).json({ error: "Authentication service is temporarily unavailable." });
  }
}

function uploadNutritionPhoto(req, res, next) {
  nutritionUpload.single("photo")(req, res, (error) => {
    if (!error) {
      return next();
    }

    if (error instanceof multer.MulterError) {
      if (error.code === "LIMIT_FILE_SIZE") {
        return res.status(413).json({ error: "Food photo is too large." });
      }
      return res.status(400).json({ error: "Food photo upload is invalid." });
    }

    console.error("Nutrition photo upload failed:", error);
    return res.status(500).json({ error: "Food photo upload is temporarily unavailable." });
  });
}

async function refreshUserTrainingStats(userId, connection = pool) {
  await connection.execute(
    `
      INSERT INTO user_training_stats (
        user_id,
        total_duration_ms,
        today_duration_ms,
        total_calories_kcal,
        session_count,
        last_synced_at
      )
      SELECT
        ?,
        COALESCE(SUM(duration_ms), 0) AS total_duration_ms,
        COALESCE(SUM(CASE WHEN DATE(training_started_at) = DATE(UTC_TIMESTAMP()) THEN duration_ms ELSE 0 END), 0) AS today_duration_ms,
        COALESCE(SUM(
          CASE
            WHEN UPPER(REPLACE(action_name, '-', '_')) IN ('SQUAT', 'DEEP_SQUAT') THEN rep_count * ${SQUAT_KCAL_PER_REP}
            WHEN UPPER(REPLACE(action_name, '-', '_')) IN ('PUSH_UP', 'PUSHUP', 'PUSH_UPS') THEN rep_count * ${PUSH_UP_KCAL_PER_REP}
            ELSE 0
          END
        ), 0) AS total_calories_kcal,
        COUNT(*) AS session_count,
        UTC_TIMESTAMP()
      FROM user_training_sessions
      WHERE user_id = ?
      ON DUPLICATE KEY UPDATE
        total_duration_ms = VALUES(total_duration_ms),
        today_duration_ms = VALUES(today_duration_ms),
        total_calories_kcal = VALUES(total_calories_kcal),
        session_count = VALUES(session_count),
        last_synced_at = VALUES(last_synced_at)
    `,
    [userId, userId],
  );
}

function addDays(value, days) {
  const date = value instanceof Date ? new Date(value) : new Date(value);
  date.setDate(date.getDate() + days);
  return date;
}

function formatDateKey(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function dateKeyFromDb(value) {
  if (!value) {
    return "";
  }
  if (value instanceof Date) {
    return formatDateKey(value);
  }
  return String(value).slice(0, 10);
}

function parseJsonFromModelText(text) {
  const normalized = normalizeString(text);
  if (!normalized) return null;

  try {
    return JSON.parse(normalized);
  } catch {
    const match = normalized.match(/\{[\s\S]*\}/);
    if (!match) return null;
    try {
      return JSON.parse(match[0]);
    } catch {
      return null;
    }
  }
}

function clipString(value, maxLength, fallback = "") {
  const normalized = normalizeString(value);
  return (normalized || fallback).slice(0, maxLength);
}

async function getTrainingPlanContext(userId) {
  await refreshUserTrainingStats(userId);
  const [summaryRows] = await pool.execute(
    "SELECT total_duration_ms, total_calories_kcal, session_count FROM user_training_stats WHERE user_id = ?",
    [userId],
  );
  const [recentRows] = await pool.execute(
    `
      SELECT action_name, duration_ms, rep_count, average_score, training_started_at
      FROM user_training_sessions
      WHERE user_id = ?
      ORDER BY COALESCE(training_started_at, created_at) DESC
      LIMIT 12
    `,
    [userId],
  );

  return {
    summary: {
      sessionCount: Number(summaryRows[0]?.session_count ?? 0),
      totalDurationMs: Number(summaryRows[0]?.total_duration_ms ?? 0),
      totalCaloriesKcal: Number(summaryRows[0]?.total_calories_kcal ?? 0),
    },
    recentSessions: recentRows.map((row) => ({
      actionName: row.action_name,
      averageScore: Number(row.average_score ?? 0),
      durationMs: Number(row.duration_ms ?? 0),
      repCount: Number(row.rep_count ?? 0),
    })),
  };
}

function buildAdaptiveTrainingPlan(context, request = {}) {
  const recentSessions = context.recentSessions ?? [];
  const sessionCount = Number(context.summary?.sessionCount ?? 0);
  const averageScore =
    recentSessions.reduce((total, session) => total + session.averageScore, 0) /
    Math.max(1, recentSessions.length);
  const squatVolume = recentSessions
    .filter((session) => session.actionName === "SQUAT")
    .reduce((total, session) => total + session.repCount, 0);
  const pushUpVolume = recentSessions
    .filter((session) => session.actionName === "PUSH_UP")
    .reduce((total, session) => total + session.repCount, 0);
  const needsFoundation = sessionCount === 0 || averageScore < 72;
  const needsUpperBalance = pushUpVolume < squatVolume * 0.75;
  const goal = needsFoundation
    ? "动作稳定与基础力量"
    : needsUpperBalance
      ? "上肢力量补强"
      : "全身力量递进";
  const difficulty = sessionCount > 12 && averageScore >= 82 ? "进阶" : "中等";
  const start = new Date();
  const planTemplates = [
    {
      focus: "动作质量",
      items: [
        { name: "深蹲", reps: needsFoundation ? 10 : 14, restSeconds: 75, sets: 3 },
        { name: "俯卧撑", reps: needsUpperBalance ? 8 : 10, restSeconds: 75, sets: 3 },
        { durationMinutes: 6, name: "肩髋动态热身", restSeconds: 30, sets: 1 },
      ],
      note: "把节奏放慢，优先保证动作深度和身体直线。",
      title: "全身动作校准",
    },
    {
      focus: "上肢力量",
      items: [
        { name: "俯卧撑", reps: needsUpperBalance ? 8 : 12, restSeconds: 90, sets: 4 },
        { durationMinutes: 8, name: "平板支撑", restSeconds: 60, sets: 2 },
        { durationMinutes: 6, name: "胸肩拉伸", restSeconds: 30, sets: 1 },
      ],
      note: "每组保留1到2次余力，避免塌腰和过快下降。",
      title: "上肢控制日",
    },
    {
      focus: "恢复",
      items: [
        { durationMinutes: 18, name: "低强度步行", restSeconds: 0, sets: 1 },
        { durationMinutes: 10, name: "髋踝灵活性", restSeconds: 20, sets: 1 },
      ],
      note: "保持轻松呼吸，让肌肉和关节恢复。",
      title: "主动恢复",
    },
    {
      focus: "下肢力量",
      items: [
        { name: "深蹲", reps: needsFoundation ? 10 : 16, restSeconds: 90, sets: 4 },
        { name: "弓步蹲", reps: 10, restSeconds: 75, sets: 3 },
        { durationMinutes: 6, name: "核心抗旋转", restSeconds: 45, sets: 2 },
      ],
      note: "注意膝盖轨迹，避免内扣，底部停留一秒。",
      title: "下肢递进日",
    },
    {
      focus: "混合体能",
      items: [
        { name: "俯卧撑", reps: 10, restSeconds: 60, sets: 3 },
        { name: "深蹲", reps: 14, restSeconds: 60, sets: 3 },
        { durationMinutes: 8, name: "间歇核心循环", restSeconds: 45, sets: 2 },
      ],
      note: "用中等强度完成循环，动作质量下降就延长休息。",
      title: "全身循环",
    },
    {
      focus: "技术巩固",
      items: [
        { name: "节奏深蹲", reps: 8, restSeconds: 90, sets: 3 },
        { name: "慢速俯卧撑", reps: 6, restSeconds: 90, sets: 3 },
        { durationMinutes: 10, name: "拉伸放松", restSeconds: 0, sets: 1 },
      ],
      note: "用慢速离心找到稳定发力，不追求数量。",
      title: "技术巩固",
    },
    {
      focus: "休息",
      items: [
        { durationMinutes: 20, name: "轻松散步", restSeconds: 0, sets: 1 },
        { durationMinutes: 8, name: "全身拉伸", restSeconds: 0, sets: 1 },
      ],
      note: "记录疲劳和睡眠，为下一周调整训练量。",
      title: "恢复与复盘",
    },
  ];
  const days = planTemplates.map((template, index) => ({
    ...template,
    date: formatDateKey(addDays(start, index)),
  }));

  return {
    days,
    difficulty,
    endsOn: days.at(-1).date,
    goal,
    startsOn: days[0].date,
    summary:
      sessionCount === 0
        ? "尚未积累足够训练记录，先用一周基础计划建立动作质量和训练节奏。"
        : `已参考最近${recentSessions.length}次训练、总训练${sessionCount}次和动作表现，安排强弱交替的一周计划。`,
    title: request.goal === "balanced_strength" ? "AI均衡力量计划" : "AI个性化训练计划",
  };
}

function normalizePlanItem(value, fallback) {
  return {
    durationMinutes:
      normalizeNonNegativeInteger(value?.durationMinutes) ??
      normalizeNonNegativeInteger(value?.duration_minutes) ??
      fallback?.durationMinutes ??
      null,
    name: clipString(value?.name, 160, fallback?.name ?? "自重训练"),
    reps: normalizeNonNegativeInteger(value?.reps) ?? fallback?.reps ?? null,
    restSeconds:
      normalizeNonNegativeInteger(value?.restSeconds) ??
      normalizeNonNegativeInteger(value?.rest_seconds) ??
      fallback?.restSeconds ??
      60,
    sets: normalizePositiveInteger(value?.sets) ?? fallback?.sets ?? 1,
  };
}

function normalizeTrainingPlan(value, fallback) {
  const fallbackStart = fallback.startsOn || formatDateKey(new Date());
  const startDate = new Date(`${fallbackStart}T12:00:00`);
  const rawDays = Array.isArray(value?.days) ? value.days : [];
  const fallbackDays = Array.isArray(fallback.days) ? fallback.days : [];
  const days = Array.from({ length: TRAINING_PLAN_DAYS }, (_, index) => {
    const rawDay = rawDays[index] ?? {};
    const fallbackDay = fallbackDays[index] ?? {};
    const fallbackDate = formatDateKey(addDays(startDate, index));
    const rawItems = Array.isArray(rawDay.items) ? rawDay.items : [];
    const fallbackItems = Array.isArray(fallbackDay.items) ? fallbackDay.items : [];
    const itemCount = Math.max(rawItems.length, fallbackItems.length, 1);
    return {
      date: dateKeyFromDb(rawDay.date) || fallbackDay.date || fallbackDate,
      focus: clipString(rawDay.focus, 120, fallbackDay.focus ?? "综合训练"),
      items: Array.from({ length: itemCount }, (_, itemIndex) =>
        normalizePlanItem(rawItems[itemIndex], fallbackItems[itemIndex]),
      ),
      note: clipString(rawDay.note, 1000, fallbackDay.note ?? ""),
      title: clipString(rawDay.title, 180, fallbackDay.title ?? `第${index + 1}天训练`),
    };
  });

  return {
    days,
    difficulty: clipString(value?.difficulty, 64, fallback.difficulty ?? "中等"),
    endsOn: dateKeyFromDb(value?.endsOn) || days.at(-1)?.date || fallbackStart,
    goal: clipString(value?.goal, 120, fallback.goal ?? "综合体能"),
    startsOn: dateKeyFromDb(value?.startsOn) || days[0]?.date || fallbackStart,
    summary: clipString(value?.summary, 1000, fallback.summary ?? "已生成一周训练安排。"),
    title: clipString(value?.title, 180, fallback.title ?? "AI训练计划"),
  };
}

async function generateTrainingPlanWithModel(context, request, fallback) {
  const token = process.env.DEEPSEEK_API_KEY;
  if (!token) return null;

  const response = await fetch("https://api.deepseek.com/chat/completions", {
    body: JSON.stringify({
      messages: [
        {
          content:
            "你是NEURAFIT的训练计划大模型。只返回JSON，不要Markdown。JSON结构必须为 {\"plan\":{\"title\",\"goal\",\"difficulty\",\"summary\",\"startsOn\",\"endsOn\",\"days\":[{\"date\",\"title\",\"focus\",\"note\",\"items\":[{\"name\",\"sets\",\"reps\",\"durationMinutes\",\"restSeconds\"}]}]}}。请生成7天计划，适合自重训练，深蹲和俯卧撑为核心动作。",
          role: "system",
        },
        {
          content: JSON.stringify({
            fallbackPlan: fallback,
            request,
            trainingContext: context,
          }),
          role: "user",
        },
      ],
      model: "deepseek-chat",
      response_format: { type: "json_object" },
      stream: false,
    }),
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    method: "POST",
  });

  if (!response.ok) return null;
  const root = await response.json().catch(() => null);
  const parsed = parseJsonFromModelText(root?.choices?.[0]?.message?.content);
  return parsed?.plan ?? parsed;
}

async function getDashboardWelcomeAdviceContext(userId) {
  const [summaryRows] = await pool.execute(
    `
      SELECT
        COUNT(*) AS totalSessionCount,
        COALESCE(SUM(CASE
          WHEN training_started_at >= DATE_SUB(UTC_DATE(), INTERVAL 1 DAY)
           AND training_started_at < UTC_DATE() THEN 1
          ELSE 0
        END), 0) AS yesterdaySessionCount,
        COALESCE(SUM(CASE
          WHEN training_started_at >= (UTC_TIMESTAMP() - INTERVAL 7 DAY) THEN 1
          ELSE 0
        END), 0) AS last7DaysSessionCount,
        COALESCE(AVG(CASE
          WHEN training_started_at >= (UTC_TIMESTAMP() - INTERVAL 14 DAY) THEN average_score
          ELSE NULL
        END), 0) AS recentAverageScore
      FROM user_training_sessions
      WHERE user_id = ?
    `,
    [userId],
  );
  const [actionRows] = await pool.execute(
    `
      SELECT action_name, COUNT(*) AS actionCount
      FROM user_training_sessions
      WHERE user_id = ?
        AND training_started_at >= (UTC_TIMESTAMP() - INTERVAL 14 DAY)
      GROUP BY action_name
      ORDER BY actionCount DESC, action_name ASC
      LIMIT 1
    `,
    [userId],
  );

  return {
    last7DaysSessionCount: Number(summaryRows[0]?.last7DaysSessionCount ?? 0),
    recentAverageScore: Number(summaryRows[0]?.recentAverageScore ?? 0),
    topActionName: normalizeActionName(actionRows[0]?.action_name),
    totalSessionCount: Number(summaryRows[0]?.totalSessionCount ?? 0),
    yesterdaySessionCount: Number(summaryRows[0]?.yesterdaySessionCount ?? 0),
  };
}

function buildDashboardWelcomeAdviceFallback(context) {
  if (context.yesterdaySessionCount >= 3) {
    return "注意恢复放松";
  }
  if (context.yesterdaySessionCount >= 1) {
    return "今天延续节奏";
  }
  if (context.recentAverageScore > 0 && context.recentAverageScore < 75) {
    return "动作放慢一点";
  }
  if (context.topActionName === "PUSH_UP") {
    return "核心继续收紧";
  }
  if (context.topActionName === "SQUAT") {
    return "蹲底保持稳定";
  }
  return "今天先练一组";
}

function sanitizeDashboardAdvice(value, fallback = "保持训练节奏") {
  const normalized = normalizeString(value)
    .replace(/[，。！？、；：,.!?;:"'`]/g, "")
    .replace(/\s+/g, "");
  const limited = Array.from(normalized).slice(0, 12).join("");
  return limited || fallback;
}

async function generateDashboardWelcomeAdviceWithModel(context, fallback) {
  const token = process.env.DEEPSEEK_API_KEY;
  if (!token) return null;

  const response = await fetch("https://api.deepseek.com/chat/completions", {
    body: JSON.stringify({
      messages: [
        {
          content:
            "你是NEURAFIT首页欢迎卡片文案助手。只返回JSON，不要Markdown。JSON结构必须为 {\"advice\":\"\"}。advice要求：中文，不超过12个汉字，不要标点，不要emoji，不要解释，语气简洁，给一句鼓励或训练建议。",
          role: "system",
        },
        {
          content: JSON.stringify({
            dashboardContext: context,
            fallbackAdvice: fallback,
          }),
          role: "user",
        },
      ],
      model: "deepseek-chat",
      response_format: { type: "json_object" },
      stream: false,
    }),
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    method: "POST",
  });

  if (!response.ok) return null;
  const root = await response.json().catch(() => null);
  const parsed = parseJsonFromModelText(root?.choices?.[0]?.message?.content);
  return parsed?.advice ?? parsed?.message ?? null;
}

async function recommendDashboardWelcomeAdvice(userId) {
  const context = await getDashboardWelcomeAdviceContext(userId);
  const fallback = buildDashboardWelcomeAdviceFallback(context);
  let modelAdvice = null;
  try {
    modelAdvice = await generateDashboardWelcomeAdviceWithModel(context, fallback);
  } catch (error) {
    console.warn("Dashboard welcome advice fell back to local rules:", error);
  }
  return sanitizeDashboardAdvice(modelAdvice ?? fallback, fallback);
}

async function recommendTrainingPlan(userId, request = {}) {
  const context = await getTrainingPlanContext(userId);
  const fallback = buildAdaptiveTrainingPlan(context, request);
  let modelPlan = null;
  try {
    modelPlan = await generateTrainingPlanWithModel(context, request, fallback);
  } catch (error) {
    console.warn("AI training plan generation fell back to local rules:", error);
  }
  return normalizeTrainingPlan(modelPlan ?? fallback, fallback);
}

async function serializeTrainingPlan(planId, connection = pool) {
  const [planRows] = await connection.execute(
    "SELECT * FROM user_training_plans WHERE id = ? LIMIT 1",
    [planId],
  );
  const planRow = planRows[0];
  if (!planRow) return null;

  const [dayRows] = await connection.execute(
    "SELECT * FROM user_training_plan_days WHERE plan_id = ? ORDER BY day_index",
    [planId],
  );
  const dayIds = dayRows.map((row) => row.id);
  const itemsByDay = new Map();
  if (dayIds.length > 0) {
    const [itemRows] = await connection.execute(
      `
        SELECT *
        FROM user_training_plan_items
        WHERE plan_day_id IN (${dayIds.map(() => "?").join(",")})
        ORDER BY plan_day_id, sort_order
      `,
      dayIds,
    );
    for (const item of itemRows) {
      itemsByDay.set(item.plan_day_id, [
        ...(itemsByDay.get(item.plan_day_id) ?? []),
        {
          durationMinutes: item.duration_minutes == null ? null : Number(item.duration_minutes),
          name: item.name,
          reps: item.reps == null ? null : Number(item.reps),
          restSeconds: Number(item.rest_seconds ?? 60),
          sets: Number(item.sets ?? 1),
        },
      ]);
    }
  }

  return {
    days: dayRows.map((day) => ({
      date: dateKeyFromDb(day.scheduled_date),
      focus: day.focus,
      items: itemsByDay.get(day.id) ?? [],
      note: day.note ?? "",
      title: day.title,
    })),
    difficulty: planRow.difficulty,
    endsOn: dateKeyFromDb(planRow.ends_on),
    goal: planRow.goal,
    startsOn: dateKeyFromDb(planRow.starts_on),
    summary: planRow.summary,
    title: planRow.title,
  };
}

async function adoptTrainingPlan(userId, rawPlan) {
  const fallback = buildAdaptiveTrainingPlan(await getTrainingPlanContext(userId));
  const plan = normalizeTrainingPlan(rawPlan, fallback);
  let connection;
  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();
    await connection.execute(
      "UPDATE user_training_plans SET status = 'replaced' WHERE user_id = ? AND status = 'active'",
      [userId],
    );
    const [result] = await connection.execute(
      `
        INSERT INTO user_training_plans
          (user_id, title, goal, difficulty, summary, source, status, starts_on, ends_on, adopted_at)
        VALUES (?, ?, ?, ?, ?, 'ai', 'active', ?, ?, UTC_TIMESTAMP(3))
      `,
      [userId, plan.title, plan.goal, plan.difficulty, plan.summary, plan.startsOn, plan.endsOn],
    );
    for (const [dayIndex, day] of plan.days.entries()) {
      const [dayResult] = await connection.execute(
        `
          INSERT INTO user_training_plan_days
            (plan_id, day_index, scheduled_date, title, focus, note)
          VALUES (?, ?, ?, ?, ?, ?)
        `,
        [result.insertId, dayIndex, day.date, day.title, day.focus, day.note],
      );
      for (const [itemIndex, item] of day.items.entries()) {
        await connection.execute(
          `
            INSERT INTO user_training_plan_items
              (plan_day_id, sort_order, name, sets, reps, duration_minutes, rest_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?)
          `,
          [
            dayResult.insertId,
            itemIndex,
            item.name,
            item.sets,
            item.reps,
            item.durationMinutes,
            item.restSeconds,
          ],
        );
      }
    }
    const savedPlan = await serializeTrainingPlan(result.insertId, connection);
    await connection.commit();
    return savedPlan;
  } catch (error) {
    if (connection) {
      await connection.rollback();
    }
    throw error;
  } finally {
    connection?.release();
  }
}

async function getCurrentTrainingPlan(userId) {
  const [rows] = await pool.execute(
    `
      SELECT id
      FROM user_training_plans
      WHERE user_id = ? AND status = 'active'
      ORDER BY adopted_at DESC, id DESC
      LIMIT 1
    `,
    [userId],
  );
  return rows[0] ? serializeTrainingPlan(rows[0].id) : null;
}

async function verifyPassword(password, storedHash) {
  if (!storedHash || typeof storedHash !== "string") {
    return { matches: false, needsRehash: false };
  }

  const parts = storedHash.split("$");
  if (parts.length !== 6 || parts[0] !== "scrypt" || !parts[4] || !parts[5]) {
    return { matches: false, needsRehash: false };
  }

  const [, nValue, rValue, pValue, saltValue, hashValue] = parts;
  const scryptOptions = {
    N: Number.parseInt(nValue, 10),
    r: Number.parseInt(rValue, 10),
    p: Number.parseInt(pValue, 10),
    maxmem: SCRYPT_MAX_MEM,
  };
  const storedBuffer = Buffer.from(hashValue, "base64url");
  const derivedKey = await scryptAsync(
    password,
    Buffer.from(saltValue, "base64url"),
    storedBuffer.length,
    scryptOptions,
  );

  if (
    storedBuffer.length === derivedKey.length &&
    crypto.timingSafeEqual(storedBuffer, derivedKey)
  ) {
    return { matches: true, needsRehash: false };
  }

  const legacyAndroidKey = await scryptAsync(
    password,
    saltValue,
    storedBuffer.length,
    scryptOptions,
  );

  return {
    matches:
      storedBuffer.length === legacyAndroidKey.length &&
      crypto.timingSafeEqual(storedBuffer, legacyAndroidKey),
    needsRehash: true,
  };
}

async function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString("base64url");
  const derivedKey = await scryptAsync(
    password,
    Buffer.from(salt, "base64url"),
    SCRYPT_KEY_LENGTH,
    {
      N: SCRYPT_N,
      r: SCRYPT_R,
      p: SCRYPT_P,
      maxmem: SCRYPT_MAX_MEM,
    },
  );
  return `scrypt$${SCRYPT_N}$${SCRYPT_R}$${SCRYPT_P}$${salt}$${derivedKey.toString("base64url")}`;
}

function normalizeString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeTrainingSessionPayload(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const clientTrainingId = normalizePositiveInteger(value.clientTrainingId);
  const actionName = normalizeActionName(value.actionName);
  const durationMs = normalizeNonNegativeInteger(value.durationMs);
  const repCount = normalizeNonNegativeInteger(value.repCount);
  const averageScore = normalizeNonNegativeInteger(value.averageScore);
  const trainingStartedAt = toMysqlDateTimeFromEpochMs(value.startedAtMs);

  if (
    clientTrainingId == null ||
    !actionName ||
    durationMs == null ||
    repCount == null ||
    averageScore == null
  ) {
    return null;
  }

  return {
    clientTrainingId,
    actionName,
    trainingStartedAt,
    durationMs,
    repCount,
    averageScore,
  };
}

function normalizeStepRecordPayload(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const stepDate = normalizeDateKey(value.date);
  const stepCount = normalizeNonNegativeInteger(value.stepCount);
  const dataSource = normalizeOptionalString(value.source, 80) ?? "device_sensor";

  if (!stepDate || stepCount == null) {
    return null;
  }

  return {
    stepDate,
    stepCount,
    dataSource,
  };
}

function normalizeNutritionRecordPayload(value, photo) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const scanId = normalizeString(value.scanId).slice(0, 64);
  const foodName = normalizeString(value.foodName).slice(0, 120);
  const foodLabel = normalizeString(value.label).slice(0, 255);
  const confidence = normalizeConfidence(value.confidence);
  const servingGrams = normalizeNonNegativeInteger(value.servingGrams);
  const caloriesKcal = normalizeNonNegativeInteger(value.caloriesKcal);
  const proteinGrams = normalizeNonNegativeDecimal(value.proteinGrams);
  const dietaryFiberGrams = normalizeNonNegativeDecimal(value.dietaryFiberGrams);
  const carbsGrams = normalizeNonNegativeDecimal(value.carbsGrams);
  const fatGrams = normalizeNonNegativeDecimal(value.fatGrams);

  if (
    !scanId ||
    !foodName ||
    !foodLabel ||
    confidence == null ||
    servingGrams == null ||
    caloriesKcal == null ||
    proteinGrams == null ||
    dietaryFiberGrams == null ||
    carbsGrams == null ||
    fatGrams == null
  ) {
    return null;
  }

  return {
    scanId,
    foodName,
    foodLabel,
    confidence,
    servingGrams,
    caloriesKcal,
    proteinGrams,
    dietaryFiberGrams,
    carbsGrams,
    fatGrams,
    analysisSummary: normalizeOptionalString(value.analysisSummary, 2000),
    dataSource: normalizeOptionalString(value.dataSource, 120),
    itemSummary: normalizeOptionalString(value.itemSummary, 1000),
    photoFilename: normalizeOptionalString(photo?.originalname, 255),
    photoMimeType: normalizeOptionalString(photo?.mimetype, 80),
    photoSizeBytes: normalizeNonNegativeInteger(photo?.size) ?? 0,
  };
}

function normalizeSelectedRecipePayload(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const recipeId = normalizeString(value.recipeId).slice(0, 64);
  const title = normalizeString(value.title).slice(0, 180);
  const imageUrl = normalizeOptionalString(value.imageUrl, 1000);
  const prepTimeMinutes = normalizeNonNegativeInteger(value.prepTimeMinutes);
  const caloriesKcal = normalizeNonNegativeInteger(value.caloriesKcal);
  const carbsGrams = normalizeNonNegativeDecimal(value.carbsGrams);
  const proteinGrams = normalizeNonNegativeDecimal(value.proteinGrams);
  const fiberGrams = normalizeNonNegativeDecimal(value.fiberGrams);
  const ingredients = normalizeString(value.ingredients).slice(0, 2000);
  const steps = normalizeOptionalString(value.steps, 4000);

  if (
    !recipeId ||
    !title ||
    !imageUrl ||
    prepTimeMinutes == null ||
    caloriesKcal == null ||
    carbsGrams == null ||
    proteinGrams == null ||
    fiberGrams == null ||
    !ingredients
  ) {
    return null;
  }

  return {
    recipeId,
    title,
    imageUrl,
    prepTimeMinutes,
    caloriesKcal,
    carbsGrams,
    proteinGrams,
    fiberGrams,
    ingredients,
    steps,
  };
}

function normalizeUserAgent(value) {
  const normalized = normalizeString(value);
  return normalized ? normalized.slice(0, 255) : null;
}

function normalizePositiveInteger(value) {
  const normalized = Number.parseInt(value, 10);
  if (!Number.isFinite(normalized) || normalized <= 0) {
    return null;
  }
  return normalized;
}

function normalizeNonNegativeInteger(value) {
  const normalized = Number.parseInt(value, 10);
  if (!Number.isFinite(normalized) || normalized < 0) {
    return null;
  }
  return normalized;
}

function normalizeNonNegativeDecimal(value) {
  const normalized = Number.parseFloat(value);
  if (!Number.isFinite(normalized) || normalized < 0) {
    return null;
  }
  return Number(normalized.toFixed(2));
}

function normalizeConfidence(value) {
  const normalized = Number.parseFloat(value);
  if (!Number.isFinite(normalized)) {
    return null;
  }
  return Math.min(Math.max(normalized, 0), 1);
}

function normalizeOptionalString(value, maxLength) {
  const normalized = normalizeString(value);
  return normalized ? normalized.slice(0, maxLength) : null;
}

function normalizeDateKey(value) {
  const normalized = normalizeString(value);
  return /^\d{4}-\d{2}-\d{2}$/.test(normalized) ? normalized : "";
}

function normalizeActionName(value) {
  const normalized = normalizeString(value)
    .replace(/[\s-]+/g, "_")
    .toUpperCase();
  if (normalized === "SQUAT" || normalized === "DEEP_SQUAT") {
    return "SQUAT";
  }
  if (normalized === "PUSH_UP" || normalized === "PUSHUP" || normalized === "PUSH_UPS") {
    return "PUSH_UP";
  }
  return normalized ? normalized.slice(0, 32) : "";
}

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function extractBearerToken(headerValue) {
  const normalized = normalizeString(headerValue);
  if (!normalized.toLowerCase().startsWith("bearer ")) {
    return "";
  }
  return normalized.slice(7).trim();
}

function getClientIp(req) {
  const forwarded = req.headers["x-forwarded-for"];
  if (typeof forwarded === "string" && forwarded.trim()) {
    return forwarded.split(",")[0].trim().slice(0, 45);
  }

  return normalizeString(req.ip).replace(/^::ffff:/, "").slice(0, 45) || null;
}

function toMysqlDateTime(date) {
  return date.toISOString().slice(0, 19).replace("T", " ");
}

function toMysqlDateTimeFromEpochMs(value) {
  const timestamp = Number.parseInt(value, 10);
  if (!Number.isFinite(timestamp) || timestamp <= 0) {
    return null;
  }

  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return toMysqlDateTime(date);
}

async function ensureColumnExists(tableName, columnName, definitionSql) {
  const [rows] = await pool.execute(
    `
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = ?
        AND COLUMN_NAME = ?
      LIMIT 1
    `,
    [tableName, columnName],
  );

  if (rows.length === 0) {
    await pool.execute(`ALTER TABLE ${tableName} ADD COLUMN ${columnName} ${definitionSql}`);
  }
}

async function ensureSchema() {
  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS users (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        username VARCHAR(50) NOT NULL,
        phone VARCHAR(20) NULL,
        email VARCHAR(100) NULL,
        password_hash VARCHAR(255) NOT NULL,
        avatar_url VARCHAR(255) NULL,
        status TINYINT NOT NULL DEFAULT 1,
        last_login_at DATETIME(3) NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uq_users_username (username),
        UNIQUE KEY uq_users_phone (phone),
        UNIQUE KEY uq_users_email (email)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_sessions (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        token_hash CHAR(64) NOT NULL,
        expires_at DATETIME(3) NOT NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uq_user_sessions_token_hash (token_hash),
        KEY idx_user_sessions_user (user_id),
        KEY idx_user_sessions_expires_at (expires_at),
        CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_login_logs (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        login_ip VARCHAR(45) NULL,
        device_info VARCHAR(255) NULL,
        login_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        KEY idx_user_login_logs_user (user_id),
        CONSTRAINT fk_user_login_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_training_sessions (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        client_training_id BIGINT NOT NULL,
        action_name VARCHAR(32) NOT NULL,
        training_started_at DATETIME NULL,
        duration_ms BIGINT NOT NULL DEFAULT 0,
        rep_count INT NOT NULL DEFAULT 0,
        average_score INT NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_user_training_client (user_id, client_training_id),
        KEY idx_training_sessions_user_id (user_id),
        KEY idx_training_sessions_started_at (training_started_at)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_training_stats (
        user_id BIGINT UNSIGNED PRIMARY KEY,
        total_duration_ms BIGINT NOT NULL DEFAULT 0,
        today_duration_ms BIGINT NOT NULL DEFAULT 0,
        total_calories_kcal DOUBLE NOT NULL DEFAULT 0,
        session_count INT NOT NULL DEFAULT 0,
        last_synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await ensureColumnExists(
    "user_training_stats",
    "today_duration_ms",
    "BIGINT NOT NULL DEFAULT 0 AFTER total_duration_ms",
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_daily_steps (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        step_date DATE NOT NULL,
        step_count INT UNSIGNED NOT NULL DEFAULT 0,
        data_source VARCHAR(80) NOT NULL DEFAULT 'device_sensor',
        synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_user_daily_steps (user_id, step_date),
        KEY idx_user_daily_steps_user_date (user_id, step_date),
        CONSTRAINT fk_user_daily_steps_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_training_plans (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        title VARCHAR(180) NOT NULL,
        goal VARCHAR(120) NOT NULL,
        difficulty VARCHAR(64) NOT NULL,
        summary VARCHAR(1000) NOT NULL,
        source VARCHAR(80) NOT NULL DEFAULT 'ai',
        status VARCHAR(32) NOT NULL DEFAULT 'active',
        starts_on DATE NOT NULL,
        ends_on DATE NOT NULL,
        adopted_at DATETIME(3) NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        KEY idx_user_training_plans_user_status (user_id, status, starts_on),
        CONSTRAINT fk_user_training_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_training_plan_days (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        plan_id BIGINT UNSIGNED NOT NULL,
        day_index INT NOT NULL,
        scheduled_date DATE NOT NULL,
        title VARCHAR(180) NOT NULL,
        focus VARCHAR(120) NOT NULL,
        note VARCHAR(1000) NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uq_user_training_plan_days_index (plan_id, day_index),
        KEY idx_user_training_plan_days_date (scheduled_date),
        CONSTRAINT fk_user_training_plan_days_plan FOREIGN KEY (plan_id) REFERENCES user_training_plans(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_training_plan_items (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        plan_day_id BIGINT UNSIGNED NOT NULL,
        sort_order INT NOT NULL,
        name VARCHAR(160) NOT NULL,
        sets INT NOT NULL DEFAULT 1,
        reps INT NULL,
        duration_minutes INT NULL,
        rest_seconds INT NOT NULL DEFAULT 60,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uq_user_training_plan_items_order (plan_day_id, sort_order),
        CONSTRAINT fk_user_training_plan_items_day FOREIGN KEY (plan_day_id) REFERENCES user_training_plan_days(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_nutrition_records (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        client_scan_id VARCHAR(64) NOT NULL,
        food_name VARCHAR(120) NOT NULL,
        food_label VARCHAR(255) NOT NULL,
        confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
        serving_grams INT UNSIGNED NOT NULL DEFAULT 0,
        calories_kcal INT UNSIGNED NOT NULL DEFAULT 0,
        protein_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        dietary_fiber_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        carbs_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        fat_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        analysis_summary TEXT NULL,
        data_source VARCHAR(120) NULL,
        item_summary VARCHAR(1000) NULL,
        photo_filename VARCHAR(255) NULL,
        photo_mime_type VARCHAR(80) NULL,
        photo_size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_user_nutrition_scan (user_id, client_scan_id),
        KEY idx_nutrition_records_user_id (user_id),
        KEY idx_nutrition_records_created_at (created_at),
        CONSTRAINT fk_nutrition_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  await pool.execute(
    `
      CREATE TABLE IF NOT EXISTS user_selected_recipes (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        client_recipe_id VARCHAR(64) NOT NULL,
        title VARCHAR(180) NOT NULL,
        image_url VARCHAR(1000) NOT NULL,
        prep_time_minutes INT UNSIGNED NOT NULL DEFAULT 0,
        calories_kcal INT UNSIGNED NOT NULL DEFAULT 0,
        carbs_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        protein_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        fiber_grams DECIMAL(8,2) NOT NULL DEFAULT 0.00,
        ingredients TEXT NOT NULL,
        steps TEXT NULL,
        selected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_user_selected_recipe (user_id, client_recipe_id),
        KEY idx_selected_recipes_user_id (user_id),
        KEY idx_selected_recipes_selected_at (selected_at),
        CONSTRAINT fk_selected_recipes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `,
  );

  // #region debug-point A:schema-columns
  const [trainingStatsColumns] = await pool.execute("SHOW COLUMNS FROM user_training_stats");
  reportDebugEvent("A", "user_training_stats columns loaded", {
    columns: trainingStatsColumns.map((column) => column.Field),
  });
  // #endregion
}

async function startServer() {
  try {
    await ensureSchema();
    const port = Number.parseInt(process.env.PORT ?? "8788", 10);
    app.listen(port, "0.0.0.0", () => {
      console.log(`Auth server listening on http://0.0.0.0:${port}`);
    });
  } catch (error) {
    console.error("Server startup failed:", error);
    process.exit(1);
  }
}

startServer();

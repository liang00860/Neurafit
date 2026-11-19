import { createHash } from "node:crypto"

import { query, withTransaction } from "../db/mysql.mjs"

const SQUAT_KCAL_PER_REP = 0.6
const PUSH_UP_KCAL_PER_REP = 0.7
const TRAINING_PLAN_DAYS = 7
const TRAINING_PLAN_DAY_MS = 24 * 60 * 60 * 1000
export const TRAINING_PLAN_CALENDAR_ID = "ai-training-plan"
export const TRAINING_PLAN_CALENDAR = {
  color: "#54d8c7",
  group: "Health",
  id: TRAINING_PLAN_CALENDAR_ID,
  name: "AI Training Plan",
  visible: true,
}

function httpError(statusCode, message) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function normalizeString(value) {
  return typeof value === "string" ? value.trim() : ""
}

function clipString(value, maxLength, fallback = "") {
  const normalized = normalizeString(value)
  return (normalized || fallback).slice(0, maxLength)
}

function normalizeNonNegativeInteger(value) {
  const normalized = Number.parseInt(value, 10)
  return Number.isFinite(normalized) && normalized >= 0 ? normalized : null
}

function normalizePositiveInteger(value) {
  const normalized = Number.parseInt(value, 10)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : null
}

function normalizeActionName(value) {
  const normalized = normalizeString(value)
    .replace(/[\s-]+/g, "_")
    .toUpperCase()

  if (normalized === "SQUAT" || normalized === "DEEP_SQUAT") {
    return "SQUAT"
  }

  if (
    normalized === "PUSH_UP" ||
    normalized === "PUSHUP" ||
    normalized === "PUSH_UPS"
  ) {
    return "PUSH_UP"
  }

  return normalized.slice(0, 32)
}

function toMysqlDateTimeFromEpochMs(value) {
  const timestamp = Number.parseInt(value, 10)
  if (!Number.isFinite(timestamp) || timestamp <= 0) {
    return null
  }

  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  return date.toISOString().slice(0, 19).replace("T", " ")
}

function addDays(value, days) {
  const date = value instanceof Date ? new Date(value) : new Date(value)
  date.setDate(date.getDate() + days)
  return date
}

function formatDateKey(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ""
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function formatTime(dateKey, hour = 7, minute = 30) {
  return `${dateKey}T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00`
}

function dateKeyFromDb(value) {
  if (!value) return ""
  if (value instanceof Date) return formatDateKey(value)
  return String(value).slice(0, 10)
}

function isDateInRange(dateKey, range) {
  const date = new Date(`${dateKey}T12:00:00`)
  const start = new Date(range.start)
  const end = new Date(range.end)
  return (
    !Number.isNaN(date.getTime()) &&
    !Number.isNaN(start.getTime()) &&
    !Number.isNaN(end.getTime()) &&
    date >= start &&
    date <= end
  )
}

function parseJsonFromModelText(text) {
  const normalized = normalizeString(text)
  if (!normalized) return null

  try {
    return JSON.parse(normalized)
  } catch {
    const match = normalized.match(/\{[\s\S]*\}/)
    if (!match) return null
    try {
      return JSON.parse(match[0])
    } catch {
      return null
    }
  }
}

function normalizeTrainingSessionPayload(value) {
  if (!value || typeof value !== "object") {
    return null
  }

  const clientTrainingId = normalizePositiveInteger(value.clientTrainingId)
  const actionName = normalizeActionName(value.actionName)
  const durationMs = normalizeNonNegativeInteger(value.durationMs)
  const repCount = normalizeNonNegativeInteger(value.repCount)
  const averageScore = normalizeNonNegativeInteger(value.averageScore)
  const trainingStartedAt = toMysqlDateTimeFromEpochMs(value.startedAtMs)

  if (
    clientTrainingId == null ||
    !actionName ||
    durationMs == null ||
    repCount == null ||
    averageScore == null
  ) {
    return null
  }

  return {
    actionName,
    averageScore,
    clientTrainingId,
    durationMs,
    repCount,
    trainingStartedAt,
  }
}

function trainingCaloriesSql() {
  return `CASE
    WHEN UPPER(REPLACE(action_name, '-', '_')) IN ('SQUAT', 'DEEP_SQUAT') THEN rep_count * ${SQUAT_KCAL_PER_REP}
    WHEN UPPER(REPLACE(action_name, '-', '_')) IN ('PUSH_UP', 'PUSHUP', 'PUSH_UPS') THEN rep_count * ${PUSH_UP_KCAL_PER_REP}
    ELSE 0
  END`
}

let trainingPlanSchemaReady

async function ensureUserTrainingPlanSchema() {
  if (!trainingPlanSchemaReady) {
    trainingPlanSchemaReady = (async () => {
      await query(
        `CREATE TABLE IF NOT EXISTS user_training_plans (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`
      )
      await query(
        `CREATE TABLE IF NOT EXISTS user_training_plan_days (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`
      )
      await query(
        `CREATE TABLE IF NOT EXISTS user_training_plan_items (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`
      )
    })()
  }
  await trainingPlanSchemaReady
}

function serializeSummary(row = {}) {
  const totalTrainingTimeMs = Number(row.totalTrainingTimeMs ?? 0)
  const kcalBurn = Number(row.kcalBurn ?? 0)
  const sessionCount = Number(row.sessionCount ?? 0)
  const last7DaysDurationMs = Number(row.last7DaysDurationMs ?? 0)
  const previous7DaysDurationMs = Number(row.previous7DaysDurationMs ?? 0)

  return {
    deltaFromLastWeekMs: last7DaysDurationMs - previous7DaysDurationMs,
    kcalBurn,
    sessionCount,
    totalCaloriesKcal: kcalBurn,
    totalDurationMs: totalTrainingTimeMs,
    totalTrainingTimeMs,
  }
}

export function extractBearerToken(headerValue) {
  const normalized = normalizeString(headerValue)
  if (!normalized.toLowerCase().startsWith("bearer ")) {
    return ""
  }

  return normalized.slice(7).trim()
}

export async function authenticateToken(token) {
  const normalizedToken = normalizeString(token)
  if (!normalizedToken) {
    throw httpError(401, "Please log in first.")
  }

  const tokenHash = createHash("sha256").update(normalizedToken).digest("hex")
  const rows = await query(
    `SELECT u.id, u.username, u.email
     FROM user_sessions s
     INNER JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ?
       AND s.expires_at > CURRENT_TIMESTAMP(3)
       AND u.status = 1
     LIMIT 1`,
    [tokenHash]
  )

  if (!rows[0]) {
    throw httpError(401, "Your login session has expired.")
  }

  return {
    email: rows[0].email ?? null,
    id: Number(rows[0].id),
    username: rows[0].username,
  }
}

async function refreshUserTrainingStats(userId, connection) {
  await connection.execute(
    `INSERT INTO user_training_stats (
        user_id,
        total_duration_ms,
        total_calories_kcal,
        session_count,
        last_synced_at
      )
      SELECT
        ?,
        COALESCE(SUM(duration_ms), 0) AS total_duration_ms,
        COALESCE(SUM(${trainingCaloriesSql()}), 0) AS total_calories_kcal,
        COUNT(*) AS session_count,
        UTC_TIMESTAMP(3)
      FROM user_training_sessions
      WHERE user_id = ?
      ON DUPLICATE KEY UPDATE
        total_duration_ms = VALUES(total_duration_ms),
        total_calories_kcal = VALUES(total_calories_kcal),
        session_count = VALUES(session_count),
        last_synced_at = VALUES(last_synced_at)`,
    [userId, userId]
  )
}

export async function syncUserTrainingSessions(rawSessions, userId) {
  const sessions = (Array.isArray(rawSessions) ? rawSessions : [])
    .map(normalizeTrainingSessionPayload)
    .filter(Boolean)

  if (sessions.length === 0) {
    throw httpError(400, "Please provide at least one training session.")
  }

  return withTransaction(async (connection) => {
    for (const session of sessions) {
      await connection.execute(
        `INSERT INTO user_training_sessions (
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
            average_score = VALUES(average_score)`,
        [
          userId,
          session.clientTrainingId,
          session.actionName,
          session.trainingStartedAt,
          session.durationMs,
          session.repCount,
          session.averageScore,
        ]
      )
    }

    await refreshUserTrainingStats(userId, connection)

    return { syncedCount: sessions.length }
  })
}

export async function getUserTrainingSummary(userId) {
  await withTransaction(async (connection) => {
    await refreshUserTrainingStats(userId, connection)
  })

  const rows = await query(
    `SELECT
        s.total_duration_ms AS totalTrainingTimeMs,
        s.total_calories_kcal AS kcalBurn,
        s.session_count AS sessionCount,
        COALESCE(SUM(CASE
          WHEN ts.training_started_at >= (UTC_TIMESTAMP(3) - INTERVAL 7 DAY) THEN ts.duration_ms
          ELSE 0
        END), 0) AS last7DaysDurationMs,
        COALESCE(SUM(CASE
          WHEN ts.training_started_at < (UTC_TIMESTAMP(3) - INTERVAL 7 DAY)
           AND ts.training_started_at >= (UTC_TIMESTAMP(3) - INTERVAL 14 DAY) THEN ts.duration_ms
          ELSE 0
        END), 0) AS previous7DaysDurationMs
      FROM user_training_stats s
      LEFT JOIN user_training_sessions ts ON ts.user_id = s.user_id
      WHERE s.user_id = ?
      GROUP BY s.user_id, s.total_duration_ms, s.total_calories_kcal, s.session_count`,
    [userId]
  )

  return serializeSummary(rows[0])
}

async function getRecentTrainingContext(userId) {
  const summary = await getUserTrainingSummary(userId)
  const recentSessions = await query(
    `SELECT action_name, duration_ms, rep_count, average_score, training_started_at
     FROM user_training_sessions
     WHERE user_id = ?
     ORDER BY COALESCE(training_started_at, created_at) DESC
     LIMIT 12`,
    [userId]
  )

  return {
    recentSessions: recentSessions.map((row) => ({
      actionName: row.action_name,
      averageScore: Number(row.average_score ?? 0),
      durationMs: Number(row.duration_ms ?? 0),
      repCount: Number(row.rep_count ?? 0),
      startedAt: row.training_started_at,
    })),
    summary,
  }
}

function normalizePlanItem(value, fallback) {
  return {
    durationMinutes:
      normalizeNonNegativeInteger(value?.durationMinutes) ??
      normalizeNonNegativeInteger(value?.duration_minutes) ??
      fallback?.durationMinutes ??
      null,
    name: clipString(value?.name, 160, fallback?.name ?? "自重训练"),
    reps:
      normalizeNonNegativeInteger(value?.reps) ??
      fallback?.reps ??
      null,
    restSeconds:
      normalizeNonNegativeInteger(value?.restSeconds) ??
      normalizeNonNegativeInteger(value?.rest_seconds) ??
      fallback?.restSeconds ??
      60,
    sets:
      normalizePositiveInteger(value?.sets) ??
      fallback?.sets ??
      1,
  }
}

function normalizePlanDay(value, fallback, index, startDate) {
  const fallbackDate = formatDateKey(addDays(startDate, index))
  const rawItems = Array.isArray(value?.items) ? value.items : []
  const fallbackItems = Array.isArray(fallback?.items) ? fallback.items : []
  const maxItems = Math.max(rawItems.length, fallbackItems.length, 1)
  const items = Array.from({ length: maxItems }, (_, itemIndex) =>
    normalizePlanItem(rawItems[itemIndex], fallbackItems[itemIndex])
  )

  return {
    date:
      dateKeyFromDb(value?.date) ||
      dateKeyFromDb(value?.scheduledDate) ||
      fallback?.date ||
      fallbackDate,
    focus: clipString(value?.focus, 120, fallback?.focus ?? "综合训练"),
    items,
    note: clipString(value?.note, 1000, fallback?.note ?? ""),
    title: clipString(value?.title, 180, fallback?.title ?? `第${index + 1}天训练`),
  }
}

function normalizePlan(value, fallback = buildAdaptivePlan({ recentSessions: [], summary: {} })) {
  const today = new Date()
  const fallbackStart = dateKeyFromDb(fallback.startsOn) || formatDateKey(today)
  const startDate = new Date(`${fallbackStart}T12:00:00`)
  const rawDays = Array.isArray(value?.days) ? value.days : []
  const fallbackDays = Array.isArray(fallback.days) ? fallback.days : []
  const dayCount = Math.max(TRAINING_PLAN_DAYS, rawDays.length, fallbackDays.length)
  const days = Array.from({ length: dayCount }, (_, index) =>
    normalizePlanDay(rawDays[index], fallbackDays[index], index, startDate)
  ).slice(0, TRAINING_PLAN_DAYS)

  return {
    days,
    difficulty: clipString(value?.difficulty, 64, fallback.difficulty ?? "中等"),
    endsOn: dateKeyFromDb(value?.endsOn) || days.at(-1)?.date || fallbackStart,
    goal: clipString(value?.goal, 120, fallback.goal ?? "综合体能"),
    startsOn: dateKeyFromDb(value?.startsOn) || days[0]?.date || fallbackStart,
    summary: clipString(
      value?.summary,
      1000,
      fallback.summary ?? "已根据你的训练记录生成一周训练安排。"
    ),
    title: clipString(value?.title, 180, fallback.title ?? "AI训练计划"),
  }
}

function buildAdaptivePlan(context, request = {}) {
  const recentSessions = context.recentSessions ?? []
  const sessionCount = Number(context.summary?.sessionCount ?? 0)
  const averageScore =
    recentSessions.reduce((total, session) => total + session.averageScore, 0) /
    Math.max(1, recentSessions.length)
  const squatVolume = recentSessions
    .filter((session) => session.actionName === "SQUAT")
    .reduce((total, session) => total + session.repCount, 0)
  const pushUpVolume = recentSessions
    .filter((session) => session.actionName === "PUSH_UP")
    .reduce((total, session) => total + session.repCount, 0)
  const needsFoundation = sessionCount === 0 || averageScore < 72
  const needsUpperBalance = pushUpVolume < squatVolume * 0.75
  const goal = needsFoundation
    ? "动作稳定与基础力量"
    : needsUpperBalance
      ? "上肢力量补强"
      : "全身力量递进"
  const difficulty = sessionCount > 12 && averageScore >= 82 ? "进阶" : "中等"
  const start = new Date()
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
  ]

  const days = planTemplates.map((template, index) => ({
    ...template,
    date: formatDateKey(addDays(start, index)),
  }))

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
  }
}

async function generatePlanWithModel(context, request, fallback) {
  const token = process.env.DEEPSEEK_API_KEY
  if (!token) return null

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
  })

  if (!response.ok) return null
  const root = await response.json().catch(() => null)
  const content = root?.choices?.[0]?.message?.content
  const parsed = parseJsonFromModelText(content)
  return parsed?.plan ?? parsed
}

function normalizeReadablePlanItem(value, fallback) {
  return {
    durationMinutes:
      normalizeNonNegativeInteger(value?.durationMinutes) ??
      normalizeNonNegativeInteger(value?.duration_minutes) ??
      fallback?.durationMinutes ??
      null,
    name: clipString(value?.name, 160, fallback?.name ?? "自重训练"),
    reps:
      normalizeNonNegativeInteger(value?.reps) ??
      fallback?.reps ??
      null,
    restSeconds:
      normalizeNonNegativeInteger(value?.restSeconds) ??
      normalizeNonNegativeInteger(value?.rest_seconds) ??
      fallback?.restSeconds ??
      60,
    sets:
      normalizePositiveInteger(value?.sets) ??
      fallback?.sets ??
      1,
  }
}

function normalizeReadablePlanDay(value, fallback, index, startDate) {
  const fallbackDate = formatDateKey(addDays(startDate, index))
  const rawItems = Array.isArray(value?.items) ? value.items : []
  const fallbackItems = Array.isArray(fallback?.items) ? fallback.items : []
  const maxItems = Math.max(rawItems.length, fallbackItems.length, 1)
  const items = Array.from({ length: maxItems }, (_, itemIndex) =>
    normalizeReadablePlanItem(rawItems[itemIndex], fallbackItems[itemIndex])
  )

  return {
    date:
      dateKeyFromDb(value?.date) ||
      dateKeyFromDb(value?.scheduledDate) ||
      fallback?.date ||
      fallbackDate,
    focus: clipString(value?.focus, 120, fallback?.focus ?? "综合训练"),
    items,
    note: clipString(value?.note, 1000, fallback?.note ?? ""),
    title: clipString(value?.title, 180, fallback?.title ?? `第${index + 1}天训练`),
  }
}

function normalizeReadablePlan(
  value,
  fallback = buildReadableAdaptivePlan({ recentSessions: [], summary: {} })
) {
  const today = new Date()
  const fallbackStart = dateKeyFromDb(fallback.startsOn) || formatDateKey(today)
  const startDate = new Date(`${fallbackStart}T12:00:00`)
  const rawDays = Array.isArray(value?.days) ? value.days : []
  const fallbackDays = Array.isArray(fallback.days) ? fallback.days : []
  const dayCount = Math.max(TRAINING_PLAN_DAYS, rawDays.length, fallbackDays.length)
  const days = Array.from({ length: dayCount }, (_, index) =>
    normalizeReadablePlanDay(rawDays[index], fallbackDays[index], index, startDate)
  ).slice(0, TRAINING_PLAN_DAYS)

  return {
    days,
    difficulty: clipString(value?.difficulty, 64, fallback.difficulty ?? "中等"),
    endsOn: dateKeyFromDb(value?.endsOn) || days.at(-1)?.date || fallbackStart,
    goal: clipString(value?.goal, 120, fallback.goal ?? "综合体能"),
    startsOn: dateKeyFromDb(value?.startsOn) || days[0]?.date || fallbackStart,
    summary: clipString(
      value?.summary,
      1000,
      fallback.summary ?? "已根据你的训练状态生成一周训练安排。"
    ),
    title: clipString(value?.title, 180, fallback.title ?? "AI训练计划"),
  }
}

function buildReadableAdaptivePlan(context, request = {}) {
  const recentSessions = context.recentSessions ?? []
  const sessionCount = Number(context.summary?.sessionCount ?? 0)
  const averageScore =
    recentSessions.reduce((total, session) => total + session.averageScore, 0) /
    Math.max(1, recentSessions.length)
  const squatVolume = recentSessions
    .filter((session) => session.actionName === "SQUAT")
    .reduce((total, session) => total + session.repCount, 0)
  const pushUpVolume = recentSessions
    .filter((session) => session.actionName === "PUSH_UP")
    .reduce((total, session) => total + session.repCount, 0)
  const needsFoundation = sessionCount === 0 || averageScore < 72
  const needsUpperBalance = pushUpVolume < squatVolume * 0.75
  const goal = needsFoundation
    ? "动作稳定与基础力量"
    : needsUpperBalance
      ? "上肢力量补强"
      : "全身力量递进"
  const difficulty = sessionCount > 12 && averageScore >= 82 ? "进阶" : "中等"
  const start = new Date()
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
  ]

  const days = planTemplates.map((template, index) => ({
    ...template,
    date: formatDateKey(addDays(start, index)),
  }))

  return {
    days,
    difficulty,
    endsOn: days.at(-1).date,
    goal,
    startsOn: days[0].date,
    summary:
      sessionCount === 0
        ? "还没有足够训练记录，先用一周基础计划建立动作质量和训练节奏。"
        : `已参考最近${recentSessions.length}次训练、总训练${sessionCount}次和动作表现，安排强弱交替的一周计划。`,
    title: request.goal === "balanced_strength" ? "AI均衡力量训练计划" : "AI个性化训练计划",
  }
}

async function generateReadablePlanWithModel(context, request, fallback) {
  const token = process.env.DEEPSEEK_API_KEY
  if (!token) return null

  const response = await fetch("https://api.deepseek.com/chat/completions", {
    body: JSON.stringify({
      messages: [
        {
          content:
            "你是NEURAFIT的训练计划大模型。只返回JSON，不要Markdown。JSON结构必须为{\"plan\":{\"title\",\"goal\",\"difficulty\",\"summary\",\"startsOn\",\"endsOn\",\"days\":[{\"date\",\"title\",\"focus\",\"note\",\"items\":[{\"name\",\"sets\",\"reps\",\"durationMinutes\",\"restSeconds\"}]}]}}。请生成7天中文训练计划，适合自重训练，以深蹲、俯卧撑、核心稳定和恢复为主，并根据用户历史训练数据调整强度。",
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
  })

  if (!response.ok) return null
  const root = await response.json().catch(() => null)
  const content = root?.choices?.[0]?.message?.content
  const parsed = parseJsonFromModelText(content)
  return parsed?.plan ?? parsed
}

export async function recommendUserTrainingPlan(userId, request = {}) {
  const context = await getRecentTrainingContext(userId)
  const fallback = buildReadableAdaptivePlan(context, request)
  let modelPlan = null
  try {
    modelPlan = await generateReadablePlanWithModel(context, request, fallback)
  } catch (error) {
    console.warn("AI training plan generation fell back to local rules:", error)
  }

  return normalizeReadablePlan(modelPlan ?? fallback, fallback)
}

async function executePlanRows(connection, sql, params) {
  if (connection?.execute) {
    const [rows] = await connection.execute(sql, params)
    return rows
  }
  return query(sql, params)
}

async function serializeTrainingPlan(planId, connection = null) {
  const planRows = await executePlanRows(
    connection,
    `SELECT *
     FROM user_training_plans
     WHERE id = ?
     LIMIT 1`,
    [planId]
  )
  const planRow = planRows[0]
  if (!planRow) return null

  const dayRows = await executePlanRows(
    connection,
    `SELECT *
     FROM user_training_plan_days
     WHERE plan_id = ?
     ORDER BY day_index`,
    [planId]
  )
  const dayIds = dayRows.map((row) => row.id)
  const itemsByDay = new Map()

  if (dayIds.length) {
    const itemRows = await executePlanRows(
      connection,
      `SELECT *
       FROM user_training_plan_items
       WHERE plan_day_id IN (${dayIds.map(() => "?").join(",")})
       ORDER BY plan_day_id, sort_order`,
      dayIds
    )
    for (const item of itemRows) {
      itemsByDay.set(item.plan_day_id, [
        ...(itemsByDay.get(item.plan_day_id) ?? []),
        {
          durationMinutes:
            item.duration_minutes == null ? null : Number(item.duration_minutes),
          name: item.name,
          reps: item.reps == null ? null : Number(item.reps),
          restSeconds: Number(item.rest_seconds ?? 60),
          sets: Number(item.sets ?? 1),
        },
      ])
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
  }
}

export async function adoptUserTrainingPlan(rawPlan, userId) {
  await ensureUserTrainingPlanSchema()
  const fallback = buildAdaptivePlan(await getRecentTrainingContext(userId), {})
  const plan = normalizePlan(rawPlan, fallback)

  return withTransaction(async (connection) => {
    await connection.execute(
      `UPDATE user_training_plans
       SET status = 'replaced'
       WHERE user_id = ? AND status = 'active'`,
      [userId]
    )

    const [result] = await connection.execute(
      `INSERT INTO user_training_plans
        (user_id, title, goal, difficulty, summary, source, status, starts_on, ends_on, adopted_at)
       VALUES (?, ?, ?, ?, ?, 'ai', 'active', ?, ?, UTC_TIMESTAMP(3))`,
      [
        userId,
        plan.title,
        plan.goal,
        plan.difficulty,
        plan.summary,
        plan.startsOn,
        plan.endsOn,
      ]
    )

    for (const [dayIndex, day] of plan.days.entries()) {
      const [dayResult] = await connection.execute(
        `INSERT INTO user_training_plan_days
          (plan_id, day_index, scheduled_date, title, focus, note)
         VALUES (?, ?, ?, ?, ?, ?)`,
        [result.insertId, dayIndex, day.date, day.title, day.focus, day.note]
      )

      for (const [itemIndex, item] of day.items.entries()) {
        await connection.execute(
          `INSERT INTO user_training_plan_items
            (plan_day_id, sort_order, name, sets, reps, duration_minutes, rest_seconds)
           VALUES (?, ?, ?, ?, ?, ?, ?)`,
          [
            dayResult.insertId,
            itemIndex,
            item.name,
            item.sets,
            item.reps,
            item.durationMinutes,
            item.restSeconds,
          ]
        )
      }
    }

    return serializeTrainingPlan(result.insertId, connection)
  })
}

export async function getCurrentUserTrainingPlan(userId) {
  await ensureUserTrainingPlanSchema()
  if (!userId) return null
  const rows = await query(
    `SELECT id
     FROM user_training_plans
     WHERE user_id = ? AND status = 'active'
     ORDER BY adopted_at DESC, id DESC
     LIMIT 1`,
    [userId]
  )

  return rows[0] ? serializeTrainingPlan(rows[0].id) : null
}

async function getLatestActiveTrainingPlanUserId() {
  await ensureUserTrainingPlanSchema()
  const rows = await query(
    `SELECT user_id
     FROM user_training_plans
     WHERE status = 'active'
     ORDER BY adopted_at DESC, id DESC
     LIMIT 1`
  )

  return rows[0] ? Number(rows[0].user_id) : null
}

async function getVisibleTrainingPlan(userId) {
  const currentUserPlan = await getCurrentUserTrainingPlan(userId)
  if (currentUserPlan) {
    return {
      plan: currentUserPlan,
      userId,
    }
  }

  const latestUserId = await getLatestActiveTrainingPlanUserId()
  if (!latestUserId) {
    return null
  }

  const latestPlan = await getCurrentUserTrainingPlan(latestUserId)
  return latestPlan
    ? {
        plan: latestPlan,
        userId: latestUserId,
      }
    : null
}

function describePlanItem(item) {
  const target =
    item.reps != null
      ? `${item.reps}次`
      : item.durationMinutes != null
        ? `${item.durationMinutes}分钟`
        : "按状态完成"
  return `${item.name} ${item.sets}组 x ${target}`
}

function describeReadablePlanItem(item) {
  const target =
    item.reps != null
      ? `${item.reps}次`
      : item.durationMinutes != null
        ? `${item.durationMinutes}分钟`
        : "按状态完成"
  return `${item.name} ${item.sets}组 x ${target}`
}

function planDayToOccurrence(plan, day) {
  const plannedMinutes = day.items.reduce(
    (total, item) =>
      total + (item.durationMinutes ?? Math.max(8, item.sets * 5)),
    0
  )
  const start = formatTime(day.date)
  const endDate = new Date(start)
  endDate.setMinutes(endDate.getMinutes() + Math.max(30, plannedMinutes))
  const end = `${day.date}T${String(endDate.getHours()).padStart(2, "0")}:${String(
    endDate.getMinutes()
  ).padStart(2, "0")}:00`
  const id = `${TRAINING_PLAN_CALENDAR_ID}-${day.date}`
  const notes = [plan.summary, day.note, ...day.items.map(describeReadablePlanItem)]
    .filter(Boolean)
    .join("\n")

  return {
    alert: "10 minutes before",
    allDay: false,
    attachments: [],
    availability: "busy",
    calendarId: TRAINING_PLAN_CALENDAR_ID,
    color: TRAINING_PLAN_CALENDAR.color,
    end,
    id,
    invitees: [],
    isPrivate: false,
    location: "NEURAFIT",
    meetingLink: "",
    notes,
    occurrenceDateKey: day.date,
    occurrenceEnd: end,
    occurrenceId: `${id}:${day.date}`,
    occurrenceStart: start,
    reminder: true,
    repeat: {
      endMode: "never",
      frequency: "none",
      interval: 1,
    },
    secondAlert: "None",
    sourceEventId: id,
    start,
    timeZone: "Local Time",
    title: day.title || plan.title,
    travelTime: "None",
    url: "",
  }
}

export async function listUserTrainingPlanOccurrences(userId, range, filters = {}) {
  if (filters.calendarIds?.length && !filters.calendarIds.includes(TRAINING_PLAN_CALENDAR_ID)) {
    return []
  }

  const visiblePlan = await getVisibleTrainingPlan(userId)
  if (!visiblePlan) return []

  return visiblePlan.plan.days
    .filter((day) => isDateInRange(day.date, range))
    .map((day) => planDayToOccurrence(visiblePlan.plan, day))
}

export async function searchUserTrainingPlanOccurrences(userId, queryText) {
  const visiblePlan = await getVisibleTrainingPlan(userId)
  if (!visiblePlan) return []
  const { plan } = visiblePlan
  const normalized = normalizeString(queryText).toLowerCase()
  const matches = (value) => normalizeString(value).toLowerCase().includes(normalized)

  return plan.days
    .filter(
      (day) =>
        !normalized ||
        matches(day.title) ||
        matches(day.focus) ||
        matches(day.note) ||
        day.items.some((item) => matches(item.name))
    )
    .map((day) => planDayToOccurrence(plan, day))
    .slice(0, 5)
}

function weekdayLabel(dateKey) {
  const day = new Date(`${dateKey}T12:00:00`).getDay()
  return ["日", "一", "二", "三", "四", "五", "六"][day] ?? ""
}

function formatMinutes(minutes) {
  if (minutes <= 0) return "0m"
  const hours = Math.floor(minutes / 60)
  const remaining = minutes % 60
  if (hours > 0) {
    return `${hours}h ${String(remaining).padStart(2, "0")}m`
  }
  return `${minutes}m`
}

function formatDurationMs(durationMs) {
  const normalized = Math.max(0, Number(durationMs ?? 0))
  const totalMinutes = Math.floor(normalized / 60000)
  return formatMinutes(totalMinutes)
}

function readableWeekdayLabel(dateKey) {
  const day = new Date(`${dateKey}T12:00:00`).getDay()
  return ["日", "一", "二", "三", "四", "五", "六"][day] ?? ""
}

export async function getUserTrainingActivity(userId) {
  const visiblePlan = await getVisibleTrainingPlan(userId)
  if (!visiblePlan) {
    return null
  }
  const { plan } = visiblePlan
  const summary = await getUserTrainingSummary(visiblePlan.userId)
  const todayRows = await query(
    `SELECT COALESCE(SUM(duration_ms), 0) AS todayDurationMs
     FROM user_training_sessions
     WHERE user_id = ?
       AND COALESCE(training_started_at, created_at) >= UTC_DATE()
       AND COALESCE(training_started_at, created_at) < (UTC_DATE() + INTERVAL 1 DAY)`,
    [visiblePlan.userId]
  )
  const todayDurationMs = Number(todayRows[0]?.todayDurationMs ?? 0)

  const today = formatDateKey(new Date())
  const selectedIndex = Math.max(
    0,
    plan.days.findIndex((day) => day.date === today)
  )

  return {
    dates: plan.days.map((day, index) => ({
      date: day.date.slice(8, 10),
      day: readableWeekdayLabel(day.date),
      selected: index === selectedIndex,
    })),
    planTitle: plan.title,
    rows: [
      {
        label: "次数",
        value: `${Number(summary.sessionCount ?? 0)}次`,
      },
      {
        label: "今日训练",
        value: formatDurationMs(todayDurationMs),
      },
      {
        label: "总时长",
        value: formatDurationMs(summary.totalDurationMs ?? 0),
      },
    ],
  }
}

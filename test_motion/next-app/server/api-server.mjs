import "./env.mjs"
import express from "express"

import { loginUser, registerUser } from "./repositories/auth-repository.mjs"
import {
  createCalendar,
  createEvent,
  deleteCalendar,
  deleteEvent,
  exportCalendar,
  importCalendar,
  listCalendars,
  listEvents,
  searchEvents,
  updateCalendar,
  updateEvent,
} from "./repositories/calendar-repository.mjs"
import {
  listDesktopState,
  saveWindowState,
} from "./repositories/desktop-repository.mjs"
import {
  createTrainingSession,
  listTrainingSessions,
} from "./repositories/motion-repository.mjs"
import {
  authenticateToken,
  adoptUserTrainingPlan,
  extractBearerToken,
  getCurrentUserTrainingPlan,
  getUserTrainingActivity,
  getUserTrainingSummary,
  recommendUserTrainingPlan,
  syncUserTrainingSessions,
} from "./repositories/user-training-repository.mjs"
import {
  createWeatherLocation,
  listWeatherLocations,
  listWeatherSnapshots,
  saveWeatherSnapshot,
} from "./repositories/weather-repository.mjs"
import { query } from "./db/mysql.mjs"
import {
  getFeaturedNeteaseTracks,
  searchNeteaseTracks,
} from "./services/netease-music.mjs"

const PORT = Number(process.env.API_PORT ?? 8788)
const app = express()
const DEBUG_SERVER_URL = process.env.DEBUG_SERVER_URL?.trim() || ""
const DEBUG_SESSION_ID = "calendar-delete-aborted"
const WEATHER_ALERTS_USER_AGENT =
  process.env.WEATHER_ALERTS_USER_AGENT?.trim() ||
  "neurafit-weather-alerts/1.0 (contact: local)"
const QWEATHER_API_KEY = process.env.QWEATHER_API_KEY?.trim() || ""
const QWEATHER_API_HOST = process.env.QWEATHER_API_HOST?.trim() || ""

app.set("trust proxy", true)
app.use(express.json({ limit: "8mb" }))

app.use((request, response, next) => {
  response.setHeader("access-control-allow-origin", request.headers.origin ?? "*")
  response.setHeader("access-control-allow-methods", "GET,POST,PATCH,DELETE,OPTIONS")
  response.setHeader("access-control-allow-headers", "authorization,content-type")
  if (request.method === "OPTIONS") {
    response.status(204).end()
    return
  }
  next()
})

function asyncRoute(handler) {
  return async (request, response, next) => {
    try {
      await handler(request, response)
    } catch (error) {
      next(error)
    }
  }
}

function requestAuthContext(request) {
  return {
    ip: request.ip ?? request.socket?.remoteAddress ?? null,
    userAgent: request.get("user-agent") ?? null,
  }
}

function normalizeString(value) {
  return typeof value === "string" ? value.trim() : ""
}

function normalizeOptionalString(value, maxLength) {
  const normalized = normalizeString(value)
  return normalized ? normalized.slice(0, maxLength) : null
}

function normalizeRequiredString(value, maxLength) {
  return normalizeString(value).slice(0, maxLength)
}

function normalizeNonNegativeInteger(value) {
  const normalized = Number.parseInt(value, 10)
  return Number.isFinite(normalized) && normalized >= 0 ? normalized : null
}

function normalizeNonNegativeDecimal(value) {
  const normalized = Number.parseFloat(value)
  if (!Number.isFinite(normalized) || normalized < 0) {
    return null
  }
  return Number(normalized.toFixed(2))
}

function normalizeConfidence(value) {
  const normalized = Number.parseFloat(value)
  if (!Number.isFinite(normalized)) {
    return null
  }
  return Math.min(Math.max(normalized, 0), 1)
}

function createHttpError(statusCode, message) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function normalizeCoordinate(value, min, max, name) {
  const normalized = Number.parseFloat(String(value))
  if (!Number.isFinite(normalized) || normalized < min || normalized > max) {
    throw createHttpError(400, `Invalid ${name}`)
  }
  return normalized
}

function normalizeCountryCode(value) {
  const normalized = normalizeString(value).toLowerCase()
  return normalized ? normalized.slice(0, 8) : null
}

function normalizeAlertText(value, maxLength) {
  if (typeof value !== "string") {
    return ""
  }

  return value.replace(/\s+/g, " ").trim().slice(0, maxLength)
}

function formatAlertDateTime(value) {
  if (!value) {
    return null
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  return new Intl.DateTimeFormat("zh-CN", {
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    month: "numeric",
    hour12: false,
  }).format(date)
}

function buildAlertTimeRange(startTime, endTime, publishedTime) {
  const start = formatAlertDateTime(startTime)
  const end = formatAlertDateTime(endTime)
  const published = formatAlertDateTime(publishedTime)

  if (start && end) {
    return `${start} - ${end}`
  }
  if (start) {
    return `开始于 ${start}`
  }
  if (end) {
    return `截至 ${end}`
  }
  if (published) {
    return `发布于 ${published}`
  }
  return "官方实时预警"
}

function sanitizeQWeatherHost(host) {
  return host.replace(/^https?:\/\//, "").replace(/\/+$/, "")
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options)
  if (!response.ok) {
    throw new Error(`request-failed:${response.status}`)
  }
  return response.json()
}

function parseQWeatherAlert(alert, locationName) {
  const title =
    normalizeAlertText(alert?.title, 120) ||
    normalizeAlertText(alert?.typeName, 120) ||
    "恶劣天气预警"
  const description =
    normalizeAlertText(alert?.text, 1200) ||
    normalizeAlertText(alert?.description, 1200) ||
    title

  return {
    area:
      normalizeAlertText(alert?.sender, 120) ||
      normalizeAlertText(alert?.area, 120) ||
      normalizeAlertText(locationName, 120) ||
      "官方预警",
    description,
    timeRange: buildAlertTimeRange(
      alert?.startTime,
      alert?.endTime,
      alert?.pubTime
    ),
    title,
  }
}

async function fetchQWeatherAlert(latitude, longitude, locationName) {
  if (!QWEATHER_API_KEY || !QWEATHER_API_HOST) {
    return null
  }

  const host = sanitizeQWeatherHost(QWEATHER_API_HOST)
  const locationLookupUrl = new URL(`https://${host}/geo/v2/city/lookup`)
  locationLookupUrl.searchParams.set(
    "location",
    `${longitude.toFixed(2)},${latitude.toFixed(2)}`
  )
  locationLookupUrl.searchParams.set("lang", "zh")
  locationLookupUrl.searchParams.set("number", "1")
  locationLookupUrl.searchParams.set("key", QWEATHER_API_KEY)

  const locationLookupData = await fetchJson(locationLookupUrl.toString(), {
    headers: {
      "accept-encoding": "gzip",
      "user-agent": WEATHER_ALERTS_USER_AGENT,
    },
  })

  const locationId = locationLookupData?.location?.[0]?.id
  if (!locationId) {
    return null
  }

  const alertUrl = new URL(`https://${host}/v7/warning/now`)
  alertUrl.searchParams.set("location", locationId)
  alertUrl.searchParams.set("lang", "zh")
  alertUrl.searchParams.set("key", QWEATHER_API_KEY)

  const alertData = await fetchJson(alertUrl.toString(), {
    headers: {
      "accept-encoding": "gzip",
      "user-agent": WEATHER_ALERTS_USER_AGENT,
    },
  })

  const activeAlerts = (alertData?.warning ?? alertData?.alerts ?? []).filter(
    (item) => {
      const status = normalizeString(item?.status).toLowerCase()
      const messageType = normalizeString(item?.messageType?.code).toLowerCase()
      return status !== "cancel" && messageType !== "cancel"
    }
  )

  if (!activeAlerts.length) {
    return null
  }

  return parseQWeatherAlert(activeAlerts[0], locationName)
}

function parseNwsAlert(alert, locationName) {
  const properties = alert?.properties ?? {}
  const title =
    normalizeAlertText(properties.headline, 120) ||
    normalizeAlertText(properties.event, 120) ||
    "Severe Weather Alert"
  const description =
    normalizeAlertText(properties.description, 1200) ||
    normalizeAlertText(properties.instruction, 1200) ||
    title

  return {
    area:
      normalizeAlertText(properties.areaDesc, 120) ||
      normalizeAlertText(locationName, 120) ||
      "National Weather Service",
    description,
    timeRange: buildAlertTimeRange(
      properties.effective ?? properties.onset,
      properties.expires,
      properties.sent
    ),
    title,
  }
}

async function fetchNwsAlert(latitude, longitude, locationName) {
  const url = new URL("https://api.weather.gov/alerts/active")
  url.searchParams.set("point", `${latitude},${longitude}`)

  const data = await fetchJson(url.toString(), {
    headers: {
      Accept: "application/geo+json",
      "User-Agent": WEATHER_ALERTS_USER_AGENT,
    },
  })

  const activeAlerts = (data?.features ?? []).filter((item) => {
    const status = normalizeString(item?.properties?.status).toLowerCase()
    return !status || status === "actual"
  })

  if (!activeAlerts.length) {
    return null
  }

  return parseNwsAlert(activeAlerts[0], locationName)
}

async function fetchCurrentWeatherAlert({
  countryCode,
  latitude,
  locationName,
  longitude,
}) {
  try {
    const qweatherAlert = await fetchQWeatherAlert(
      latitude,
      longitude,
      locationName
    )
    if (qweatherAlert) {
      return { alert: qweatherAlert, provider: "qweather" }
    }
  } catch (error) {
    console.warn("QWeather alert request failed", error)
  }

  if (countryCode === "us") {
    try {
      const nwsAlert = await fetchNwsAlert(latitude, longitude, locationName)
      return { alert: nwsAlert, provider: "weather.gov" }
    } catch (error) {
      console.warn("weather.gov alert request failed", error)
      return { alert: null, provider: "weather.gov" }
    }
  }

  return { alert: null, provider: QWEATHER_API_KEY ? "qweather" : null }
}

function normalizeNutritionRecordPayload(value) {
  if (!value || typeof value !== "object") {
    return null
  }

  const scanId = normalizeRequiredString(value.scanId, 64)
  const foodName = normalizeRequiredString(value.foodName, 120)
  const foodLabel = normalizeRequiredString(value.label, 255)
  const confidence = normalizeConfidence(value.confidence)
  const servingGrams = normalizeNonNegativeInteger(value.servingGrams)
  const caloriesKcal = normalizeNonNegativeInteger(value.caloriesKcal)
  const proteinGrams = normalizeNonNegativeDecimal(value.proteinGrams)
  const dietaryFiberGrams = normalizeNonNegativeDecimal(value.dietaryFiberGrams)
  const carbsGrams = normalizeNonNegativeDecimal(value.carbsGrams)
  const fatGrams = normalizeNonNegativeDecimal(value.fatGrams)

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
    return null
  }

  return {
    analysisSummary: normalizeOptionalString(value.analysisSummary, 2000),
    caloriesKcal,
    carbsGrams,
    confidence,
    dataSource: normalizeOptionalString(value.dataSource, 120),
    dietaryFiberGrams,
    fatGrams,
    foodLabel,
    foodName,
    imageUri: normalizeOptionalString(value.imageUri, 1000),
    itemSummary: normalizeOptionalString(value.itemSummary, 1000),
    proteinGrams,
    scanId,
    servingGrams,
  }
}

let nutritionSchemaReady

async function ensureNutritionSchema() {
  if (!nutritionSchemaReady) {
    nutritionSchemaReady = query(
      `CREATE TABLE IF NOT EXISTS user_nutrition_records (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        client_scan_id VARCHAR(64) NOT NULL,
        image_uri VARCHAR(1000) NULL,
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
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uniq_user_nutrition_scan (user_id, client_scan_id),
        KEY idx_nutrition_records_user_id (user_id),
        KEY idx_nutrition_records_created_at (created_at),
        CONSTRAINT fk_nutrition_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`
    )
  }
  await nutritionSchemaReady
}

async function requireAuth(request, _response, next) {
  try {
    request.authUser = await authenticateToken(
      extractBearerToken(request.get("authorization"))
    )
    next()
  } catch (error) {
    next(error)
  }
}

async function optionalAuth(request, _response, next) {
  const token = extractBearerToken(request.get("authorization"))
  if (!token) {
    next()
    return
  }

  try {
    request.authUser = await authenticateToken(token)
  } catch {
    request.authUser = null
  }
  next()
}

function reportCalendarDeleteDebugEvent(
  hypothesisId,
  location,
  msg,
  data = {}
) {
  if (!DEBUG_SERVER_URL) return
  void fetch(DEBUG_SERVER_URL, {
    body: JSON.stringify({
      data,
      hypothesisId,
      level: "info",
      location,
      msg: `[DEBUG] ${msg}`,
      runId: "pre-fix",
      sessionId: DEBUG_SESSION_ID,
      source: "server",
      ts: new Date().toISOString(),
    }),
    headers: {
      "content-type": "application/json",
    },
    method: "POST",
  }).catch(() => undefined)
}

const loginRoute = asyncRoute(async (request, response) => {
  response.json(await loginUser(request.body, requestAuthContext(request)))
})

const registerRoute = asyncRoute(async (request, response) => {
  response
    .status(201)
    .json(await registerUser(request.body, requestAuthContext(request)))
})

app.get(
  "/api/health",
  asyncRoute(async (_request, response) => {
    const [version] = await query(
      `SELECT VERSION() AS version, DATABASE() AS database_name,
              @@character_set_database AS charset_database,
              @@collation_database AS collation_database,
              @@character_set_connection AS charset_connection`
    )
    response.json({ ok: true, version })
  })
)

app.post("/api/auth/login", loginRoute)

app.post("/api/auth/register", registerRoute)

app.post("/api/login", loginRoute)

app.post("/api/register", registerRoute)

app.get(
  "/api/calendar/calendars",
  asyncRoute(async (_request, response) => {
    response.json(await listCalendars())
  })
)

app.post(
  "/api/calendar/calendars",
  asyncRoute(async (request, response) => {
    response.status(201).json(await createCalendar(request.body))
  })
)

app.patch(
  "/api/calendar/calendars/:id",
  asyncRoute(async (request, response) => {
    response.json(await updateCalendar(request.params.id, request.body))
  })
)

app.delete(
  "/api/calendar/calendars/:id",
  asyncRoute(async (request, response) => {
    await deleteCalendar(request.params.id)
    response.status(204).end()
  })
)

app.get(
  "/api/calendar/events",
  optionalAuth,
  asyncRoute(async (request, response) => {
    const calendarIds = request.query.calendarIds
      ? String(request.query.calendarIds).split(",").filter(Boolean)
      : undefined
    response.json(
      await listEvents(
        {
          end: String(request.query.end),
          start: String(request.query.start),
        },
        { calendarIds },
        { userId: request.authUser?.id }
      )
    )
  })
)

app.post(
  "/api/calendar/events",
  asyncRoute(async (request, response) => {
    response.status(201).json(await createEvent(request.body))
  })
)

app.patch(
  "/api/calendar/events/:id",
  asyncRoute(async (request, response) => {
    response.json(await updateEvent(request.params.id, request.body))
  })
)

app.delete(
  "/api/calendar/events/:id",
  asyncRoute(async (request, response) => {
    // #region debug-point C:calendar-delete-route-enter
    reportCalendarDeleteDebugEvent(
      "C",
      "api-server.mjs:delete-calendar-event:enter",
      "Calendar delete route received request",
      {
        id: request.params.id,
        ip: request.ip ?? request.socket?.remoteAddress ?? null,
        userAgent: request.get("user-agent") ?? null,
      }
    )
    // #endregion
    await deleteEvent(request.params.id)
    // #region debug-point C:calendar-delete-route-success
    reportCalendarDeleteDebugEvent(
      "C",
      "api-server.mjs:delete-calendar-event:success",
      "Calendar delete route completed request",
      {
        id: request.params.id,
        status: 204,
      }
    )
    // #endregion
    response.status(204).end()
  })
)

app.get(
  "/api/calendar/search",
  optionalAuth,
  asyncRoute(async (request, response) => {
    response.json(
      await searchEvents(String(request.query.q ?? ""), {
        userId: request.authUser?.id,
      })
    )
  })
)

app.get(
  "/api/calendar/export",
  asyncRoute(async (request, response) => {
    const format = String(request.query.format ?? "json")
    response.type(format === "ics" ? "text/calendar" : "application/json")
    response.send(await exportCalendar(format))
  })
)

app.post(
  "/api/calendar/import",
  asyncRoute(async (request, response) => {
    response.json(await importCalendar(request.body))
  })
)

app.get(
  "/api/motion/sessions",
  asyncRoute(async (_request, response) => {
    response.json(await listTrainingSessions())
  })
)

app.post(
  "/api/motion/sessions",
  asyncRoute(async (request, response) => {
    response.status(201).json(await createTrainingSession(request.body))
  })
)

app.post(
  "/api/training-sessions/sync",
  requireAuth,
  asyncRoute(async (request, response) => {
    response.json(
      await syncUserTrainingSessions(
        request.body?.sessions,
        request.authUser.id
      )
    )
  })
)

app.get(
  "/api/training-stats/summary",
  requireAuth,
  asyncRoute(async (request, response) => {
    response.json(await getUserTrainingSummary(request.authUser.id))
  })
)

app.post(
  "/api/training-plans/recommend",
  requireAuth,
  asyncRoute(async (request, response) => {
    response.json({
      plan: await recommendUserTrainingPlan(request.authUser.id, request.body ?? {}),
    })
  })
)

app.post(
  "/api/training-plans/adopt",
  requireAuth,
  asyncRoute(async (request, response) => {
    const plan = request.body?.plan
    if (!plan || typeof plan !== "object") {
      response.status(400).json({ error: "请提供有效的训练计划。" })
      return
    }
    response.status(201).json({
      plan: await adoptUserTrainingPlan(plan, request.authUser.id),
    })
  })
)

app.get(
  "/api/training-plans/current",
  requireAuth,
  asyncRoute(async (request, response) => {
    const plan = await getCurrentUserTrainingPlan(request.authUser.id)
    response.json({ plan })
  })
)

app.get(
  "/api/training-plans/activity",
  optionalAuth,
  asyncRoute(async (request, response) => {
    response.json(await getUserTrainingActivity(request.authUser?.id))
  })
)

app.post(
  "/api/nutrition-records",
  requireAuth,
  asyncRoute(async (request, response) => {
    const record = normalizeNutritionRecordPayload(request.body)
    if (!record) {
      response.status(400).json({
        error: "请提供有效的营养扫描记录。",
      })
      return
    }

    await ensureNutritionSchema()
    const result = await query(
      `INSERT INTO user_nutrition_records (
          user_id,
          client_scan_id,
          image_uri,
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
          item_summary
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          id = LAST_INSERT_ID(id),
          image_uri = VALUES(image_uri),
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
          item_summary = VALUES(item_summary)`,
      [
        request.authUser.id,
        record.scanId,
        record.imageUri,
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
      ]
    )

    response.status(201).json({
      record: {
        id: Number(result.insertId),
        scanId: record.scanId,
      },
    })
  })
)

app.get(
  "/api/desktop/state",
  asyncRoute(async (_request, response) => {
    response.json(await listDesktopState())
  })
)

app.post(
  "/api/ai/chat",
  asyncRoute(async (request, response) => {
    const token = process.env.DEEPSEEK_API_KEY
    if (!token) {
      throw new Error("请在 .env.local 中配置真实的 DEEPSEEK_API_KEY")
    }

    const deepseekResponse = await fetch("https://api.deepseek.com/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        model: "deepseek-chat",
        messages: [
          {
            role: "system",
            content: "You are a helpful assistant for neurafitOS, an advanced health and fitness operating system. You should introduce yourself as the neurafitOS Assistant when asked. Keep your answers concise, helpful, and naturally conversational."
          },
          ...request.body.messages
        ],
        stream: true
      })
    })

    if (!deepseekResponse.ok) {
      const error = await deepseekResponse.json().catch(() => ({}))
      return response.status(deepseekResponse.status).json(error)
    }

    response.setHeader("Content-Type", "text/event-stream")
    response.setHeader("Cache-Control", "no-cache")
    response.setHeader("Connection", "keep-alive")

    if (deepseekResponse.body) {
      for await (const chunk of deepseekResponse.body) {
        response.write(chunk)
      }
      response.end()
    } else {
      response.end()
    }
  })
)

app.get(
  "/api/music/netease/featured",
  asyncRoute(async (_request, response) => {
    response.json(await getFeaturedNeteaseTracks())
  })
)

app.get(
  "/api/music/netease/search",
  asyncRoute(async (request, response) => {
    response.json(
      await searchNeteaseTracks(
        String(request.query.q ?? ""),
        Number(request.query.limit ?? 8)
      )
    )
  })
)

app.put(
  "/api/desktop/windows/:appKey",
  asyncRoute(async (request, response) => {
    response.json(await saveWindowState(request.params.appKey, request.body))
  })
)

app.get(
  "/api/weather/locations",
  asyncRoute(async (_request, response) => {
    response.json(await listWeatherLocations())
  })
)

app.post(
  "/api/weather/locations",
  asyncRoute(async (request, response) => {
    response.status(201).json(await createWeatherLocation(request.body))
  })
)

app.get(
  "/api/weather/locations/:id/snapshots",
  asyncRoute(async (request, response) => {
    response.json(await listWeatherSnapshots(request.params.id))
  })
)

app.post(
  "/api/weather/locations/:id/snapshots",
  asyncRoute(async (request, response) => {
    response
      .status(201)
      .json(await saveWeatherSnapshot(request.params.id, request.body))
  })
)

app.get(
  "/api/weather/alerts/current",
  asyncRoute(async (request, response) => {
    const latitude = normalizeCoordinate(
      request.query.latitude,
      -90,
      90,
      "latitude"
    )
    const longitude = normalizeCoordinate(
      request.query.longitude,
      -180,
      180,
      "longitude"
    )
    const countryCode = normalizeCountryCode(request.query.countryCode)
    const locationName = normalizeOptionalString(request.query.name, 120)

    response.json(
      await fetchCurrentWeatherAlert({
        countryCode,
        latitude,
        locationName,
        longitude,
      })
    )
  })
)

app.use((error, _request, response, _next) => {
  console.error(error)
  const statusCode =
    Number.isInteger(error?.statusCode) && error.statusCode >= 400
      ? error.statusCode
      : 500

  const message =
    error instanceof Error ? error.message : "Unexpected server error"

  response.status(statusCode).json({
    error: message,
    message,
  })
})

app.listen(PORT, () => {
  console.log(`API server listening on http://localhost:${PORT}`)
})

import { query } from "../db/mysql.mjs"

const PROFILE_ID = 1

export async function listWeatherLocations() {
  const rows = await query(
    `SELECT id, name, latitude, longitude, is_default
     FROM weather_locations
     WHERE profile_id = ? AND deleted_at IS NULL
     ORDER BY is_default DESC, name`,
    [PROFILE_ID]
  )

  return rows.map((row) => ({
    id: String(row.id),
    isDefault: Boolean(row.is_default),
    latitude: row.latitude == null ? null : Number(row.latitude),
    longitude: row.longitude == null ? null : Number(row.longitude),
    name: row.name,
  }))
}

export async function createWeatherLocation(input) {
  const result = await query(
    `INSERT INTO weather_locations
      (profile_id, name, latitude, longitude, is_default)
     VALUES (?, ?, ?, ?, ?)`,
    [
      PROFILE_ID,
      input.name,
      input.latitude ?? null,
      input.longitude ?? null,
      input.isDefault ? 1 : 0,
    ]
  )

  return { id: String(result.insertId), ...input }
}

export async function saveWeatherSnapshot(locationId, input) {
  const result = await query(
    `INSERT INTO weather_snapshots
      (location_id, forecast_at, temperature_c, humidity, wind_speed_kmh, condition_text, payload)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    [
      locationId,
      input.forecastAt ?? new Date(),
      input.temperatureC ?? null,
      input.humidity ?? null,
      input.windSpeedKmh ?? null,
      input.conditionText ?? "",
      JSON.stringify(input.payload ?? {}),
    ]
  )

  return { id: String(result.insertId), locationId, ...input }
}

export async function listWeatherSnapshots(locationId) {
  const rows = await query(
    `SELECT id, forecast_at, temperature_c, humidity, wind_speed_kmh, condition_text, payload
     FROM weather_snapshots
     WHERE location_id = ?
     ORDER BY forecast_at DESC
     LIMIT 40`,
    [locationId]
  )

  return rows.map((row) => ({
    conditionText: row.condition_text,
    forecastAt: row.forecast_at,
    humidity: row.humidity,
    id: String(row.id),
    payload: row.payload,
    temperatureC: row.temperature_c == null ? null : Number(row.temperature_c),
    windSpeedKmh: row.wind_speed_kmh == null ? null : Number(row.wind_speed_kmh),
  }))
}

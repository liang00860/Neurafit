import { query, withTransaction } from "../db/mysql.mjs"
import {
  listUserTrainingPlanOccurrences,
  searchUserTrainingPlanOccurrences,
  TRAINING_PLAN_CALENDAR,
} from "./user-training-repository.mjs"

const PROFILE_ID = 1

function toJson(value, fallback) {
  if (value == null) return fallback
  if (typeof value === "string") {
    try {
      return JSON.parse(value)
    } catch {
      return fallback
    }
  }
  return value
}

function toMysqlDateTime(value) {
  if (!value) return null
  return String(value).replace("T", " ").slice(0, 19)
}

function toClientDateTime(value) {
  if (!value) return ""
  const date = value instanceof Date ? value : new Date(value)
  const pad = (input) => String(input).padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function mapCalendar(row) {
  return {
    checked: Boolean(row.visible),
    color: row.color,
    group: row.group_name,
    id: row.client_id,
    ignoreAlerts: Boolean(row.ignore_alerts),
    name: row.name,
    shared: Boolean(row.shared),
    subscribed: Boolean(row.subscribed),
    visible: Boolean(row.visible),
  }
}

function mapEvent(row, invitees = [], attachments = []) {
  return {
    alert: row.alert,
    allDay: Boolean(row.all_day),
    attachments,
    availability: row.availability,
    calendarId: row.calendar_client_id,
    color: row.color ?? undefined,
    end: toClientDateTime(row.end_at),
    id: row.client_id,
    invitees,
    isPrivate: Boolean(row.is_private),
    location: row.location,
    meetingLink: row.meeting_link,
    notes: row.notes,
    reminder: Boolean(row.reminder),
    repeat: toJson(row.repeat_rule, {
      endMode: "never",
      frequency: "none",
      interval: 1,
    }),
    secondAlert: row.second_alert,
    start: toClientDateTime(row.start_at),
    timeZone: row.time_zone,
    title: row.title,
    travelTime: row.travel_time,
    url: row.url,
  }
}

function makeClientId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function addMonths(date, months) {
  const next = new Date(date)
  next.setMonth(next.getMonth() + months)
  return next
}

function addYears(date, years) {
  const next = new Date(date)
  next.setFullYear(next.getFullYear() + years)
  return next
}

function endOfDay(date) {
  const next = new Date(date)
  next.setHours(23, 59, 59, 999)
  return next
}

function formatDateKey(value) {
  const date = value instanceof Date ? value : new Date(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function nextRepeatDate(date, repeat) {
  const interval = Math.max(1, repeat.interval || 1)

  if (repeat.frequency === "daily" || repeat.frequency === "custom") {
    return addDays(date, interval)
  }

  if (repeat.frequency === "weekly") {
    return addDays(date, interval * 7)
  }

  if (repeat.frequency === "monthly") {
    return addMonths(date, interval)
  }

  return addYears(date, interval)
}

function toOccurrence(event, occurrenceStart, occurrenceEnd) {
  const occurrenceDateKey = formatDateKey(occurrenceStart)

  return {
    ...event,
    occurrenceDateKey,
    occurrenceEnd,
    occurrenceId: `${event.id}:${occurrenceDateKey}`,
    occurrenceStart,
    sourceEventId: event.id,
  }
}

function expandOccurrences(events, range) {
  const rangeStart = new Date(range.start)
  const rangeEnd = new Date(range.end)
  const occurrences = []

  for (const event of events) {
    if (event.repeat.frequency === "none") {
      const start = new Date(event.start)
      if (start >= rangeStart && start <= rangeEnd) {
        occurrences.push(toOccurrence(event, event.start, event.end))
      }
      continue
    }

    const baseStart = new Date(event.start)
    const baseEnd = new Date(event.end)
    const duration = baseEnd.getTime() - baseStart.getTime()
    let cursor = new Date(baseStart)
    let emitted = 0
    let guard = 0

    while (cursor <= rangeEnd && guard < 1000) {
      guard += 1
      const excluded = event.repeat.excludedDates?.includes(formatDateKey(cursor))
      const withinEnd =
        event.repeat.endMode !== "onDate" ||
        !event.repeat.endDate ||
        cursor <= endOfDay(new Date(event.repeat.endDate))
      const withinCount =
        event.repeat.endMode !== "after" ||
        !event.repeat.count ||
        emitted < event.repeat.count

      if (
        !excluded &&
        withinEnd &&
        withinCount &&
        cursor >= rangeStart &&
        cursor <= rangeEnd
      ) {
        occurrences.push(
          toOccurrence(
            event,
            toClientDateTime(cursor),
            toClientDateTime(new Date(cursor.getTime() + duration))
          )
        )
      }

      emitted += 1
      cursor = nextRepeatDate(cursor, event.repeat)
    }
  }

  return occurrences.sort(
    (left, right) =>
      new Date(left.occurrenceStart).getTime() -
      new Date(right.occurrenceStart).getTime()
  )
}

async function getCalendarListDbId(connection, clientId) {
  const [rows] = await connection.execute(
    "SELECT id FROM calendar_lists WHERE profile_id = ? AND client_id = ? AND deleted_at IS NULL",
    [PROFILE_ID, clientId]
  )

  if (!rows[0]) {
    throw new Error(`Calendar not found: ${clientId}`)
  }

  return rows[0].id
}

async function hydrateEvents(rows) {
  if (rows.length === 0) return []

  const ids = rows.map((row) => row.id)
  const inviteeRows = await query(
    `SELECT event_id, email, name, status
     FROM calendar_event_invitees
     WHERE event_id IN (${ids.map(() => "?").join(",")})`,
    ids
  )
  const attachmentRows = await query(
    `SELECT event_id, name
     FROM calendar_event_attachments
     WHERE event_id IN (${ids.map(() => "?").join(",")})`,
    ids
  )

  const inviteesByEvent = new Map()
  const attachmentsByEvent = new Map()
  for (const invitee of inviteeRows) {
    inviteesByEvent.set(invitee.event_id, [
      ...(inviteesByEvent.get(invitee.event_id) ?? []),
      {
        email: invitee.email,
        name: invitee.name,
        status: invitee.status,
      },
    ])
  }
  for (const attachment of attachmentRows) {
    attachmentsByEvent.set(attachment.event_id, [
      ...(attachmentsByEvent.get(attachment.event_id) ?? []),
      attachment.name,
    ])
  }

  return rows.map((row) =>
    mapEvent(
      row,
      inviteesByEvent.get(row.id) ?? [],
      attachmentsByEvent.get(row.id) ?? []
    )
  )
}

async function writeEventRelations(connection, eventId, input) {
  await connection.execute("DELETE FROM calendar_event_invitees WHERE event_id = ?", [
    eventId,
  ])
  await connection.execute(
    "DELETE FROM calendar_event_attachments WHERE event_id = ?",
    [eventId]
  )

  for (const invitee of input.invitees ?? []) {
    await connection.execute(
      `INSERT INTO calendar_event_invitees (event_id, email, name, status)
       VALUES (?, ?, ?, ?)`,
      [eventId, invitee.email, invitee.name, invitee.status]
    )
  }

  for (const attachment of input.attachments ?? []) {
    await connection.execute(
      `INSERT INTO calendar_event_attachments (event_id, name)
       VALUES (?, ?)`,
      [eventId, attachment]
    )
  }
}

export async function listCalendars() {
  const rows = await query(
    `SELECT *
     FROM calendar_lists
     WHERE profile_id = ? AND deleted_at IS NULL
     ORDER BY group_name, name`,
    [PROFILE_ID]
  )
  const calendars = rows.map(mapCalendar)
  if (!calendars.some((calendar) => calendar.id === TRAINING_PLAN_CALENDAR.id)) {
    calendars.push(TRAINING_PLAN_CALENDAR)
  }
  return calendars
}

export async function createCalendar(input) {
  const clientId = input.id || makeClientId("calendar")
  await query(
    `INSERT INTO calendar_lists
      (profile_id, client_id, group_name, name, color, visible, shared, subscribed, ignore_alerts)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      PROFILE_ID,
      clientId,
      input.group,
      input.name,
      input.color,
      input.visible ? 1 : 0,
      input.shared ? 1 : 0,
      input.subscribed ? 1 : 0,
      input.ignoreAlerts ? 1 : 0,
    ]
  )
  return { ...input, id: clientId }
}

export async function updateCalendar(id, patch) {
  const current = (await listCalendars()).find((calendar) => calendar.id === id)
  if (!current) throw new Error(`Calendar not found: ${id}`)
  const next = { ...current, ...patch, id }
  await query(
    `UPDATE calendar_lists
     SET group_name = ?, name = ?, color = ?, visible = ?, shared = ?, subscribed = ?, ignore_alerts = ?
     WHERE profile_id = ? AND client_id = ? AND deleted_at IS NULL`,
    [
      next.group,
      next.name,
      next.color,
      next.visible ? 1 : 0,
      next.shared ? 1 : 0,
      next.subscribed ? 1 : 0,
      next.ignoreAlerts ? 1 : 0,
      PROFILE_ID,
      id,
    ]
  )
  return next
}

export async function deleteCalendar(id) {
  await query(
    "UPDATE calendar_lists SET deleted_at = CURRENT_TIMESTAMP(3) WHERE profile_id = ? AND client_id = ?",
    [PROFILE_ID, id]
  )
}

export async function createEvent(input) {
  return withTransaction(async (connection) => {
    const calendarListId = await getCalendarListDbId(connection, input.calendarId)
    const clientId = input.id || makeClientId("event")
    const [result] = await connection.execute(
      `INSERT INTO calendar_events
        (profile_id, calendar_list_id, client_id, title, start_at, end_at, all_day, color, location,
         meeting_link, notes, reminder, repeat_rule, alert, second_alert, travel_time, time_zone,
         availability, is_private, url)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        PROFILE_ID,
        calendarListId,
        clientId,
        input.title,
        toMysqlDateTime(input.start),
        toMysqlDateTime(input.end),
        input.allDay ? 1 : 0,
        input.color ?? null,
        input.location,
        input.meetingLink,
        input.notes,
        input.reminder ? 1 : 0,
        JSON.stringify(input.repeat),
        input.alert,
        input.secondAlert,
        input.travelTime,
        input.timeZone,
        input.availability,
        input.isPrivate ? 1 : 0,
        input.url,
      ]
    )
    await writeEventRelations(connection, result.insertId, input)
    return { ...input, id: clientId }
  })
}

export async function updateEvent(id, patch) {
  const events = await listEvents({
    end: "2028-12-31T23:59:59",
    start: "2024-01-01T00:00:00",
  })
  const current = events.find((event) => event.id === id)
  if (!current) throw new Error(`Event not found: ${id}`)
  const next = {
    ...current,
    ...patch,
    id,
    repeat: patch.repeat ? { ...current.repeat, ...patch.repeat } : current.repeat,
  }

  await withTransaction(async (connection) => {
    const calendarListId = await getCalendarListDbId(connection, next.calendarId)
    const [rows] = await connection.execute(
      "SELECT id FROM calendar_events WHERE profile_id = ? AND client_id = ? AND deleted_at IS NULL",
      [PROFILE_ID, id]
    )
    if (!rows[0]) throw new Error(`Event not found: ${id}`)

    await connection.execute(
      `UPDATE calendar_events
       SET calendar_list_id = ?, title = ?, start_at = ?, end_at = ?, all_day = ?, color = ?,
           location = ?, meeting_link = ?, notes = ?, reminder = ?, repeat_rule = ?,
           alert = ?, second_alert = ?, travel_time = ?, time_zone = ?, availability = ?,
           is_private = ?, url = ?
       WHERE id = ?`,
      [
        calendarListId,
        next.title,
        toMysqlDateTime(next.start),
        toMysqlDateTime(next.end),
        next.allDay ? 1 : 0,
        next.color ?? null,
        next.location,
        next.meetingLink,
        next.notes,
        next.reminder ? 1 : 0,
        JSON.stringify(next.repeat),
        next.alert,
        next.secondAlert,
        next.travelTime,
        next.timeZone,
        next.availability,
        next.isPrivate ? 1 : 0,
        next.url,
        rows[0].id,
      ]
    )
    await writeEventRelations(connection, rows[0].id, next)
  })

  return next
}

export async function deleteEvent(id) {
  await query(
    "UPDATE calendar_events SET deleted_at = CURRENT_TIMESTAMP(3) WHERE profile_id = ? AND client_id = ?",
    [PROFILE_ID, id]
  )
}

export async function listEvents(range, filters = {}, context = {}) {
  const params = [PROFILE_ID]
  let calendarFilter = ""
  if (filters.calendarIds?.length) {
    calendarFilter = `AND cl.client_id IN (${filters.calendarIds.map(() => "?").join(",")})`
    params.push(...filters.calendarIds)
  }

  const rows = await query(
    `SELECT ce.*, cl.client_id AS calendar_client_id
     FROM calendar_events ce
     JOIN calendar_lists cl ON cl.id = ce.calendar_list_id
     WHERE ce.profile_id = ?
       AND ce.deleted_at IS NULL
       AND cl.deleted_at IS NULL
       ${calendarFilter}
     ORDER BY ce.start_at`,
    params
  )

  const calendarOccurrences = expandOccurrences(await hydrateEvents(rows), range)
  const planOccurrences = await listUserTrainingPlanOccurrences(
    context.userId,
    range,
    filters
  )

  return [...calendarOccurrences, ...planOccurrences].sort(
    (left, right) =>
      new Date(left.occurrenceStart).getTime() -
      new Date(right.occurrenceStart).getTime()
  )
}

export async function searchEvents(queryText, context = {}) {
  const normalized = `%${String(queryText ?? "").trim()}%`
  const rows = await query(
    `SELECT ce.*, cl.client_id AS calendar_client_id
     FROM calendar_events ce
     JOIN calendar_lists cl ON cl.id = ce.calendar_list_id
     WHERE ce.profile_id = ?
       AND ce.deleted_at IS NULL
       AND cl.deleted_at IS NULL
       AND (? = '%%' OR ce.title LIKE ? OR ce.location LIKE ? OR ce.notes LIKE ? OR cl.name LIKE ?)
     ORDER BY ce.start_at
     LIMIT 30`,
    [PROFILE_ID, normalized, normalized, normalized, normalized, normalized]
  )
  const calendarOccurrences = expandOccurrences(await hydrateEvents(rows), {
    end: "2027-12-31T23:59:59",
    start: "2025-01-01T00:00:00",
  })
  const planOccurrences = await searchUserTrainingPlanOccurrences(
    context.userId,
    queryText
  )

  return [...calendarOccurrences, ...planOccurrences]
    .sort(
      (left, right) =>
        new Date(left.occurrenceStart).getTime() -
        new Date(right.occurrenceStart).getTime()
    )
    .slice(0, 30)
}

export async function exportCalendar(format) {
  const calendars = await listCalendars()
  const events = await hydrateEvents(
    await query(
      `SELECT ce.*, cl.client_id AS calendar_client_id
       FROM calendar_events ce
       JOIN calendar_lists cl ON cl.id = ce.calendar_list_id
       WHERE ce.profile_id = ? AND ce.deleted_at IS NULL AND cl.deleted_at IS NULL
       ORDER BY ce.start_at`,
      [PROFILE_ID]
    )
  )

  if (format === "json") {
    return JSON.stringify({ calendars, events }, null, 2)
  }

  return events
    .map((event) =>
      [
        "BEGIN:VEVENT",
        `UID:${event.id}`,
        `SUMMARY:${event.title}`,
        `DTSTART:${event.start.replaceAll(/[-:]/g, "").replace("T", "T")}`,
        `DTEND:${event.end.replaceAll(/[-:]/g, "").replace("T", "T")}`,
        `LOCATION:${event.location}`,
        "END:VEVENT",
      ].join("\n")
    )
    .join("\n")
}

export async function importCalendar(state) {
  const calendars = Array.isArray(state.calendars) ? state.calendars : []
  const events = Array.isArray(state.events) ? state.events : []

  await withTransaction(async (connection) => {
    for (const calendar of calendars) {
      await connection.execute(
        `INSERT INTO calendar_lists
          (profile_id, client_id, group_name, name, color, visible, shared, subscribed, ignore_alerts)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
          group_name = VALUES(group_name), name = VALUES(name), color = VALUES(color),
          visible = VALUES(visible), shared = VALUES(shared), subscribed = VALUES(subscribed),
          ignore_alerts = VALUES(ignore_alerts), deleted_at = NULL`,
        [
          PROFILE_ID,
          calendar.id,
          calendar.group,
          calendar.name,
          calendar.color,
          calendar.visible ? 1 : 0,
          calendar.shared ? 1 : 0,
          calendar.subscribed ? 1 : 0,
          calendar.ignoreAlerts ? 1 : 0,
        ]
      )
    }
  })

  for (const event of events) {
    const existing = (
      await query(
        "SELECT client_id FROM calendar_events WHERE profile_id = ? AND client_id = ?",
        [PROFILE_ID, event.id]
      )
    )[0]
    if (existing) {
      await updateEvent(event.id, event)
    } else {
      await createEvent(event)
    }
  }

  return { calendars: await listCalendars(), events: await searchEvents("") }
}

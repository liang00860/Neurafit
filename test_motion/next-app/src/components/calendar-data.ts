import { RemoteCalendarAdapter } from "@/components/calendar-remote-api"

export type CalendarView = "day" | "week" | "month" | "year"

export type RepeatFrequency =
  | "none"
  | "daily"
  | "weekly"
  | "monthly"
  | "yearly"
  | "custom"

export type RepeatEndMode = "never" | "onDate" | "after"

export type RecurrenceScope = "this" | "future" | "series"

export type Availability = "busy" | "free" | "tentative"

export type RsvpStatus = "accepted" | "declined" | "tentative" | "pending"

export type ExportFormat = "json" | "ics"

export type CalendarList = {
  color: string
  group: string
  id: string
  ignoreAlerts?: boolean
  name: string
  shared?: boolean
  subscribed?: boolean
  visible: boolean
}

export type Invitee = {
  email: string
  name: string
  status: RsvpStatus
}

export type RecurrenceRule = {
  count?: number
  endDate?: string
  endMode: RepeatEndMode
  excludedDates?: string[]
  frequency: RepeatFrequency
  interval: number
  weekdays?: number[]
}

export type CalendarEvent = {
  alert: string
  allDay: boolean
  attachments: string[]
  availability: Availability
  calendarId: string
  color?: string
  end: string
  id: string
  invitees: Invitee[]
  isPrivate: boolean
  location: string
  meetingLink: string
  notes: string
  reminder?: boolean
  repeat: RecurrenceRule
  secondAlert: string
  start: string
  timeZone: string
  title: string
  travelTime: string
  url: string
}

export type CalendarOccurrence = CalendarEvent & {
  occurrenceDateKey: string
  occurrenceEnd: string
  occurrenceId: string
  occurrenceStart: string
  sourceEventId: string
}

export type CalendarEventInput = Omit<CalendarEvent, "id">

export type CalendarListInput = Omit<CalendarList, "id">

export type EventRange = {
  end: Date
  start: Date
}

const TRAINING_PLAN_CALENDAR_ID = "ai-training-plan"
const TRAINING_PLAN_CALENDAR: CalendarList = {
  color: "#54d8c7",
  group: "Health",
  id: TRAINING_PLAN_CALENDAR_ID,
  name: "AI Training Plan",
  visible: true,
}

function ensureTrainingPlanCalendar(calendars: CalendarList[]) {
  const next = [...calendars]
  const index = next.findIndex((calendar) => calendar.id === TRAINING_PLAN_CALENDAR_ID)

  if (index === -1) {
    next.push(TRAINING_PLAN_CALENDAR)
    return next
  }

  next[index] = {
    ...next[index],
    color: next[index].color || TRAINING_PLAN_CALENDAR.color,
    group: next[index].group || TRAINING_PLAN_CALENDAR.group,
    name: next[index].name || TRAINING_PLAN_CALENDAR.name,
  }
  return next
}

export interface CalendarApi {
  createCalendar(input: CalendarListInput): Promise<CalendarList>
  createEvent(input: CalendarEventInput): Promise<CalendarEvent>
  deleteCalendar(id: string): Promise<void>
  deleteEvent(id: string, recurrenceScope?: RecurrenceScope): Promise<void>
  exportCalendar(format: ExportFormat): Promise<string>
  importCalendar(file: File): Promise<{
    calendars: CalendarList[]
    events: CalendarEvent[]
  }>
  listCalendars(): Promise<CalendarList[]>
  listEvents(
    range: EventRange,
    filters?: { calendarIds?: string[] }
  ): Promise<CalendarOccurrence[]>
  searchEvents(query: string): Promise<CalendarOccurrence[]>
  updateCalendar(
    id: string,
    patch: Partial<CalendarList>
  ): Promise<CalendarList>
  updateEvent(
    id: string,
    patch: Partial<CalendarEvent>,
    recurrenceScope?: RecurrenceScope
  ): Promise<CalendarEvent>
}

const STORAGE_KEY = "macos-calendar-state-v1"

export const WEEKDAY_LABELS = ["一", "二", "三", "四", "五", "六", "日"]

export const MONTH_LABELS = [
  "1月",
  "2月",
  "3月",
  "4月",
  "5月",
  "6月",
  "7月",
  "8月",
  "9月",
  "10月",
  "11月",
  "12月",
]

const seedCalendars: CalendarList[] = [
  {
    color: "#ff3b30",
    group: "iCloud",
    id: "home",
    name: "Home",
    visible: true,
  },
  {
    color: "#007aff",
    group: "iCloud",
    id: "personal",
    name: "Personal",
    visible: true,
  },
  {
    color: "#34c759",
    group: "iCloud",
    id: "family",
    name: "Family",
    shared: true,
    visible: true,
  },
  {
    color: "#ff9500",
    group: "Work",
    id: "product",
    name: "Product Team",
    shared: true,
    visible: true,
  },
  {
    color: "#af52de",
    group: "Work",
    id: "launches",
    name: "Launches",
    visible: true,
  },
  {
    color: "#5ac8fa",
    group: "Work",
    id: "focus",
    name: "Focus Blocks",
    visible: false,
  },
  {
    color: "#ffcc00",
    group: "Other",
    id: "holidays",
    name: "US Holidays",
    subscribed: true,
    visible: true,
  },
  {
    color: "#ff2d55",
    group: "Other",
    id: "birthdays",
    name: "Birthdays",
    visible: true,
  },
]

const seedEvents: CalendarEvent[] = [
  createSeedEvent({
    calendarId: "personal",
    id: "standup",
    start: "2026-06-02T09:30:00",
    title: "Daily standup",
  }),
  createSeedEvent({
    calendarId: "launches",
    id: "design-review",
    start: "2026-06-04T11:00:00",
    title: "Design review",
  }),
  createSeedEvent({
    calendarId: "family",
    id: "family-dinner",
    start: "2026-06-06T19:00:00",
    title: "Family dinner",
  }),
  createSeedEvent({
    calendarId: "product",
    id: "sprint-planning",
    repeat: {
      endMode: "never",
      frequency: "weekly",
      interval: 1,
      weekdays: [2],
    },
    start: "2026-06-09T10:00:00",
    title: "Sprint planning",
  }),
  createSeedEvent({
    calendarId: "personal",
    id: "supplier-reminder",
    reminder: true,
    start: "2026-06-09T14:00:00",
    title: "Call supplier",
  }),
  createSeedEvent({
    calendarId: "product",
    id: "product-sync",
    start: "2026-06-12T14:00:00",
    title: "Product sync",
  }),
  createSeedEvent({
    calendarId: "personal",
    id: "renew-subscription",
    reminder: true,
    start: "2026-06-15T09:00:00",
    title: "Renew subscription",
  }),
  createSeedEvent({
    calendarId: "personal",
    id: "quarterly-planning",
    invitees: [
      { email: "maya@example.com", name: "Maya", status: "accepted" },
      { email: "ben@example.com", name: "Ben", status: "tentative" },
      { email: "ari@example.com", name: "Ari", status: "declined" },
    ],
    location: "1 Infinite Loop, Cupertino",
    meetingLink: "https://meet.example.com/quarterly",
    notes: "Review roadmap, launch risks, and staffing needs.",
    repeat: {
      endMode: "never",
      frequency: "weekly",
      interval: 1,
      weekdays: [4],
    },
    start: "2026-06-18T09:00:00",
    title: "Quarterly planning",
    travelTime: "25 minutes",
    url: "https://roadmap.example.com",
  }),
  createSeedEvent({
    calendarId: "family",
    id: "facetime-maya",
    meetingLink: "facetime://maya",
    start: "2026-06-18T13:00:00",
    title: "FaceTime with Maya",
  }),
  createSeedEvent({
    calendarId: "home",
    id: "flight-sfo",
    location: "SFO",
    start: "2026-06-18T17:00:00",
    title: "Flight to SFO",
    travelTime: "45 minutes",
  }),
  createSeedEvent({
    allDay: true,
    calendarId: "birthdays",
    id: "dad-birthday",
    start: "2026-06-21T00:00:00",
    title: "Dad birthday",
  }),
  createSeedEvent({
    calendarId: "home",
    id: "airport-alert",
    start: "2026-06-24T15:40:00",
    title: "Time to leave: Airport",
    travelTime: "35 minutes",
  }),
  createSeedEvent({
    calendarId: "focus",
    id: "export-report",
    reminder: true,
    start: "2026-06-27T09:00:00",
    title: "Export monthly report",
  }),
]

function createSeedEvent(
  input: Partial<CalendarEvent> & {
    calendarId: string
    id: string
    start: string
    title: string
  }
): CalendarEvent {
  const end = addMinutes(new Date(input.start), input.allDay ? 1440 : 60)

  return {
    alert: input.alert ?? "10 minutes before",
    allDay: input.allDay ?? false,
    attachments: input.attachments ?? ["agenda.pdf"],
    availability: "busy",
    calendarId: input.calendarId,
    color: input.color,
    end: input.end ?? toLocalInputValue(end),
    id: input.id,
    invitees: input.invitees ?? [],
    isPrivate: input.isPrivate ?? false,
    location: input.location ?? "",
    meetingLink: input.meetingLink ?? "",
    notes: input.notes ?? "",
    reminder: input.reminder,
    repeat: {
      endMode: input.repeat?.endMode ?? "never",
      frequency: input.repeat?.frequency ?? "none",
      interval: input.repeat?.interval ?? 1,
      weekdays: input.repeat?.weekdays,
      count: input.repeat?.count,
      endDate: input.repeat?.endDate,
      excludedDates: input.repeat?.excludedDates ?? [],
    },
    secondAlert: "Time to leave",
    start: input.start,
    timeZone: "Pacific Time",
    title: input.title,
    travelTime: input.travelTime ?? "25 minutes",
    url: input.url ?? "",
  }
}

function cloneState(state: CalendarState): CalendarState {
  return {
    calendars: state.calendars.map((calendar) => ({ ...calendar })),
    events: state.events.map((event) => ({
      ...event,
      attachments: [...event.attachments],
      invitees: event.invitees.map((invitee) => ({ ...invitee })),
      repeat: {
        ...event.repeat,
        excludedDates: [...(event.repeat.excludedDates ?? [])],
        weekdays: event.repeat.weekdays
          ? [...event.repeat.weekdays]
          : undefined,
      },
    })),
  }
}

type CalendarState = {
  calendars: CalendarList[]
  events: CalendarEvent[]
}

class MockCalendarAdapter implements CalendarApi {
  private state: CalendarState

  constructor() {
    this.state = this.loadState()
  }

  async createCalendar(input: CalendarListInput) {
    const calendar: CalendarList = {
      ...input,
      id: createId("calendar"),
    }
    this.state.calendars = [...this.state.calendars, calendar]
    this.persist()
    return { ...calendar }
  }

  async createEvent(input: CalendarEventInput) {
    const event: CalendarEvent = {
      ...input,
      id: createId("event"),
      repeat: {
        ...input.repeat,
        excludedDates: input.repeat.excludedDates ?? [],
      },
    }
    this.state.events = [...this.state.events, event]
    this.persist()
    return cloneState({ calendars: [], events: [event] }).events[0]
  }

  async deleteCalendar(id: string) {
    this.state.calendars = this.state.calendars.filter(
      (calendar) => calendar.id !== id
    )
    this.state.events = this.state.events.filter(
      (event) => event.calendarId !== id
    )
    this.persist()
  }

  async deleteEvent(id: string) {
    this.state.events = this.state.events.filter((event) => event.id !== id)
    this.persist()
  }

  async exportCalendar(format: ExportFormat) {
    if (format === "json") {
      return JSON.stringify(this.state, null, 2)
    }

    return this.state.events
      .map((event) =>
        [
          "BEGIN:VEVENT",
          `UID:${event.id}`,
          `SUMMARY:${event.title}`,
          `DTSTART:${compactDate(event.start)}`,
          `DTEND:${compactDate(event.end)}`,
          `LOCATION:${event.location}`,
          "END:VEVENT",
        ].join("\n")
      )
      .join("\n")
  }

  async importCalendar(file: File) {
    const text = await file.text()
    const parsed = JSON.parse(text) as Partial<CalendarState>
    const calendars = Array.isArray(parsed.calendars) ? parsed.calendars : []
    const events = Array.isArray(parsed.events) ? parsed.events : []

    this.state.calendars = mergeById(this.state.calendars, calendars)
    this.state.events = mergeById(this.state.events, events)
    this.persist()

    return cloneState({
      calendars: this.state.calendars,
      events: this.state.events,
    })
  }

  async listCalendars() {
    return cloneState(this.state).calendars
  }

  async listEvents(range: EventRange, filters?: { calendarIds?: string[] }) {
    const calendarIds =
      filters?.calendarIds ??
      this.state.calendars
        .filter((calendar) => calendar.visible)
        .map((calendar) => calendar.id)

    return expandOccurrences(
      this.state.events.filter((event) =>
        calendarIds.includes(event.calendarId)
      ),
      range
    )
  }

  async searchEvents(query: string) {
    const normalized = query.trim().toLowerCase()
    const range = {
      end: new Date("2027-12-31T23:59:00"),
      start: new Date("2025-01-01T00:00:00"),
    }
    const occurrences = expandOccurrences(this.state.events, range)

    if (!normalized) {
      return occurrences.slice(0, 6)
    }

    const calendarById = new Map(
      this.state.calendars.map((calendar) => [calendar.id, calendar])
    )

    return occurrences.filter((event) => {
      const calendarName = calendarById.get(event.calendarId)?.name ?? ""
      return [event.title, event.location, calendarName, event.notes]
        .join(" ")
        .toLowerCase()
        .includes(normalized)
    })
  }

  async updateCalendar(id: string, patch: Partial<CalendarList>) {
    let updated: CalendarList | undefined
    this.state.calendars = this.state.calendars.map((calendar) => {
      if (calendar.id !== id) {
        return calendar
      }
      updated = { ...calendar, ...patch, id }
      return updated
    })
    this.persist()

    if (!updated) {
      throw new Error(`Calendar not found: ${id}`)
    }

    return { ...updated }
  }

  async updateEvent(id: string, patch: Partial<CalendarEvent>) {
    let updated: CalendarEvent | undefined
    this.state.events = this.state.events.map((event) => {
      if (event.id !== id) {
        return event
      }
      updated = {
        ...event,
        ...patch,
        id,
        repeat: patch.repeat
          ? {
              ...event.repeat,
              ...patch.repeat,
              excludedDates:
                patch.repeat.excludedDates ?? event.repeat.excludedDates,
            }
          : event.repeat,
      }
      return updated
    })
    this.persist()

    if (!updated) {
      throw new Error(`Event not found: ${id}`)
    }

    return cloneState({ calendars: [], events: [updated] }).events[0]
  }

  private loadState(): CalendarState {
    if (typeof window === "undefined") {
      return cloneState({
        calendars: ensureTrainingPlanCalendar(seedCalendars),
        events: seedEvents,
      })
    }

    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (!saved) {
      return cloneState({
        calendars: ensureTrainingPlanCalendar(seedCalendars),
        events: seedEvents,
      })
    }

    try {
      const parsed = JSON.parse(saved) as CalendarState
      return cloneState({
        calendars: ensureTrainingPlanCalendar(parsed.calendars ?? seedCalendars),
        events: parsed.events ?? seedEvents,
      })
    } catch {
      return cloneState({
        calendars: ensureTrainingPlanCalendar(seedCalendars),
        events: seedEvents,
      })
    }
  }

  private persist() {
    if (typeof window === "undefined") {
      return
    }

    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state))
  }
}

class FallbackCalendarAdapter implements CalendarApi {
  private readonly fallback: CalendarApi
  private readonly remote: CalendarApi

  constructor(remote: CalendarApi, fallback: CalendarApi) {
    this.remote = remote
    this.fallback = fallback
  }

  private async tryRemote<T>(
    operation: () => Promise<T>,
    fallback: () => Promise<T>
  ) {
    try {
      return await operation()
    } catch (error) {
      console.warn("Calendar API fallback:", error)
      return fallback()
    }
  }

  createCalendar(input: CalendarListInput) {
    return this.tryRemote(
      () => this.remote.createCalendar(input),
      () => this.fallback.createCalendar(input)
    )
  }

  createEvent(input: CalendarEventInput) {
    return this.tryRemote(
      () => this.remote.createEvent(input),
      () => this.fallback.createEvent(input)
    )
  }

  deleteCalendar(id: string) {
    return this.tryRemote(
      () => this.remote.deleteCalendar(id),
      () => this.fallback.deleteCalendar(id)
    )
  }

  deleteEvent(id: string, recurrenceScope?: RecurrenceScope) {
    return this.tryRemote(
      () => this.remote.deleteEvent(id, recurrenceScope),
      () => this.fallback.deleteEvent(id, recurrenceScope)
    )
  }

  exportCalendar(format: ExportFormat) {
    return this.tryRemote(
      () => this.remote.exportCalendar(format),
      () => this.fallback.exportCalendar(format)
    )
  }

  importCalendar(file: File) {
    return this.tryRemote(
      () => this.remote.importCalendar(file),
      () => this.fallback.importCalendar(file)
    )
  }

  listCalendars() {
    return this.tryRemote(
      async () => ensureTrainingPlanCalendar(await this.remote.listCalendars()),
      async () => ensureTrainingPlanCalendar(await this.fallback.listCalendars())
    )
  }

  listEvents(range: EventRange, filters?: { calendarIds?: string[] }) {
    return this.tryRemote(
      () => this.remote.listEvents(range, filters),
      () => this.fallback.listEvents(range, filters)
    )
  }

  searchEvents(query: string) {
    return this.tryRemote(
      () => this.remote.searchEvents(query),
      () => this.fallback.searchEvents(query)
    )
  }

  updateCalendar(id: string, patch: Partial<CalendarList>) {
    return this.tryRemote(
      () => this.remote.updateCalendar(id, patch),
      () => this.fallback.updateCalendar(id, patch)
    )
  }

  updateEvent(
    id: string,
    patch: Partial<CalendarEvent>,
    recurrenceScope?: RecurrenceScope
  ) {
    return this.tryRemote(
      () => this.remote.updateEvent(id, patch, recurrenceScope),
      () => this.fallback.updateEvent(id, patch, recurrenceScope)
    )
  }
}

export const calendarApi: CalendarApi = new FallbackCalendarAdapter(
  new RemoteCalendarAdapter(),
  new MockCalendarAdapter()
)

export function addDays(date: Date, days: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

export function addMonths(date: Date, months: number) {
  const next = new Date(date)
  next.setMonth(next.getMonth() + months)
  return next
}

export function addYears(date: Date, years: number) {
  const next = new Date(date)
  next.setFullYear(next.getFullYear() + years)
  return next
}

export function addMinutes(date: Date, minutes: number) {
  const next = new Date(date)
  next.setMinutes(next.getMinutes() + minutes)
  return next
}

export function buildDefaultEvent(
  date: Date,
  calendarId: string
): CalendarEventInput {
  const start = new Date(date)
  start.setHours(9, 0, 0, 0)
  const end = addMinutes(start, 60)

  return {
    alert: "提前 10 分钟",
    allDay: false,
    attachments: [],
    availability: "busy",
    calendarId,
    end: toLocalInputValue(end),
    invitees: [],
    isPrivate: false,
    location: "",
    meetingLink: "",
    notes: "",
    repeat: {
      endMode: "never",
      excludedDates: [],
      frequency: "none",
      interval: 1,
    },
    secondAlert: "无",
    start: toLocalInputValue(start),
    timeZone: "太平洋时间",
    title: "新建事件",
    travelTime: "无",
    url: "",
  }
}

export function buildMonthCells(date: Date) {
  const first = new Date(date.getFullYear(), date.getMonth(), 1)
  const start = startOfWeek(first)

  return Array.from({ length: 42 }, (_, index) => {
    const cellDate = addDays(start, index)
    return {
      currentMonth: cellDate.getMonth() === date.getMonth(),
      date: cellDate,
      dateKey: formatDateKey(cellDate),
      day: cellDate.getDate(),
      selected: false,
      today: isSameDay(cellDate, new Date()),
      weekend: cellDate.getDay() === 0 || cellDate.getDay() === 6,
    }
  })
}

export function eventDisplayTime(event: CalendarOccurrence) {
  if (event.allDay) {
    return ""
  }

  return formatTime(new Date(event.occurrenceStart))
}

export function formatDateKey(date: Date | string) {
  const value = typeof date === "string" ? new Date(date) : date
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, "0")
  const day = String(value.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

export function formatMonthTitle(date: Date) {
  return `${date.getFullYear()}年${date.getMonth() + 1}月`
}

export function formatShortDate(date: Date | string) {
  const value = typeof date === "string" ? new Date(date) : date
  return `${value.getMonth() + 1}月${value.getDate()}日`
}

export function formatTime(date: Date) {
  return date.toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
}

export function groupCalendars(calendars: CalendarList[]) {
  return calendars.reduce<Record<string, CalendarList[]>>(
    (groups, calendar) => {
      groups[calendar.group] = groups[calendar.group] ?? []
      groups[calendar.group].push(calendar)
      return groups
    },
    {}
  )
}

export function isSameDay(left: Date | string, right: Date | string) {
  return formatDateKey(left) === formatDateKey(right)
}

export function rangeForView(date: Date, view: CalendarView): EventRange {
  if (view === "day") {
    return {
      end: endOfDay(date),
      start: startOfDay(date),
    }
  }

  if (view === "week") {
    const start = startOfWeek(date)
    return {
      end: endOfDay(addDays(start, 6)),
      start,
    }
  }

  if (view === "year") {
    return {
      end: new Date(date.getFullYear(), 11, 31, 23, 59, 59),
      start: new Date(date.getFullYear(), 0, 1),
    }
  }

  const first = new Date(date.getFullYear(), date.getMonth(), 1)
  const cells = buildMonthCells(first)
  return {
    end: endOfDay(cells[cells.length - 1].date),
    start: startOfDay(cells[0].date),
  }
}

export function startOfDay(date: Date) {
  const next = new Date(date)
  next.setHours(0, 0, 0, 0)
  return next
}

export function startOfWeek(date: Date) {
  const next = startOfDay(date)
  const day = next.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  return addDays(next, mondayOffset)
}

export function toLocalInputValue(date: Date | string) {
  const value = typeof date === "string" ? new Date(date) : date
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, "0")
  const day = String(value.getDate()).padStart(2, "0")
  const hours = String(value.getHours()).padStart(2, "0")
  const minutes = String(value.getMinutes()).padStart(2, "0")
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function compactDate(value: string) {
  return value.replace(/[-:]/g, "").replace(".", "")
}

function createId(prefix: string) {
  return `${prefix}-${Date.now().toString(36)}-${Math.random()
    .toString(36)
    .slice(2, 8)}`
}

function endOfDay(date: Date) {
  const next = new Date(date)
  next.setHours(23, 59, 59, 999)
  return next
}

function expandOccurrences(events: CalendarEvent[], range: EventRange) {
  const occurrences: CalendarOccurrence[] = []

  for (const event of events) {
    if (event.repeat.frequency === "none") {
      const start = new Date(event.start)
      if (start >= range.start && start <= range.end) {
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

    while (cursor <= range.end && guard < 1000) {
      guard += 1
      const excluded = event.repeat.excludedDates?.includes(
        formatDateKey(cursor)
      )
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
        cursor >= range.start &&
        cursor <= range.end
      ) {
        occurrences.push(
          toOccurrence(
            event,
            toLocalInputValue(cursor),
            toLocalInputValue(new Date(cursor.getTime() + duration))
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

function mergeById<T extends { id: string }>(current: T[], incoming: T[]) {
  const merged = new Map(current.map((item) => [item.id, item]))
  for (const item of incoming) {
    merged.set(item.id, item)
  }
  return Array.from(merged.values())
}

function nextRepeatDate(date: Date, repeat: RecurrenceRule) {
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

function toOccurrence(
  event: CalendarEvent,
  occurrenceStart: string,
  occurrenceEnd: string
): CalendarOccurrence {
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

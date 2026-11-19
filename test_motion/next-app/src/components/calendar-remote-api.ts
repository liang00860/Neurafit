import type {
  CalendarApi,
  CalendarEvent,
  CalendarEventInput,
  CalendarList,
  CalendarListInput,
  CalendarOccurrence,
  EventRange,
  ExportFormat,
  RecurrenceScope,
} from "@/components/calendar-data"
import { apiRequest } from "@/lib/api-client"

export class RemoteCalendarAdapter implements CalendarApi {
  async createCalendar(input: CalendarListInput) {
    return apiRequest<CalendarList>("/calendar/calendars", {
      body: JSON.stringify(input),
      method: "POST",
    })
  }

  async createEvent(input: CalendarEventInput) {
    return apiRequest<CalendarEvent>("/calendar/events", {
      body: JSON.stringify(input),
      method: "POST",
    })
  }

  async deleteCalendar(id: string) {
    await apiRequest<void>(`/calendar/calendars/${encodeURIComponent(id)}`, {
      method: "DELETE",
    })
  }

  async deleteEvent(id: string, _recurrenceScope?: RecurrenceScope) {
    await apiRequest<void>(`/calendar/events/${encodeURIComponent(id)}`, {
      method: "DELETE",
    })
  }

  async exportCalendar(format: ExportFormat) {
    return apiRequest<string>(`/calendar/export?format=${format}`)
  }

  async importCalendar(file: File) {
    const text = await file.text()
    return apiRequest<{ calendars: CalendarList[]; events: CalendarEvent[] }>(
      "/calendar/import",
      {
        body: text,
        method: "POST",
      }
    )
  }

  async listCalendars() {
    return apiRequest<CalendarList[]>("/calendar/calendars")
  }

  async listEvents(range: EventRange, filters?: { calendarIds?: string[] }) {
    const params = new URLSearchParams({
      end: range.end.toISOString(),
      start: range.start.toISOString(),
    })

    if (filters?.calendarIds?.length) {
      params.set("calendarIds", filters.calendarIds.join(","))
    }

    return apiRequest<CalendarOccurrence[]>(`/calendar/events?${params}`)
  }

  async searchEvents(query: string) {
    return apiRequest<CalendarOccurrence[]>(
      `/calendar/search?q=${encodeURIComponent(query)}`
    )
  }

  async updateCalendar(id: string, patch: Partial<CalendarList>) {
    return apiRequest<CalendarList>(
      `/calendar/calendars/${encodeURIComponent(id)}`,
      {
        body: JSON.stringify(patch),
        method: "PATCH",
      }
    )
  }

  async updateEvent(
    id: string,
    patch: Partial<CalendarEvent>,
    _recurrenceScope?: RecurrenceScope
  ) {
    return apiRequest<CalendarEvent>(
      `/calendar/events/${encodeURIComponent(id)}`,
      {
        body: JSON.stringify(patch),
        method: "PATCH",
      }
    )
  }
}

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react"
import {
  Bell,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Circle,
  Clock3,
  Link,
  MapPin,
  MoreHorizontal,
  PanelLeft,
  Paperclip,
  Plus,
  Repeat,
  Search,
  Users,
  Video,
  X,
} from "lucide-react"

import {
  addDays,
  addMonths,
  addYears,
  buildDefaultEvent,
  buildMonthCells,
  calendarApi,
  eventDisplayTime,
  formatDateKey,
  formatMonthTitle,
  formatShortDate,
  formatTime,
  groupCalendars,
  isSameDay,
  rangeForView,
  startOfWeek,
  WEEKDAY_LABELS,
  type Availability,
  type CalendarEventInput,
  type CalendarList,
  type CalendarOccurrence,
  type CalendarView,
  type RecurrenceScope,
  type RepeatFrequency,
} from "@/components/calendar-data"

type DraftEvent = CalendarEventInput & {
  id?: string
  occurrenceDateKey?: string
  sourceEventId?: string
}

type EditorState = {
  draft: DraftEvent
  mode: "create" | "edit"
  scope: RecurrenceScope
} | null

const CALENDAR_COLORS = [
  "#ff3b30",
  "#ff9500",
  "#ffcc00",
  "#34c759",
  "#5ac8fa",
  "#007aff",
  "#af52de",
  "#ff2d55",
]

const VIEW_LABELS: Record<CalendarView, string> = {
  day: "日",
  week: "周",
  month: "月",
  year: "年",
}

const REPEAT_LABELS: Record<RepeatFrequency, string> = {
  none: "不重复",
  daily: "每天",
  weekly: "每周",
  monthly: "每月",
  yearly: "每年",
  custom: "自定义",
}

const AVAILABILITY_LABELS: Record<Availability, string> = {
  busy: "忙碌",
  free: "空闲",
  tentative: "暂定",
}

const RSVP_LABELS = {
  accepted: "接受",
  tentative: "待定",
  declined: "拒绝",
  pending: "待回复",
} as const

const KNOWN_TEXT_LABELS: Record<string, string> = {
  "1 day before": "提前 1 天",
  "10 minutes before": "提前 10 分钟",
  "25 minutes": "25 分钟",
  "35 minutes": "35 分钟",
  "45 minutes": "45 分钟",
  "At time of event": "事件开始时",
  Birthdays: "生日",
  Calendar: "日历",
  "Call supplier": "联系供应商",
  "Daily standup": "每日站会",
  "Dad birthday": "爸爸生日",
  "Design review": "设计评审",
  Family: "家人",
  "Family dinner": "家庭晚餐",
  Home: "家庭",
  "FaceTime with Maya": "与 Maya FaceTime",
  "Flight to SFO": "飞往 SFO",
  "Focus Blocks": "专注时段",
  "Launches": "发布计划",
  "New Event": "新建事件",
  None: "无",
  Other: "其他",
  Personal: "个人",
  "Product sync": "产品同步",
  "Product Team": "产品团队",
  "Project planning": "项目规划",
  "Quarterly planning": "季度规划",
  "Renew subscription": "续订服务",
  "Sprint planning": "冲刺规划",
  "Time to leave": "出发提醒",
  "Time to leave: Airport": "该出发了：前往机场",
  "Pacific Time": "太平洋时间",
  "US Holidays": "美国节假日",
  Work: "工作",
}

function localizeKnownText(value: string) {
  return KNOWN_TEXT_LABELS[value] ?? value
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}

export function CalendarAppWindow({
  className = "",
  windowControls,
}: {
  className?: string
  windowControls?: ReactNode
}) {
  const importInputRef = useRef<HTMLInputElement>(null)
  const [calendars, setCalendars] = useState<CalendarList[]>([])
  const [occurrences, setOccurrences] = useState<CalendarOccurrence[]>([])
  const [currentDate, setCurrentDate] = useState(new Date(2026, 5, 1))
  const [selectedDate, setSelectedDate] = useState(new Date(2026, 5, 18))
  const [view, setView] = useState<CalendarView>("month")
  const [selectedOccurrence, setSelectedOccurrence] =
    useState<CalendarOccurrence | null>(null)
  const [editor, setEditor] = useState<EditorState>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [searchResults, setSearchResults] = useState<CalendarOccurrence[]>([])
  const [moreOpen, setMoreOpen] = useState(false)
  const [calendarMenuId, setCalendarMenuId] = useState<string | null>(null)
  const [initialPanelsReady, setInitialPanelsReady] = useState(false)
  const [notificationVisible, setNotificationVisible] = useState(true)
  const [toast, setToast] = useState("")
  const [reloadKey, setReloadKey] = useState(0)

  const calendarById = useMemo(
    () => new Map(calendars.map((calendar) => [calendar.id, calendar])),
    [calendars]
  )

  const visibleCalendarIds = useMemo(
    () =>
      calendars
        .filter((calendar) => calendar.visible)
        .map((calendar) => calendar.id),
    [calendars]
  )

  const eventsByDate = useMemo(() => {
    const grouped = new Map<string, CalendarOccurrence[]>()
    for (const event of occurrences) {
      grouped.set(event.occurrenceDateKey, [
        ...(grouped.get(event.occurrenceDateKey) ?? []),
        event,
      ])
    }
    return grouped
  }, [occurrences])

  useEffect(() => {
    let ignore = false

    async function loadCalendars() {
      const nextCalendars = await calendarApi.listCalendars()
      if (!ignore) {
        setCalendars(nextCalendars)
      }
    }

    loadCalendars()

    return () => {
      ignore = true
    }
  }, [reloadKey])

  useEffect(() => {
    let ignore = false

    async function loadEvents() {
      const range = rangeForView(currentDate, view)
      const nextEvents = await calendarApi.listEvents(range, {
        calendarIds: visibleCalendarIds,
      })
      if (!ignore) {
        setOccurrences(nextEvents)
      }
    }

    loadEvents()
    window.addEventListener("focus", loadEvents)
    const interval = window.setInterval(loadEvents, 5_000)

    return () => {
      ignore = true
      window.removeEventListener("focus", loadEvents)
      window.clearInterval(interval)
    }
  }, [currentDate, reloadKey, view, visibleCalendarIds])

  useEffect(() => {
    let ignore = false

    async function search() {
      const results = await calendarApi.searchEvents(searchQuery)
      if (!ignore) {
        setSearchResults(results.slice(0, 5))
      }
    }

    search()

    return () => {
      ignore = true
    }
  }, [searchQuery, reloadKey])

  useEffect(() => {
    if (!toast) {
      return undefined
    }

    const timeout = window.setTimeout(() => setToast(""), 2400)
    return () => window.clearTimeout(timeout)
  }, [toast])

  useEffect(() => {
    if (
      initialPanelsReady ||
      calendars.length === 0 ||
      occurrences.length === 0
    ) {
      return
    }

    const june18 = new Date(2026, 5, 18)
    const featuredEvent =
      occurrences.find(
        (event) =>
          event.title === "Quarterly planning" || event.title === "季度规划"
      ) ??
      occurrences[0]

    const timeoutId = window.setTimeout(() => {
      setSelectedDate(june18)
      setSelectedOccurrence(featuredEvent)
      setInitialPanelsReady(true)
    }, 0)

    return () => window.clearTimeout(timeoutId)
  }, [calendars, initialPanelsReady, occurrences])

  const defaultCalendarId = calendars[0]?.id ?? "personal"

  const refresh = () => setReloadKey((key) => key + 1)

  const showToast = (message: string) => {
    setToast(message)
  }

  const navigate = (direction: -1 | 1) => {
    if (view === "day") {
      setCurrentDate((date) => addDays(date, direction))
      return
    }

    if (view === "week") {
      setCurrentDate((date) => addDays(date, direction * 7))
      return
    }

    if (view === "year") {
      setCurrentDate((date) => addYears(date, direction))
      return
    }

    setCurrentDate((date) => addMonths(date, direction))
  }

  const openEditorForDate = (date: Date) => {
    setSelectedDate(date)
    setSelectedOccurrence(null)
    setEditor({
      draft: buildDefaultEvent(date, defaultCalendarId),
      mode: "create",
      scope: "series",
    })
  }

  const openEditorForOccurrence = (event: CalendarOccurrence) => {
    setSelectedOccurrence(event)
    setEditor({
      draft: {
        ...event,
        end: event.occurrenceEnd,
        occurrenceDateKey: event.occurrenceDateKey,
        sourceEventId: event.sourceEventId,
        start: event.occurrenceStart,
      },
      mode: "edit",
      scope: "series",
    })
  }

  const selectOccurrence = (event: CalendarOccurrence) => {
    setSelectedOccurrence(event)
    setSelectedDate(new Date(event.occurrenceStart))
  }

  const jumpToOccurrence = (event: CalendarOccurrence) => {
    setCurrentDate(new Date(event.occurrenceStart))
    setSelectedDate(new Date(event.occurrenceStart))
    setSelectedOccurrence(event)
    setSearchQuery("")
  }

  const saveEditor = async () => {
    if (!editor) {
      return
    }

    const input = draftToInput(editor.draft)
    const sourceEvent = editor.draft.sourceEventId

    if (editor.mode === "create" || !editor.draft.id || !sourceEvent) {
      await calendarApi.createEvent(input)
      showToast("事件已创建")
      setEditor(null)
      refresh()
      return
    }

    if (selectedOccurrence && selectedOccurrence.repeat.frequency !== "none") {
      await saveRecurringEdit(input, editor.scope, selectedOccurrence)
    } else {
      await calendarApi.updateEvent(sourceEvent, input)
    }

    showToast("事件已保存")
    setEditor(null)
    refresh()
  }

  const saveRecurringEdit = async (
    input: CalendarEventInput,
    scope: RecurrenceScope,
    occurrence: CalendarOccurrence
  ) => {
    if (scope === "this") {
      const excludedDates = [
        ...(occurrence.repeat.excludedDates ?? []),
        occurrence.occurrenceDateKey,
      ]
      await calendarApi.updateEvent(occurrence.sourceEventId, {
        repeat: {
          ...occurrence.repeat,
          excludedDates: Array.from(new Set(excludedDates)),
        },
      })
      await calendarApi.createEvent({
        ...input,
        repeat: {
          endMode: "never",
          excludedDates: [],
          frequency: "none",
          interval: 1,
        },
      })
      return
    }

    if (scope === "future") {
      const previousDay = addDays(new Date(occurrence.occurrenceStart), -1)
      await calendarApi.updateEvent(occurrence.sourceEventId, {
        repeat: {
          ...occurrence.repeat,
          endDate: formatDateKey(previousDay),
          endMode: "onDate",
        },
      })
      await calendarApi.createEvent(input)
      return
    }

    await calendarApi.updateEvent(occurrence.sourceEventId, input, scope)
  }

  const deleteSelectedEvent = async (scope: RecurrenceScope) => {
    if (!selectedOccurrence) {
      return
    }

    if (selectedOccurrence.repeat.frequency !== "none" && scope === "this") {
      await calendarApi.updateEvent(selectedOccurrence.sourceEventId, {
        repeat: {
          ...selectedOccurrence.repeat,
          excludedDates: [
            ...(selectedOccurrence.repeat.excludedDates ?? []),
            selectedOccurrence.occurrenceDateKey,
          ],
        },
      })
    } else if (
      selectedOccurrence.repeat.frequency !== "none" &&
      scope === "future"
    ) {
      await calendarApi.updateEvent(selectedOccurrence.sourceEventId, {
        repeat: {
          ...selectedOccurrence.repeat,
          endDate: formatDateKey(
            addDays(new Date(selectedOccurrence.occurrenceStart), -1)
          ),
          endMode: "onDate",
        },
      })
    } else {
      await calendarApi.deleteEvent(selectedOccurrence.sourceEventId, scope)
    }

    setSelectedOccurrence(null)
    showToast("事件已删除")
    refresh()
  }

  const updateDraft = <Key extends keyof DraftEvent>(
    key: Key,
    value: DraftEvent[Key]
  ) => {
    setEditor((current) =>
      current
        ? {
            ...current,
            draft: {
              ...current.draft,
              [key]: value,
            },
          }
        : current
    )
  }

  const updateRepeat = <Key extends keyof DraftEvent["repeat"]>(
    key: Key,
    value: DraftEvent["repeat"][Key]
  ) => {
    setEditor((current) =>
      current
        ? {
            ...current,
            draft: {
              ...current.draft,
              repeat: {
                ...current.draft.repeat,
                [key]: value,
              },
            },
          }
        : current
    )
  }

  const toggleCalendarVisibility = async (calendar: CalendarList) => {
    const updated = await calendarApi.updateCalendar(calendar.id, {
      visible: !calendar.visible,
    })
    setCalendars((items) =>
      items.map((item) => (item.id === updated.id ? updated : item))
    )
    refresh()
  }

  const createCalendar = async () => {
    await calendarApi.createCalendar({
      color: CALENDAR_COLORS[calendars.length % CALENDAR_COLORS.length],
      group: "iCloud",
      name: `新建日历 ${calendars.length + 1}`,
      visible: true,
    })
    showToast("日历已创建")
    refresh()
  }

  const updateCalendarColor = async (calendar: CalendarList, color: string) => {
    await calendarApi.updateCalendar(calendar.id, { color })
    showToast("日历颜色已更新")
    refresh()
  }

  const renameCalendar = async (calendar: CalendarList) => {
    const nextName = window.prompt("重命名日历", calendar.name)
    if (!nextName?.trim()) {
      return
    }
    await calendarApi.updateCalendar(calendar.id, { name: nextName.trim() })
    showToast("日历已重命名")
    refresh()
  }

  const deleteCalendar = async (calendar: CalendarList) => {
    const confirmed = window.confirm(`确定删除“${localizeKnownText(calendar.name)}”吗？`)
    if (!confirmed) {
      return
    }
    await calendarApi.deleteCalendar(calendar.id)
    showToast("日历已删除")
    refresh()
  }

  const exportCalendar = async (format: "json" | "ics") => {
    const text = await calendarApi.exportCalendar(format)
    const blob = new Blob([text], {
      type: format === "json" ? "application/json" : "text/calendar",
    })
    const href = URL.createObjectURL(blob)
    const anchor = document.createElement("a")
    anchor.href = href
    anchor.download = `日历导出.${format}`
    anchor.click()
    URL.revokeObjectURL(href)
    showToast(`已导出 ${format.toUpperCase()}`)
  }

  const importCalendar = async (file: File | null) => {
    if (!file) {
      return
    }
    try {
      await calendarApi.importCalendar(file)
      showToast("日历已导入")
      refresh()
    } catch {
      showToast("导入失败")
    }
  }

  const printCalendar = () => {
    showToast("打印预览已就绪")
  }

  return (
    <section
      className={`flex h-full w-full flex-col overflow-hidden rounded-[18px] border border-black/12 bg-[#f5f5f7] text-[#1d1d1f] shadow-[0_42px_110px_rgba(0,0,0,0.45)] ${className}`}
    >
      <input
        ref={importInputRef}
        type="file"
        accept="application/json,.json"
        className="hidden"
        onChange={(event) =>
          importCalendar(event.currentTarget.files?.[0] ?? null)
        }
      />
      <div
        className="flex h-[62px] shrink-0 items-center border-b border-black/10 bg-[#f4f4f5]/88 px-[18px] backdrop-blur-[28px]"
        data-window-drag-handle
      >
        {windowControls}
        <button
          className="ml-[24px] flex size-[32px] items-center justify-center rounded-[7px] text-[#5f6169] hover:bg-black/6"
          onClick={() => showToast("Sidebar toggled")}
        >
          <PanelLeft size={18} />
        </button>
        <button
          className="ml-[12px] h-[32px] rounded-[7px] border border-black/10 bg-white/70 px-[13px] text-[13px] font-medium text-[#202124] shadow-[0_1px_1px_rgba(0,0,0,0.06)]"
          onClick={() => {
            const today = new Date()
            setCurrentDate(today)
            setSelectedDate(today)
          }}
        >
          今天
        </button>
        <div className="ml-[10px] flex overflow-hidden rounded-[7px] border border-black/10 bg-white/60">
          <button
            className="flex size-[32px] items-center justify-center border-r border-black/8 text-[#60636c]"
            onClick={() => navigate(-1)}
          >
            <ChevronLeft size={17} />
          </button>
          <button
            className="flex size-[32px] items-center justify-center text-[#60636c]"
            onClick={() => navigate(1)}
          >
            <ChevronRight size={17} />
          </button>
        </div>
        <h2 className="ml-[24px] text-[22px] font-semibold tracking-[-0.02em] text-[#1d1d1f]">
          {view === "year"
            ? currentDate.getFullYear()
            : formatMonthTitle(currentDate)}
        </h2>
        <div className="ml-auto flex h-[32px] overflow-hidden rounded-[8px] border border-black/10 bg-[#e8e8ea] p-[2px] text-[13px] font-medium text-[#4c4d52]">
          {(["day", "week", "month", "year"] as CalendarView[]).map((item) => (
            <button
              key={item}
              className={`rounded-[6px] px-[13px] capitalize ${
                view === item
                  ? "bg-white text-[#1d1d1f] shadow-[0_1px_3px_rgba(0,0,0,0.16)]"
                  : ""
              }`}
              onClick={() => setView(item)}
            >
              {VIEW_LABELS[item]}
            </button>
          ))}
        </div>
        <div className="ml-[14px] flex h-[32px] w-[184px] items-center rounded-[8px] border border-black/10 bg-white/72 px-[10px] text-[#8a8d95]">
          <Search size={15} />
          <input
            className="ml-[7px] min-w-0 flex-1 bg-transparent text-[13px] text-[#202124] outline-none placeholder:text-[#8a8d95]"
            placeholder="搜索"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>
        <button
          className="ml-[10px] flex size-[32px] items-center justify-center rounded-[8px] bg-[#007aff] text-white shadow-[0_4px_10px_rgba(0,122,255,0.24)]"
          onClick={() => openEditorForDate(selectedDate)}
        >
          <Plus size={19} />
        </button>
        <div className="relative">
          <button
            className="ml-[8px] flex size-[32px] items-center justify-center rounded-[8px] text-[#60636c] hover:bg-black/6"
            onClick={() => setMoreOpen((open) => !open)}
          >
            <MoreHorizontal size={19} />
          </button>
          {moreOpen && (
            <MenuPanel className="top-[38px] right-0 w-[178px]">
              <MenuButton onClick={printCalendar}>打印日历</MenuButton>
              <MenuButton onClick={() => exportCalendar("json")}>
                导出 JSON
              </MenuButton>
              <MenuButton onClick={() => exportCalendar("ics")}>
                导出 ICS
              </MenuButton>
              <MenuButton onClick={() => importInputRef.current?.click()}>
                导入日历
              </MenuButton>
              <MenuButton
                onClick={() => {
                  createCalendar()
                  setMoreOpen(false)
                }}
              >
                新建日历
              </MenuButton>
              <MenuButton
                onClick={() => {
                  showToast("已添加订阅日历")
                  setMoreOpen(false)
                }}
              >
                通过 URL 订阅
              </MenuButton>
              <MenuButton
                onClick={() => {
                  showToast("日历已刷新")
                  refresh()
                }}
              >
                刷新
              </MenuButton>
            </MenuPanel>
          )}
        </div>
      </div>

      <div className="flex min-h-0 flex-1">
        <aside className="w-[260px] border-r border-black/10 bg-[#ececef]/72 p-[14px] backdrop-blur-[24px]">
          <MiniMonthPicker
            currentDate={currentDate}
            onSelectDate={(date) => {
              setCurrentDate(date)
              setSelectedDate(date)
            }}
            selectedDate={selectedDate}
          />
          <div className="mt-[16px] flex items-center justify-between px-[4px]">
            <span className="text-[12px] font-semibold tracking-[0.08em] text-[#7b7d85] uppercase">
              日历
            </span>
            <button
              className="flex size-[22px] items-center justify-center rounded-full text-[#007aff] hover:bg-white/70"
              onClick={createCalendar}
            >
              <Plus size={15} />
            </button>
          </div>
          <CalendarSidebar
            calendarMenuId={calendarMenuId}
            calendars={calendars}
            onColor={updateCalendarColor}
            onDelete={deleteCalendar}
            onMenu={setCalendarMenuId}
            onRename={renameCalendar}
            onToggle={toggleCalendarVisibility}
          />
          {notificationVisible && (
            <div className="mt-[18px] rounded-[12px] border border-black/7 bg-white/56 p-[10px] text-[12px] text-[#646770]">
              <div className="mb-[8px] flex items-center gap-[7px] font-semibold text-[#3b3d43]">
                <Bell size={14} className="text-[#ff9500]" />
                出发提醒
              </div>
              机场方向路况较拥堵，建议在 3:40 PM 前出发。
            </div>
          )}
        </aside>

        <div className="relative min-h-0 flex-1 bg-[#fbfbfc]">
          {view === "month" && (
            <MonthView
              currentDate={currentDate}
              eventsByDate={eventsByDate}
              onCreate={openEditorForDate}
              onSelectDate={(date) => {
                setSelectedDate(date)
                setCurrentDate(date)
              }}
              onSelectEvent={selectOccurrence}
              selectedDate={selectedDate}
              calendarById={calendarById}
            />
          )}
          {view === "week" && (
            <WeekView
              calendarById={calendarById}
              currentDate={currentDate}
              eventsByDate={eventsByDate}
              onCreate={openEditorForDate}
              onSelectDate={setSelectedDate}
              onSelectEvent={selectOccurrence}
            />
          )}
          {view === "day" && (
            <DayView
              calendarById={calendarById}
              currentDate={currentDate}
              events={eventsByDate.get(formatDateKey(currentDate)) ?? []}
              onCreate={openEditorForDate}
              onSelectEvent={selectOccurrence}
            />
          )}
          {view === "year" && (
            <YearView
              currentDate={currentDate}
              occurrences={occurrences}
              onSelectMonth={(date) => {
                setCurrentDate(date)
                setView("month")
              }}
            />
          )}

          {editor && (
            <EventEditor
              calendars={calendars}
              editor={editor}
              onCancel={() => setEditor(null)}
              onDraft={updateDraft}
              onRepeat={updateRepeat}
              onSave={saveEditor}
              onScope={(scope) =>
                setEditor((current) =>
                  current ? { ...current, scope } : current
                )
              }
            />
          )}

          {selectedOccurrence && (
            <EventDetails
              calendar={calendarById.get(selectedOccurrence.calendarId)}
              event={selectedOccurrence}
              onClose={() => setSelectedOccurrence(null)}
              onDelete={deleteSelectedEvent}
              onEdit={() => {
                if (selectedOccurrence) {
                  openEditorForOccurrence(selectedOccurrence)
                }
              }}
              onToast={showToast}
            />
          )}

          <SearchResults
            calendarById={calendarById}
            onJump={jumpToOccurrence}
            results={searchResults}
            visible={searchQuery.length > 0}
          />

          {notificationVisible && (
            <NotificationPreview
              onDismiss={() => setNotificationVisible(false)}
              onToast={showToast}
            />
          )}

          <div className="absolute bottom-[20px] left-[298px] flex items-center gap-[8px] rounded-full border border-black/8 bg-white/86 px-[12px] py-[7px] text-[12px] text-[#5d6068] shadow-[0_10px_26px_rgba(0,0,0,0.12)]">
            <Clock3 size={14} />
            时区：浮动 - 太平洋时间
            <span className="h-[14px] w-px bg-black/12" />
            <button onClick={printCalendar}>打印</button>-
            <button onClick={() => importInputRef.current?.click()}>
              导入
            </button>
            -<button onClick={() => exportCalendar("json")}>导出</button>-
            <button onClick={() => showToast("已添加订阅日历")}>
              订阅
            </button>
          </div>
        </div>
      </div>

      {toast && (
        <div className="absolute bottom-[22px] left-1/2 z-30 -translate-x-1/2 rounded-full border border-black/10 bg-white/95 px-[16px] py-[9px] text-[12px] font-medium text-[#303238] shadow-[0_14px_34px_rgba(0,0,0,0.18)] backdrop-blur-[20px]">
          {toast}
        </div>
      )}
    </section>
  )
}

function CalendarSidebar({
  calendarMenuId,
  calendars,
  onColor,
  onDelete,
  onMenu,
  onRename,
  onToggle,
}: {
  calendarMenuId: string | null
  calendars: CalendarList[]
  onColor: (calendar: CalendarList, color: string) => void
  onDelete: (calendar: CalendarList) => void
  onMenu: (id: string | null) => void
  onRename: (calendar: CalendarList) => void
  onToggle: (calendar: CalendarList) => void
}) {
  const groups = groupCalendars(calendars)

  return (
    <div className="mt-[8px] space-y-[14px]">
      {Object.entries(groups).map(([group, items]) => (
        <div key={group}>
          <div className="px-[4px] text-[12px] font-semibold text-[#8a8d95]">
            {localizeKnownText(group)}
          </div>
          <div className="mt-[6px] space-y-[2px]">
            {items.map((calendar) => (
              <div key={calendar.id} className="relative">
                <div className="flex h-[29px] items-center rounded-[7px] px-[6px] text-[13px] text-[#292a2f] hover:bg-white/65">
                  <button
                    className="mr-[8px] flex size-[12px] rounded-full border border-black/10"
                    style={{
                      backgroundColor: calendar.visible
                        ? calendar.color
                        : "transparent",
                    }}
                    onClick={() => onToggle(calendar)}
                  />
                  <span className="min-w-0 flex-1 truncate">
                    {localizeKnownText(calendar.name)}
                  </span>
                  {calendar.shared && (
                    <Users size={13} className="ml-[5px] text-[#8c8f98]" />
                  )}
                  {calendar.subscribed && (
                    <Repeat size={13} className="ml-[5px] text-[#8c8f98]" />
                  )}
                  <button
                    className="ml-[5px] rounded-[5px] px-[3px] text-[#8c8f98] hover:bg-white"
                    onClick={() =>
                      onMenu(
                        calendarMenuId === calendar.id ? null : calendar.id
                      )
                    }
                  >
                    <MoreHorizontal size={14} />
                  </button>
                </div>
                {calendarMenuId === calendar.id && (
                  <MenuPanel className="top-[28px] left-[28px] z-20 w-[170px]">
                    <MenuButton onClick={() => onRename(calendar)}>
                      重命名
                    </MenuButton>
                    <MenuButton onClick={() => onToggle(calendar)}>
                      {calendar.visible ? "隐藏" : "显示"}
                    </MenuButton>
                    <MenuButton
                      onClick={() =>
                        onColor(calendar, nextColor(calendar.color))
                      }
                    >
                      更换颜色
                    </MenuButton>
                    <div className="flex gap-[5px] px-[8px] py-[6px]">
                      {CALENDAR_COLORS.map((color) => (
                        <button
                          key={color}
                          className="size-[16px] rounded-full border border-black/10"
                          style={{ backgroundColor: color }}
                          onClick={() => onColor(calendar, color)}
                        />
                      ))}
                    </div>
                    <MenuButton onClick={() => onDelete(calendar)}>
                      删除
                    </MenuButton>
                    <MenuButton onClick={() => onMenu(null)}>关闭</MenuButton>
                  </MenuPanel>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

function MiniMonthPicker({
  currentDate,
  onSelectDate,
  selectedDate,
}: {
  currentDate: Date
  onSelectDate: (date: Date) => void
  selectedDate: Date
}) {
  const cells = buildMonthCells(currentDate).slice(0, 35)

  return (
    <div className="rounded-[14px] border border-black/5 bg-white/55 p-[12px] shadow-[inset_0_1px_0_rgba(255,255,255,0.75)]">
      <div className="mb-[10px] flex items-center justify-between text-[13px] font-semibold text-[#1d1d1f]">
        <span>{formatMonthTitle(currentDate)}</span>
        <div className="flex gap-[8px] text-[#7a7a7d]">
          <ChevronLeft size={14} />
          <ChevronRight size={14} />
        </div>
      </div>
      <div className="grid grid-cols-7 gap-y-[7px] text-center text-[10px] font-medium text-[#8b8b90]">
        {WEEKDAY_LABELS.map((day, index) => (
          <span key={`${day}-${index}`}>{day}</span>
        ))}
        {cells.map((cell) => (
          <button
            key={cell.dateKey}
            className={`mx-auto flex size-[22px] items-center justify-center rounded-full text-[11px] ${
              cell.today
                ? "bg-[#007aff] font-semibold text-white"
                : isSameDay(cell.date, selectedDate)
                  ? "bg-[#d7eaff] font-semibold text-[#005ac1]"
                  : cell.currentMonth
                    ? "text-[#2f3034]"
                    : "text-[#bebec3]"
            }`}
            onClick={() => onSelectDate(cell.date)}
          >
            {cell.day}
          </button>
        ))}
      </div>
    </div>
  )
}

function MonthView({
  calendarById,
  currentDate,
  eventsByDate,
  onCreate,
  onSelectDate,
  onSelectEvent,
  selectedDate,
}: {
  calendarById: Map<string, CalendarList>
  currentDate: Date
  eventsByDate: Map<string, CalendarOccurrence[]>
  onCreate: (date: Date) => void
  onSelectDate: (date: Date) => void
  onSelectEvent: (event: CalendarOccurrence) => void
  selectedDate: Date
}) {
  const cells = buildMonthCells(currentDate).slice(0, 35)

  return (
    <>
      <div className="grid h-[36px] grid-cols-7 border-b border-black/10 bg-[#f7f7f8] text-center text-[12px] font-semibold tracking-[0.04em] text-[#777a82] uppercase">
        {WEEKDAY_LABELS.map((day) => (
          <div key={day} className="flex items-center justify-center">
            {day}
          </div>
        ))}
      </div>
      <div className="grid h-[calc(100%-36px)] grid-cols-7 grid-rows-5">
        {cells.map((cell) => {
          const events = eventsByDate.get(cell.dateKey) ?? []
          return (
            <div
              key={cell.dateKey}
              className={`relative border-r border-b border-black/8 p-[7px] ${
                cell.weekend ? "bg-[#f5f6f8]" : "bg-white"
              }`}
              onClick={() => onSelectDate(cell.date)}
              onDoubleClick={() => onCreate(cell.date)}
            >
              <div
                className={`ml-auto flex size-[25px] items-center justify-center rounded-full text-[13px] font-medium ${
                  cell.today
                    ? "bg-[#007aff] text-white"
                    : isSameDay(cell.date, selectedDate)
                      ? "bg-[#cfe5ff] text-[#005ac1]"
                      : cell.currentMonth
                        ? "text-[#303136]"
                        : "text-[#b7bac2]"
                }`}
              >
                {cell.day}
              </div>
              <div className="mt-[5px] space-y-[4px]">
                {events.slice(0, 4).map((event) => (
                  <EventPill
                    key={event.occurrenceId}
                    calendar={calendarById.get(event.calendarId)}
                    event={event}
                    onSelect={onSelectEvent}
                  />
                ))}
                {events.length > 4 && (
                  <button className="h-[20px] rounded-[5px] px-[6px] text-[11px] font-medium text-[#6f727a]">
                    +{events.length - 4} 更多
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}

function WeekView({
  calendarById,
  currentDate,
  eventsByDate,
  onCreate,
  onSelectDate,
  onSelectEvent,
}: {
  calendarById: Map<string, CalendarList>
  currentDate: Date
  eventsByDate: Map<string, CalendarOccurrence[]>
  onCreate: (date: Date) => void
  onSelectDate: (date: Date) => void
  onSelectEvent: (event: CalendarOccurrence) => void
}) {
  const start = startOfWeek(currentDate)
  const days = Array.from({ length: 7 }, (_, index) => addDays(start, index))

  return (
    <>
      <div className="grid h-[48px] grid-cols-7 border-b border-black/10 bg-[#f7f7f8] text-center text-[12px] font-semibold tracking-[0.04em] text-[#777a82] uppercase">
        {days.map((day, index) => (
          <button
            key={formatDateKey(day)}
            className="flex flex-col items-center justify-center"
            onClick={() => onSelectDate(day)}
          >
            <span>{WEEKDAY_LABELS[index]}</span>
            <span className="text-[14px] text-[#303136]">{day.getDate()}</span>
          </button>
        ))}
      </div>
      <div className="grid h-[calc(100%-48px)] grid-cols-7">
        {days.map((day) => {
          const events = eventsByDate.get(formatDateKey(day)) ?? []
          return (
            <div
              key={formatDateKey(day)}
              className="border-r border-black/8 bg-white p-[8px]"
              onDoubleClick={() => onCreate(day)}
            >
              <div className="space-y-[6px]">
                {events.map((event) => (
                  <EventPill
                    key={event.occurrenceId}
                    calendar={calendarById.get(event.calendarId)}
                    event={event}
                    onSelect={onSelectEvent}
                  />
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}

function DayView({
  calendarById,
  currentDate,
  events,
  onCreate,
  onSelectEvent,
}: {
  calendarById: Map<string, CalendarList>
  currentDate: Date
  events: CalendarOccurrence[]
  onCreate: (date: Date) => void
  onSelectEvent: (event: CalendarOccurrence) => void
}) {
  return (
    <div className="h-full bg-white">
      <div className="flex h-[48px] items-center border-b border-black/10 bg-[#f7f7f8] px-[18px] text-[13px] font-semibold text-[#555861]">
        {formatShortDate(currentDate)}
      </div>
      <div className="relative h-[calc(100%-48px)] overflow-hidden">
        {Array.from({ length: 12 }, (_, index) => (
          <div
            key={index}
            className="flex h-[56px] border-b border-black/7 text-[11px] text-[#8d9098]"
            onDoubleClick={() => onCreate(currentDate)}
          >
            <span className="w-[68px] px-[12px] pt-[6px]">{index + 8}:00</span>
            <span className="flex-1" />
          </div>
        ))}
        <div className="absolute top-[18px] left-[78px] w-[420px] space-y-[8px]">
          {events.map((event) => (
            <EventPill
              key={event.occurrenceId}
              calendar={calendarById.get(event.calendarId)}
              event={event}
              onSelect={onSelectEvent}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function YearView({
  currentDate,
  occurrences,
  onSelectMonth,
}: {
  currentDate: Date
  occurrences: CalendarOccurrence[]
  onSelectMonth: (date: Date) => void
}) {
  return (
    <div className="grid h-full grid-cols-4 gap-[14px] overflow-hidden bg-[#fbfbfc] p-[18px]">
      {Array.from({ length: 12 }, (_, month) => {
        const date = new Date(currentDate.getFullYear(), month, 1)
        const eventsInMonth = occurrences.filter(
          (event) => new Date(event.occurrenceStart).getMonth() === month
        )
        return (
          <button
            key={month}
            className="rounded-[14px] border border-black/8 bg-white p-[12px] text-left shadow-[0_6px_18px_rgba(0,0,0,0.06)]"
            onClick={() => onSelectMonth(date)}
          >
            <div className="mb-[8px] text-[14px] font-semibold">
              {formatMonthTitle(date)}
            </div>
            <MiniMonthDots date={date} events={eventsInMonth} />
          </button>
        )
      })}
    </div>
  )
}

function MiniMonthDots({
  date,
  events,
}: {
  date: Date
  events: CalendarOccurrence[]
}) {
  const eventDays = new Set(events.map((event) => event.occurrenceDateKey))
  return (
    <div className="grid grid-cols-7 gap-y-[5px] text-center text-[10px] text-[#8b8e96]">
      {WEEKDAY_LABELS.map((day) => (
        <span key={day}>{day[0]}</span>
      ))}
      {buildMonthCells(date)
        .slice(0, 35)
        .map((cell) => (
          <span
            key={cell.dateKey}
            className={`relative mx-auto flex size-[18px] items-center justify-center rounded-full ${
              cell.currentMonth ? "text-[#303136]" : "text-[#c5c7ce]"
            }`}
          >
            {cell.day}
            {eventDays.has(cell.dateKey) && (
              <span className="absolute bottom-[-2px] size-[3px] rounded-full bg-[#007aff]" />
            )}
          </span>
        ))}
    </div>
  )
}

function EventPill({
  calendar,
  event,
  onSelect,
}: {
  calendar?: CalendarList
  event: CalendarOccurrence
  onSelect: (event: CalendarOccurrence) => void
}) {
  const color = event.color ?? calendar?.color ?? "#007aff"
  return (
    <button
      className={`flex h-[20px] w-full items-center rounded-[5px] px-[6px] text-left text-[11px] font-medium ${
        event.reminder
          ? "border border-black/9 bg-white text-[#4b4d55]"
          : "text-white"
      }`}
      style={event.reminder ? undefined : { backgroundColor: color }}
      onClick={(clickEvent) => {
        clickEvent.stopPropagation()
        onSelect(event)
      }}
    >
      {event.reminder && (
        <Circle size={8} className="mr-[5px]" style={{ color }} />
      )}
      <span className="truncate">
        {eventDisplayTime(event)} {localizeKnownText(event.title)}
      </span>
    </button>
  )
}

function EventEditor({
  calendars,
  editor,
  onCancel,
  onDraft,
  onRepeat,
  onSave,
  onScope,
}: {
  calendars: CalendarList[]
  editor: NonNullable<EditorState>
  onCancel: () => void
  onDraft: <Key extends keyof DraftEvent>(
    key: Key,
    value: DraftEvent[Key]
  ) => void
  onRepeat: <Key extends keyof DraftEvent["repeat"]>(
    key: Key,
    value: DraftEvent["repeat"][Key]
  ) => void
  onSave: () => void
  onScope: (scope: RecurrenceScope) => void
}) {
  const draft = editor.draft

  return (
    <div className="absolute top-[142px] left-[456px] z-20 w-[342px] rounded-[16px] border border-black/10 bg-white/95 p-[14px] shadow-[0_24px_64px_rgba(0,0,0,0.24)] backdrop-blur-[24px]">
      <div className="mb-[10px] flex items-center justify-between">
        <h3 className="text-[15px] font-semibold">
          {editor.mode === "create" ? "新建事件" : "编辑事件"}
        </h3>
        <div className="flex gap-[6px]">
          <button
            className="rounded-[6px] bg-[#f1f2f4] px-[10px] py-[4px] text-[12px] font-semibold text-[#34363c]"
            onClick={onCancel}
          >
            取消
          </button>
          <button
            className="rounded-[6px] bg-[#007aff] px-[10px] py-[4px] text-[12px] font-semibold text-white"
            onClick={onSave}
          >
            保存
          </button>
        </div>
      </div>
      <input
        value={draft.title}
        className="h-[34px] w-full rounded-[8px] border border-black/10 bg-[#f7f7f8] px-[10px] text-[13px] ring-[#007aff]/30 outline-none focus:ring-3"
        onChange={(event) => onDraft("title", event.target.value)}
      />
      <div className="mt-[10px] space-y-[7px] text-[12px] text-[#4d5057]">
        <FormRow label="日历">
          <select
            className="max-w-[160px] rounded-full bg-[#dbeafe] px-[9px] py-[3px] text-[#005ac1] outline-none"
            value={draft.calendarId}
            onChange={(event) => onDraft("calendarId", event.target.value)}
          >
            {calendars.map((calendar) => (
              <option key={calendar.id} value={calendar.id}>
                {localizeKnownText(calendar.name)}
              </option>
            ))}
          </select>
        </FormRow>
        <FormRow label="全天">
          <button
            className={`h-[18px] w-[32px] rounded-full ${
              draft.allDay ? "bg-[#007aff]" : "bg-[#d1d1d6]"
            }`}
            onClick={() => onDraft("allDay", !draft.allDay)}
          />
        </FormRow>
        <FormRow label="开始">
          <input
            className="w-[150px] bg-transparent text-right font-medium text-[#22242a] outline-none"
            type="datetime-local"
            value={draft.start}
            onChange={(event) => onDraft("start", event.target.value)}
          />
        </FormRow>
        <FormRow label="结束">
          <input
            className="w-[150px] bg-transparent text-right font-medium text-[#22242a] outline-none"
            type="datetime-local"
            value={draft.end}
            onChange={(event) => onDraft("end", event.target.value)}
          />
        </FormRow>
        <FormRow label="重复">
          <select
            className="bg-transparent text-right font-medium text-[#22242a] outline-none"
            value={draft.repeat.frequency}
            onChange={(event) =>
              onRepeat("frequency", event.target.value as RepeatFrequency)
            }
          >
            {(
              ["none", "daily", "weekly", "monthly", "yearly", "custom"] as const
            ).map(
              (item) => (
                <option key={item} value={item}>
                  {REPEAT_LABELS[item]}
                </option>
              )
            )}
          </select>
        </FormRow>
        {draft.repeat.frequency !== "none" && (
          <>
            <FormRow label="重复间隔">
              <input
                className="w-[50px] bg-transparent text-right font-medium text-[#22242a] outline-none"
                min={1}
                type="number"
                value={draft.repeat.interval}
                onChange={(event) =>
                  onRepeat("interval", Number(event.target.value))
                }
              />
            </FormRow>
            <FormRow label="结束重复">
              <select
                className="bg-transparent text-right font-medium text-[#22242a] outline-none"
                value={draft.repeat.endMode}
                onChange={(event) =>
                  onRepeat(
                    "endMode",
                    event.target.value as DraftEvent["repeat"]["endMode"]
                  )
                }
              >
                <option value="never">永不</option>
                <option value="onDate">指定日期</option>
                <option value="after">次数后</option>
              </select>
            </FormRow>
          </>
        )}
        {draft.id && draft.repeat.frequency !== "none" && (
          <FormRow label="编辑范围">
            <select
              className="bg-transparent text-right font-medium text-[#22242a] outline-none"
              value={editor.scope}
              onChange={(event) =>
                onScope(event.target.value as RecurrenceScope)
              }
            >
              <option value="this">仅此事件</option>
              <option value="future">此后所有事件</option>
              <option value="series">整个重复系列</option>
            </select>
          </FormRow>
        )}
        <TextSelect
          label="提醒"
          value={draft.alert}
          values={uniqueValues([
            draft.alert,
            "无",
            "事件开始时",
            "提前 10 分钟",
            "提前 1 小时",
            "None",
            "At time of event",
            "10 minutes before",
            "1 hour before",
          ])}
          onChange={(value) => onDraft("alert", value)}
        />
        <TextSelect
          label="第二提醒"
          value={draft.secondAlert}
          values={uniqueValues([
            draft.secondAlert,
            "无",
            "出发提醒",
            "提前 1 天",
            "None",
            "Time to leave",
            "1 day before",
          ])}
          onChange={(value) => onDraft("secondAlert", value)}
        />
        <TextInput
          label="出行时间"
          value={draft.travelTime}
          onChange={(value) => onDraft("travelTime", value)}
        />
        <TextInput
          label="地点"
          value={draft.location}
          onChange={(value) => onDraft("location", value)}
        />
        <TextInput
          label="参与人"
          value={draft.invitees.map((invitee) => invitee.email).join(", ")}
          onChange={(value) =>
            onDraft(
              "invitees",
              value
                .split(",")
                .map((email) => email.trim())
                .filter(Boolean)
                .map((email) => ({ email, name: email, status: "pending" }))
            )
          }
        />
        <TextInput
          label="视频通话"
          value={draft.meetingLink}
          onChange={(value) => onDraft("meetingLink", value)}
        />
        <TextInput
          label="备注"
          value={draft.notes}
          onChange={(value) => onDraft("notes", value)}
        />
        <TextInput
          label="URL"
          value={draft.url}
          onChange={(value) => onDraft("url", value)}
        />
        <TextInput
          label="附件"
          value={draft.attachments.join(", ")}
          onChange={(value) =>
            onDraft(
              "attachments",
              value
                .split(",")
                .map((item) => item.trim())
                .filter(Boolean)
            )
          }
        />
        <TextInput
          label="时区"
          value={draft.timeZone}
          onChange={(value) => onDraft("timeZone", value)}
        />
        <FormRow label="忙闲状态">
          <select
            className="bg-transparent text-right font-medium text-[#22242a] outline-none"
            value={draft.availability}
            onChange={(event) =>
              onDraft("availability", event.target.value as Availability)
            }
          >
            <option value="busy">{AVAILABILITY_LABELS.busy}</option>
            <option value="free">{AVAILABILITY_LABELS.free}</option>
            <option value="tentative">{AVAILABILITY_LABELS.tentative}</option>
          </select>
        </FormRow>
        <FormRow label="私密">
          <button
            className={`h-[18px] w-[32px] rounded-full ${
              draft.isPrivate ? "bg-[#007aff]" : "bg-[#d1d1d6]"
            }`}
            onClick={() => onDraft("isPrivate", !draft.isPrivate)}
          />
        </FormRow>
      </div>
    </div>
  )
}

function EventDetails({
  calendar,
  event,
  onClose,
  onDelete,
  onEdit,
  onToast,
}: {
  calendar?: CalendarList
  event: CalendarOccurrence
  onClose: () => void
  onDelete: (scope: RecurrenceScope) => void
  onEdit: () => void
  onToast: (message: string) => void
}) {
  const [scope, setScope] = useState<RecurrenceScope>("series")
  const color = event.color ?? calendar?.color ?? "#007aff"

  return (
    <div className="absolute top-[118px] right-[34px] z-20 w-[286px] rounded-[16px] border border-black/10 bg-white/96 p-[14px] shadow-[0_22px_54px_rgba(0,0,0,0.2)] backdrop-blur-[24px]">
      <div className="flex items-start gap-[10px]">
        <span
          className="mt-[4px] size-[11px] rounded-full"
          style={{ backgroundColor: color }}
        />
        <div className="min-w-0 flex-1">
          <h3 className="text-[15px] font-semibold">
            {localizeKnownText(event.title)}
          </h3>
          <p className="mt-[3px] text-[12px] text-[#6f727a]">
            {formatShortDate(event.occurrenceStart)}{" "}
            {formatTime(new Date(event.occurrenceStart))} -{" "}
            {formatTime(new Date(event.occurrenceEnd))}
          </p>
        </div>
        <button
          type="button"
          aria-label="关闭详情"
          className="flex size-[26px] shrink-0 items-center justify-center rounded-full text-[#8b8e96] transition hover:bg-black/6 hover:text-[#303136]"
          onClick={onClose}
        >
          <X size={14} />
        </button>
      </div>
      <div className="mt-[13px] space-y-[9px] text-[12px] text-[#4f535b]">
        <div className="flex items-center gap-[8px]">
          <Repeat size={14} className="text-[#8b8e96]" />
          {event.repeat.frequency === "none"
            ? "不重复"
            : `${REPEAT_LABELS[event.repeat.frequency]}，可编辑此事件或此后所有事件`}
        </div>
        <div className="flex items-center gap-[8px]">
          <MapPin size={14} className="text-[#8b8e96]" />
          {event.location || "无地点"}
        </div>
        <div className="h-[74px] rounded-[10px] bg-[linear-gradient(135deg,#dfeefc,#d7f0dd_48%,#f5e6cd)] p-[9px] text-[11px] text-[#39505f]">
          地图预览 - {localizeKnownText(event.travelTime || "无")} - 可导航
        </div>
        <button
          className="flex items-center gap-[8px]"
          onClick={() => onToast("会议已打开")}
        >
          <Video size={14} className="text-[#007aff]" />
          加入视频会议
        </button>
        <div className="flex items-center gap-[8px]">
          <Users size={14} className="text-[#8b8e96]" />
          {event.invitees.length
            ? event.invitees
                .map(
                  (invitee) =>
                    `${invitee.name} ${RSVP_LABELS[invitee.status] ?? invitee.status}`
                )
                .join(" / ")
            : "无参与人"}
        </div>
        <button
          className="flex items-center gap-[8px]"
          onClick={() => onToast("链接已打开")}
        >
          <Link size={14} className="text-[#8b8e96]" />
          {event.url || "无链接"}
        </button>
        <div className="flex items-center gap-[8px]">
          <Paperclip size={14} className="text-[#8b8e96]" />
          {event.attachments.join(", ") || "无附件"}
        </div>
        {event.repeat.frequency !== "none" && (
          <select
            className="h-[28px] w-full rounded-[7px] border border-black/8 bg-[#f7f7f8] px-[8px] text-[12px] outline-none"
            value={scope}
            onChange={(changeEvent) =>
              setScope(changeEvent.target.value as RecurrenceScope)
            }
          >
            <option value="this">仅此事件</option>
            <option value="future">此后所有事件</option>
            <option value="series">整个重复系列</option>
          </select>
        )}
        <div className="flex gap-[8px] pt-[4px]">
          <button
            className="h-[30px] flex-1 rounded-[8px] bg-[#007aff] text-[12px] font-semibold text-white"
            onClick={onEdit}
          >
            编辑
          </button>
          <button
            className="h-[30px] flex-1 rounded-[8px] bg-[#ff3b30]/10 text-[12px] font-semibold text-[#d70015]"
            onClick={() => onDelete(scope)}
          >
            删除
          </button>
        </div>
        <div className="grid grid-cols-3 gap-[6px]">
          {(["accepted", "tentative", "declined"] as const).map((status) => (
            <button
              key={status}
              className="rounded-[7px] bg-[#f1f2f4] py-[5px] text-[11px]"
              onClick={() => onToast(`已回复：${RSVP_LABELS[status]}`)}
            >
              {RSVP_LABELS[status]}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

function NotificationPreview({
  onDismiss,
  onToast,
}: {
  onDismiss: () => void
  onToast: (message: string) => void
}) {
  return (
    <div className="absolute right-[24px] bottom-[18px] z-10 flex w-[360px] items-start gap-[12px] rounded-[16px] border border-black/10 bg-white/92 p-[13px] shadow-[0_18px_48px_rgba(0,0,0,0.2)] backdrop-blur-[24px]">
      <div className="flex size-[42px] shrink-0 items-center justify-center rounded-[10px] bg-[#f2f2f7] text-[#ff3b30]">
        <CalendarDays size={25} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between">
          <h3 className="text-[13px] font-semibold">日历</h3>
          <span className="text-[11px] text-[#8d9098]">刚刚</span>
        </div>
        <p className="mt-[2px] text-[12px] text-[#3b3d43]">
          飞往 SFO 即将开始
        </p>
        <div className="mt-[8px] flex gap-[7px]">
          <button
            className="rounded-[6px] bg-[#f1f2f4] px-[8px] py-[4px] text-[11px] text-[#2e3035]"
            onClick={() => onToast("已稍后提醒 10 分钟")}
          >
            稍后提醒
          </button>
          <button
            className="rounded-[6px] bg-[#f1f2f4] px-[8px] py-[4px] text-[11px] text-[#2e3035]"
            onClick={() => onToast("路线已打开")}
          >
            路线
          </button>
          <button
            className="rounded-[6px] bg-[#f1f2f4] px-[8px] py-[4px] text-[11px] text-[#2e3035]"
            onClick={() => onToast("已加入会议")}
          >
            加入
          </button>
          <button
            className="rounded-[6px] bg-[#f1f2f4] px-[8px] py-[4px] text-[11px] text-[#2e3035]"
            onClick={onDismiss}
          >
            忽略
          </button>
        </div>
      </div>
    </div>
  )
}

function SearchResults({
  calendarById,
  onJump,
  results,
  visible,
}: {
  calendarById: Map<string, CalendarList>
  onJump: (event: CalendarOccurrence) => void
  results: CalendarOccurrence[]
  visible: boolean
}) {
  if (!visible) {
    return null
  }

  return (
    <div className="absolute top-[590px] left-[24px] z-20 w-[252px] rounded-[13px] border border-black/9 bg-white/94 p-[12px] shadow-[0_16px_38px_rgba(0,0,0,0.16)]">
      <div className="mb-[9px] flex items-center gap-[7px] text-[13px] font-semibold">
        <Search size={14} className="text-[#8b8e96]" />
        搜索结果
      </div>
      {results.length === 0 && (
        <div className="px-[8px] py-[6px] text-[12px] text-[#757983]">
          未找到事件
        </div>
      )}
      {results.map((event) => (
        <button
          key={event.occurrenceId}
          className="w-full rounded-[8px] px-[8px] py-[6px] text-left text-[12px] hover:bg-[#f2f6ff]"
          onClick={() => onJump(event)}
        >
          <div className="font-medium text-[#1d1d1f]">
            {localizeKnownText(event.title)}
          </div>
          <div className="mt-[1px] text-[#757983]">
            {formatShortDate(event.occurrenceStart)} -{" "}
            {localizeKnownText(calendarById.get(event.calendarId)?.name ?? "日历")} -{" "}
            {event.location || "无地点"}
          </div>
        </button>
      ))}
    </div>
  )
}

function FormRow({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="flex items-center justify-between gap-[10px]">
      <span className="shrink-0">{label}</span>
      {children}
    </div>
  )
}

function MenuButton({
  children,
  onClick,
}: {
  children: ReactNode
  onClick: () => void
}) {
  return (
    <button
      className="block w-full rounded-[7px] px-[8px] py-[5px] text-left text-[12px] text-[#2f3238] hover:bg-[#e8f1ff]"
      onClick={onClick}
    >
      {children}
    </button>
  )
}

function MenuPanel({
  children,
  className,
}: {
  children: ReactNode
  className: string
}) {
  return (
    <div
      className={`absolute z-30 rounded-[11px] border border-black/10 bg-white/96 p-[5px] shadow-[0_16px_42px_rgba(0,0,0,0.18)] backdrop-blur-[20px] ${className}`}
      data-window-no-drag
    >
      {children}
    </div>
  )
}

function TextInput({
  label,
  onChange,
  value,
}: {
  label: string
  onChange: (value: string) => void
  value: string
}) {
  return (
    <FormRow label={label}>
      <input
        className="min-w-0 flex-1 bg-transparent text-right font-medium text-[#22242a] outline-none"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </FormRow>
  )
}

function TextSelect({
  label,
  onChange,
  value,
  values,
}: {
  label: string
  onChange: (value: string) => void
  value: string
  values: string[]
}) {
  return (
    <FormRow label={label}>
      <select
        className="bg-transparent text-right font-medium text-[#22242a] outline-none"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {values.map((item) => (
          <option key={item} value={item}>
            {localizeKnownText(item)}
          </option>
        ))}
      </select>
    </FormRow>
  )
}

function draftToInput(draft: DraftEvent): CalendarEventInput {
  return {
    alert: draft.alert,
    allDay: draft.allDay,
    attachments: draft.attachments,
    availability: draft.availability,
    calendarId: draft.calendarId,
    color: draft.color,
    end: draft.end,
    invitees: draft.invitees,
    isPrivate: draft.isPrivate,
    location: draft.location,
    meetingLink: draft.meetingLink,
    notes: draft.notes,
    reminder: draft.reminder,
    repeat: draft.repeat,
    secondAlert: draft.secondAlert,
    start: draft.start,
    timeZone: draft.timeZone,
    title: draft.title,
    travelTime: draft.travelTime,
    url: draft.url,
  }
}

function nextColor(color: string) {
  const index = CALENDAR_COLORS.indexOf(color)
  return CALENDAR_COLORS[(index + 1) % CALENDAR_COLORS.length]
}

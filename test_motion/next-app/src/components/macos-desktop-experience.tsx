import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from "react"
import { AnimatePresence, motion } from "framer-motion"
import {
  Activity,
  BatteryCharging,
  Bell,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Circle,
  CloudSun,
  CornerUpLeft,
  Clock3,
  Folder,
  Image,
  Link,
  MapPin,
  Mic,
  MoreHorizontal,
  PanelLeft,
  Paperclip,
  Plus,
  Radio,
  Repeat,
  Search,
  Send,
  Settings,
  SlidersHorizontal,
  UtensilsCrossed,
  Users,
  Video,
  Wifi,
} from "lucide-react"

import { CalendarAppWindow } from "@/components/calendar-app"
import {
  getDesktopState,
  saveDesktopWindowState,
  type RemoteDesktopWindowState,
} from "@/components/desktop-api"
import { FitnessActivityWidget } from "@/components/fitness-activity-widget"
import { MotionAppWindow } from "@/components/motion-app"
import { SpotifyCard, type MusicSearchCommand } from "@/components/spotify-card"
import { MinimalStepsCard } from "@/components/ui/minimal-steps-card"
import { WeatherAppWindow } from "@/components/weather-app"
import { WebRtcTestWindow } from "@/components/webrtc-test-window"
import { MotionWebRtcProvider } from "@/hooks/use-motion-webrtc"

const DESIGN_WIDTH = 1600
const MENU_BAR_HEIGHT = 36

type WindowId = "calendar" | "motion" | "weather" | "webrtcTest"

type WindowBounds = {
  height: number
  width: number
  x: number
  y: number
}

type DesktopWindowState = WindowBounds & {
  maximized: boolean
  minimized: boolean
  open: boolean
  restoreBounds: WindowBounds | null
  zIndex: number
}

const defaultWindowBounds: Record<WindowId, WindowBounds> = {
  calendar: { height: 792, width: 1344, x: 128, y: 76 },
  motion: { height: 760, width: 1160, x: 220, y: 92 },
  weather: { height: 830, width: 1380, x: 110, y: 58 },
  webrtcTest: { height: 640, width: 960, x: 300, y: 118 },
}

const initialWindowZIndex: Record<WindowId, number> = {
  calendar: 9,
  motion: 8,
  weather: 7,
  webrtcTest: 6,
}

const persistedWindowIds = ["calendar", "motion", "weather"] as const satisfies
  readonly WindowId[]

const interactiveDragBlockerSelector =
  "button, input, textarea, select, a, [role='button'], [contenteditable='true'], [data-window-no-drag]"

const windowMotionTransition = {
  default: {
    type: "spring",
    stiffness: 430,
    damping: 38,
    mass: 0.9,
  },
  filter: {
    duration: 0.16,
    ease: "easeOut",
  },
  opacity: {
    duration: 0.16,
    ease: "easeOut",
  },
  scale: {
    type: "spring",
    stiffness: 520,
    damping: 34,
    mass: 0.75,
  },
} as const

type BrowserSpeechRecognitionEvent = {
  error?: string
  results: ArrayLike<
    ArrayLike<{ transcript: string }> & {
      isFinal?: boolean
    }
  >
}

type BrowserSpeechRecognition = {
  continuous: boolean
  interimResults: boolean
  lang: string
  onend: (() => void) | null
  onerror: ((event: BrowserSpeechRecognitionEvent) => void) | null
  onresult: ((event: BrowserSpeechRecognitionEvent) => void) | null
  onstart?: (() => void) | null
  start: () => void
  stop: () => void
}

type BrowserSpeechRecognitionConstructor = new () => BrowserSpeechRecognition

function getSpeechRecognitionConstructor():
  | BrowserSpeechRecognitionConstructor
  | null {
  if (typeof window === "undefined") {
    return null
  }

  const browserWindow = window as Window & {
    SpeechRecognition?: BrowserSpeechRecognitionConstructor
    webkitSpeechRecognition?: BrowserSpeechRecognitionConstructor
  }

  return (
    browserWindow.SpeechRecognition ??
    browserWindow.webkitSpeechRecognition ??
    null
  )
}

function extractMusicQuery(text: string) {
  const normalized = text
    .trim()
    .replace(/[。！？，,.!?]/g, " ")
    .replace(/\s+/g, " ")

  if (!normalized) {
    return null
  }

  const genericMusicRequestPatterns = [
    /^听歌$/i,
    /^想听歌$/i,
    /^播放音乐$/i,
    /^来点音乐$/i,
    /^放首歌$/i,
    /^来首歌$/i,
  ]

  if (genericMusicRequestPatterns.some((pattern) => pattern.test(normalized))) {
    return "热门歌曲"
  }

  const explicitPatterns = [
    /(?:我想听|想听|听一下|听听|听|播放一下|播放|放一下|放一首|放首|放|来一首|来首|来点|搜一下|搜索一下|搜索|帮我放|给我放)\s*(.+)$/i,
    /(?:网易云|云音乐)\s*(?:搜索|播放)?\s*(.+)$/i,
  ]

  for (const pattern of explicitPatterns) {
    const match = normalized.match(pattern)
    if (!match) {
      continue
    }

    const query = match[1]
      .replace(/^(一首|首|一下|点|个)\s*/i, "")
      .replace(/\s*(这首歌|这首|歌曲)$/i, "")
      .trim()

    if (query && query !== "歌" && query !== "音乐") {
      return query
    }
  }

  if (normalized.includes("的歌")) {
    const [artist] = normalized.split("的歌")
    const query = artist.trim()
    if (query) {
      return `${query} 的歌`
    }
  }

  return null
}

function joinVoiceInput(base: string, spoken: string) {
  const normalizedBase = base.trim()
  const normalizedSpoken = spoken.trim()

  if (!normalizedBase) {
    return normalizedSpoken
  }

  if (!normalizedSpoken) {
    return normalizedBase
  }

  return `${normalizedBase} ${normalizedSpoken}`.trim()
}

function getSpeechRecognitionErrorMessage(error?: string) {
  switch (error) {
    case "aborted":
      return ""
    case "audio-capture":
      return "未检测到可用麦克风，请检查设备权限。"
    case "network":
      return "语音识别服务连接失败，请确认网络可用，并优先使用 Chrome 浏览器。"
    case "no-speech":
      return "没有识别到语音，可以再试一次。"
    case "not-allowed":
    case "service-not-allowed":
      return "浏览器未授予麦克风权限，请先允许语音访问。"
    default:
      return "语音输入暂时不可用，请稍后重试。"
  }
}

function createWindowState(id: WindowId, open: boolean): DesktopWindowState {
  return {
    ...defaultWindowBounds[id],
    maximized: false,
    minimized: false,
    open,
    restoreBounds: null,
    zIndex: initialWindowZIndex[id],
  }
}

function createInitialWindowState(): Record<WindowId, DesktopWindowState> {
  return {
    calendar: createWindowState("calendar", true),
    motion: createWindowState("motion", false),
    weather: createWindowState("weather", false),
    webrtcTest: createWindowState("webrtcTest", false),
  }
}

function normalizeWindowsForDesktop(
  currentWindows: Record<WindowId, DesktopWindowState>,
  desktopHeight: number
): Record<WindowId, DesktopWindowState> {
  let changed = false
  const nextWindows = { ...currentWindows }

  for (const id of Object.keys(currentWindows) as WindowId[]) {
    const currentWindow = currentWindows[id]
    const nextBounds = currentWindow.maximized
      ? getMaximizedBounds(desktopHeight)
      : clampWindowBounds(currentWindow, desktopHeight)

    if (
      currentWindow.x !== nextBounds.x ||
      currentWindow.y !== nextBounds.y ||
      currentWindow.width !== nextBounds.width ||
      currentWindow.height !== nextBounds.height
    ) {
      changed = true
      nextWindows[id] = {
        ...currentWindow,
        ...nextBounds,
      }
    }
  }

  return changed ? nextWindows : currentWindows
}

function isWindowId(value: string): value is WindowId {
  return value in defaultWindowBounds
}

function mergeRemoteWindowState(
  currentWindows: Record<WindowId, DesktopWindowState>,
  remoteWindows: RemoteDesktopWindowState[],
  desktopHeight: number
): {
  maxZIndex: number
  windows: Record<WindowId, DesktopWindowState>
} {
  let maxZIndex = 10
  const nextWindows = { ...currentWindows }

  for (const remoteWindow of remoteWindows) {
    if (!isWindowId(remoteWindow.appKey)) {
      continue
    }

    maxZIndex = Math.max(maxZIndex, remoteWindow.zIndex + 1)
    nextWindows[remoteWindow.appKey] = {
      ...nextWindows[remoteWindow.appKey],
      ...clampWindowBounds(
        {
          height: remoteWindow.height,
          width: remoteWindow.width,
          x: remoteWindow.x,
          y: remoteWindow.y,
        },
        desktopHeight
      ),
      maximized: remoteWindow.maximized,
      minimized: remoteWindow.minimized,
      open: remoteWindow.open,
      restoreBounds: remoteWindow.restoreBounds,
      zIndex: remoteWindow.zIndex,
    }
  }

  if (
    !Object.values(nextWindows).some(
      (windowState) => windowState.open && !windowState.minimized
    )
  ) {
    nextWindows.calendar = {
      ...createWindowState("calendar", true),
      zIndex: maxZIndex,
    }
    maxZIndex += 1
  }

  return { maxZIndex, windows: nextWindows }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function getMaximizedBounds(desktopHeight: number): WindowBounds {
  return {
    height: Math.max(240, desktopHeight - 160),
    width: DESIGN_WIDTH - 32,
    x: 16,
    y: 48,
  }
}

function clampWindowBounds(
  bounds: WindowBounds,
  desktopHeight: number
): WindowBounds {
  const maxX = Math.max(0, DESIGN_WIDTH - bounds.width)
  const maxY = Math.max(MENU_BAR_HEIGHT, desktopHeight - bounds.height)

  return {
    ...bounds,
    x: clamp(bounds.x, 0, maxX),
    y: clamp(bounds.y, MENU_BAR_HEIGHT, maxY),
  }
}

const dockApps = [
  {
    label: "Finder",
    icon: Folder,
    className: "bg-gradient-to-br from-[#73abff] to-[#437fe8]",
    iconClassName: "text-[#1f4f9b]",
    active: true,
  },
  {
    id: "calendar",
    label: "Calendar",
    icon: CalendarDays,
    className: "bg-gradient-to-b from-[#fefefe] to-[#e8e8ea]",
    iconClassName: "text-[#e3453f]",
    active: false,
  },
  {
    id: "weather",
    label: "Weather",
    icon: CloudSun,
    className:
      "bg-[linear-gradient(180deg,#79c2ff_0%,#5e8ef7_55%,#4466d8_100%)]",
    iconClassName: "text-white",
    active: false,
  },
  {
    label: "Recipes",
    icon: UtensilsCrossed,
    className:
      "bg-[linear-gradient(145deg,#ffb05b_0%,#ff8a4c_42%,#d95d39_100%)]",
    iconClassName: "text-white",
    active: false,
  },
  {
    id: "motion",
    label: "Motion",
    icon: Image,
    className: "bg-white",
    iconClassName: "text-white",
    active: false,
  },
  {
    id: "webrtcTest",
    label: "WebRTC Test",
    icon: Radio,
    className:
      "bg-[linear-gradient(145deg,#171f2c_0%,#0f766e_58%,#5eead4_100%)]",
    iconClassName: "text-white",
    active: false,
  },
  {
    label: "Photos",
    icon: Image,
    className: "bg-gradient-to-br from-[#a25a3d] to-[#5d243f]",
    iconClassName: "text-white",
    active: false,
  },
]

const monthCells = Array.from({ length: 35 }, (_, index) => {
  const currentMonth = index < 30
  const day = currentMonth ? index + 1 : index - 29

  return {
    currentMonth,
    day,
    weekend: index % 7 > 4,
    today: currentMonth && day === 1,
    selected: currentMonth && day === 18,
  }
})

const stepsChartData = [
  { value: 5200 },
  { value: 7400 },
  { value: 6100 },
  { value: 8800 },
  { value: 6900 },
  { value: 10320 },
  { value: 8420 },
  { value: 7600 },
  { value: 9400 },
  { value: 7100 },
  { value: 9800 },
  { value: 8240 },
]

type CalendarSource = {
  group: string
  items: {
    checked: boolean
    color: string
    name: string
    shared?: boolean
    subscribed?: boolean
  }[]
}

const calendarSources: CalendarSource[] = [
  {
    group: "iCloud",
    items: [
      { name: "Home", color: "#ff3b30", checked: true },
      { name: "Personal", color: "#007aff", checked: true },
      { name: "Family", color: "#34c759", checked: true, shared: true },
    ],
  },
  {
    group: "Work",
    items: [
      { name: "Product Team", color: "#ff9500", checked: true, shared: true },
      { name: "Launches", color: "#af52de", checked: true },
      { name: "Focus Blocks", color: "#5ac8fa", checked: false },
    ],
  },
  {
    group: "Other",
    items: [
      {
        name: "US Holidays",
        color: "#ffcc00",
        checked: true,
        subscribed: true,
      },
      { name: "Birthdays", color: "#ff2d55", checked: true },
    ],
  },
]

const calendarEvents: Record<
  number,
  { title: string; color: string; time?: string; reminder?: boolean }[]
> = {
  2: [{ title: "Daily standup", color: "#007aff", time: "9:30 AM" }],
  4: [{ title: "Design review", color: "#af52de", time: "11 AM" }],
  6: [{ title: "Family dinner", color: "#34c759", time: "7 PM" }],
  9: [
    { title: "Sprint planning", color: "#ff9500", time: "10 AM" },
    { title: "Call supplier", color: "#007aff", reminder: true },
  ],
  12: [{ title: "Product sync", color: "#ff9500", time: "2 PM" }],
  15: [{ title: "Renew subscription", color: "#8e8e93", reminder: true }],
  18: [
    { title: "Quarterly planning", color: "#007aff", time: "9 AM" },
    { title: "FaceTime with Maya", color: "#34c759", time: "1 PM" },
    { title: "Flight to SFO", color: "#ff3b30", time: "5 PM" },
    { title: "+3 more", color: "#8e8e93" },
  ],
  21: [{ title: "Dad birthday", color: "#ff2d55" }],
  24: [{ title: "Time to leave: Airport", color: "#ff3b30", time: "3:40 PM" }],
  27: [{ title: "Export monthly report", color: "#5ac8fa", reminder: true }],
}

function useDesktopScale() {
  const [viewport, setViewport] = useState(() => ({
    height: window.innerHeight,
    width: window.innerWidth,
  }))

  useEffect(() => {
    const updateViewport = () => {
      setViewport({
        height: window.innerHeight,
        width: window.innerWidth,
      })
    }

    updateViewport()
    window.addEventListener("resize", updateViewport)

    return () => window.removeEventListener("resize", updateViewport)
  }, [])

  const scale = viewport.width / DESIGN_WIDTH
  const desktopHeight = viewport.height / scale

  return {
    canvasHeight: viewport.height,
    desktopHeight,
    scale,
  }
}

function WindowControls({
  className = "",
  onClose,
  onMinimize,
  onToggleMaximize,
}: {
  className?: string
  onClose?: () => void
  onMinimize?: () => void
  onToggleMaximize?: () => void
}) {
  return (
    <div
      className={`flex items-center gap-[9px] ${className}`}
      data-window-no-drag
    >
      <button
        aria-label="Close window"
        className="size-[15px] rounded-full bg-[#ff5f57] p-0 shadow-[inset_0_0_0_1px_rgba(0,0,0,0.22)] transition-transform duration-100 active:scale-90"
        type="button"
        onClick={(event) => {
          event.stopPropagation()
          onClose?.()
        }}
      />
      <button
        aria-label="Minimize window"
        className="size-[15px] rounded-full bg-[#ffbd2e] p-0 shadow-[inset_0_0_0_1px_rgba(0,0,0,0.2)] transition-transform duration-100 active:scale-90"
        type="button"
        onClick={(event) => {
          event.stopPropagation()
          onMinimize?.()
        }}
      />
      <button
        aria-label="Toggle maximize window"
        className="size-[15px] rounded-full bg-[#28c840] p-0 shadow-[inset_0_0_0_1px_rgba(0,0,0,0.22)] transition-transform duration-100 active:scale-90"
        type="button"
        onClick={(event) => {
          event.stopPropagation()
          onToggleMaximize?.()
        }}
      />
    </div>
  )
}

function DraggableWindowFrame({
  children,
  desktopHeight,
  scale,
  state,
  onFocus,
  onMove,
}: {
  children: ReactNode
  desktopHeight: number
  scale: number
  state: DesktopWindowState
  onFocus: () => void
  onMove: (bounds: WindowBounds) => void
}) {
  const [dragging, setDragging] = useState(false)
  const visible = state.open && !state.minimized

  const handlePointerDown = (event: ReactPointerEvent<HTMLElement>) => {
    if (event.button !== 0) {
      return
    }

    onFocus()

    const target = event.target as HTMLElement
    const dragHandle = target.closest("[data-window-drag-handle]")
    const dragBlocked = target.closest(interactiveDragBlockerSelector)

    if (!dragHandle || dragBlocked || state.maximized) {
      return
    }

    event.preventDefault()
    setDragging(true)

    const startX = event.clientX
    const startY = event.clientY
    const startBounds: WindowBounds = {
      height: state.height,
      width: state.width,
      x: state.x,
      y: state.y,
    }

    const handlePointerMove = (moveEvent: PointerEvent) => {
      const deltaX = (moveEvent.clientX - startX) / scale
      const deltaY = (moveEvent.clientY - startY) / scale

      onMove(
        clampWindowBounds(
          {
            ...startBounds,
            x: startBounds.x + deltaX,
            y: startBounds.y + deltaY,
          },
          desktopHeight
        )
      )
    }

    const handlePointerUp = () => {
      document.removeEventListener("pointermove", handlePointerMove)
      document.removeEventListener("pointerup", handlePointerUp)
      document.body.style.cursor = ""
      document.body.style.userSelect = ""
      setDragging(false)
    }

    document.body.style.cursor = "default"
    document.body.style.userSelect = "none"
    document.addEventListener("pointermove", handlePointerMove)
    document.addEventListener("pointerup", handlePointerUp, { once: true })
  }

  return (
    <AnimatePresence>
      {visible && (
        <motion.section
          className="absolute"
          initial={{
            filter: "blur(8px)",
            height: state.height,
            left: state.x,
            opacity: 0,
            scale: 0.96,
            top: state.y,
            width: state.width,
            y: 18,
          }}
          animate={{
            filter: "blur(0px)",
            height: state.height,
            left: state.x,
            opacity: 1,
            scale: 1,
            top: state.y,
            width: state.width,
            y: 0,
          }}
          exit={{
            filter: "blur(7px)",
            opacity: 0,
            scale: 0.94,
            y: 24,
          }}
          transition={dragging ? { duration: 0 } : windowMotionTransition}
          style={{
            transformOrigin: "50% 100%",
            willChange: "left, top, width, height, transform, opacity, filter",
            zIndex: state.zIndex,
          }}
          onPointerDownCapture={handlePointerDown}
        >
          {children}
        </motion.section>
      )}
    </AnimatePresence>
  )
}

function TopMenuBar({
  activeApp = "File",
  onReturnToManual = () => undefined,
  onToggleAi,
}: {
  activeApp?: "Calendar" | "File" | "Motion" | "Weather" | "WebRTC"
  onReturnToManual?: () => void
  onToggleAi?: () => void
}) {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setNow(new Date())
    }, 1000)

    return () => window.clearInterval(intervalId)
  }, [])

  const menuBarDateTime = new Intl.DateTimeFormat("en-US", {
    hour: "numeric",
    minute: "2-digit",
    weekday: "short",
  }).format(now)

  return (
    <div className="absolute inset-x-0 top-0 z-[200] flex h-[36px] items-center border-b border-white/10 bg-[#111114]/88 px-[22px] text-[18px] font-medium text-[#e7e7ea] backdrop-blur-2xl">
      <div className="mr-[31px] flex size-[19px] items-center justify-center rounded-[3px] bg-[#050506] text-[4px] font-semibold tracking-[-0.02em] text-white/75">
        AM
      </div>
      <button
        className="mr-[24px] flex h-[24px] items-center gap-[7px] rounded-[6px] px-[8px] text-[15px] font-semibold text-white/86 transition-colors hover:bg-white/10 hover:text-white active:bg-white/14"
        type="button"
        onClick={onReturnToManual}
      >
        <CornerUpLeft size={15} strokeWidth={2.4} />
        Return
      </button>
      <div className="flex items-center gap-[28px]">
        {activeApp !== "File" && (
          <span className="font-bold text-white">{activeApp}</span>
        )}
        <span className={activeApp === "File" ? "font-bold text-white" : ""}>
          File
        </span>
        <span>Edit</span>
        <span>View</span>
        <span>Window</span>
        <span>Help</span>
      </div>
      <div className="ml-auto flex items-center gap-[24px] text-white/85">
        <button
          aria-label="Toggle AI Assistant"
          className="flex items-center justify-center transition-transform hover:scale-105 active:scale-95"
          type="button"
          onClick={onToggleAi}
        >
          <img
            src="/neurafit_logo_transparent_cropped.png"
            alt="AI"
            className="size-[20px] object-contain"
            style={{ filter: "brightness(0) invert(1)" }}
          />
        </button>
        <Wifi size={22} strokeWidth={2.4} />
        <BatteryCharging size={21} strokeWidth={2.1} />
        <SlidersHorizontal size={21} strokeWidth={2.1} />
        <span className="ml-[9px] text-[16px] font-semibold tracking-[-0.01em] text-white">
          {menuBarDateTime}
        </span>
      </div>
    </div>
  )
}

function CalendarWidget() {
  return (
    <section className="absolute top-[55px] left-[1160px] z-[2] flex w-[420px] origin-top-right scale-90 flex-col gap-[20px]">
      <FitnessActivityWidget />
      <MinimalStepsCard
        className="max-w-none"
        data={stepsChartData}
        icon={<Activity size={20} strokeWidth={2.1} aria-hidden="true" />}
        subtitle="6月17日"
        title="步数"
        totalSteps={8420}
      />
    </section>
  )
}

function MusicWidget({
  command,
  onCommandHandled,
}: {
  command: MusicSearchCommand | null
  onCommandHandled: (requestId: string) => void
}) {
  return (
    <section className="absolute top-[55px] left-[20px] z-[2] w-[320px] origin-top-left scale-90">
      <SpotifyCard command={command} onCommandHandled={onCommandHandled} />
    </section>
  )
}

function MiniMonthPicker() {
  return (
    <div className="rounded-[14px] border border-black/5 bg-white/55 p-[12px] shadow-[inset_0_1px_0_rgba(255,255,255,0.75)]">
      <div className="mb-[10px] flex items-center justify-between text-[13px] font-semibold text-[#1d1d1f]">
        <span>June 2026</span>
        <div className="flex gap-[8px] text-[#7a7a7d]">
          <ChevronLeft size={14} />
          <ChevronRight size={14} />
        </div>
      </div>
      <div className="grid grid-cols-7 gap-y-[7px] text-center text-[10px] font-medium text-[#8b8b90]">
        {["M", "T", "W", "T", "F", "S", "S"].map((day, index) => (
          <span key={`${day}-${index}`}>{day}</span>
        ))}
        {Array.from({ length: 30 }, (_, index) => {
          const day = index + 1
          return (
            <span
              key={day}
              className={`mx-auto flex size-[22px] items-center justify-center rounded-full text-[11px] ${
                day === 1
                  ? "bg-[#007aff] font-semibold text-white"
                  : day === 18
                    ? "bg-[#d7eaff] font-semibold text-[#005ac1]"
                    : "text-[#2f3034]"
              }`}
            >
              {day}
            </span>
          )
        })}
        {Array.from({ length: 5 }, (_, index) => (
          <span key={`next-${index}`} className="text-[#bebec3]">
            {index + 1}
          </span>
        ))}
      </div>
    </div>
  )
}

export function CalendarApplicationWindow() {
  return (
    <section className="absolute top-[76px] left-[128px] z-[9] h-[792px] w-[1344px] overflow-hidden rounded-[18px] border border-black/12 bg-[#f5f5f7] text-[#1d1d1f] shadow-[0_42px_110px_rgba(0,0,0,0.45)]">
      <div className="flex h-[62px] items-center border-b border-black/10 bg-[#f4f4f5]/88 px-[18px] backdrop-blur-[28px]">
        <WindowControls />
        <button className="ml-[24px] flex size-[32px] items-center justify-center rounded-[7px] text-[#5f6169] hover:bg-black/6">
          <PanelLeft size={18} />
        </button>
        <button className="ml-[12px] h-[32px] rounded-[7px] border border-black/10 bg-white/70 px-[13px] text-[13px] font-medium text-[#202124] shadow-[0_1px_1px_rgba(0,0,0,0.06)]">
          Today
        </button>
        <div className="ml-[10px] flex overflow-hidden rounded-[7px] border border-black/10 bg-white/60">
          <button className="flex size-[32px] items-center justify-center border-r border-black/8 text-[#60636c]">
            <ChevronLeft size={17} />
          </button>
          <button className="flex size-[32px] items-center justify-center text-[#60636c]">
            <ChevronRight size={17} />
          </button>
        </div>
        <h2 className="ml-[24px] text-[22px] font-semibold tracking-[-0.02em] text-[#1d1d1f]">
          June 2026
        </h2>
        <div className="ml-auto flex h-[32px] overflow-hidden rounded-[8px] border border-black/10 bg-[#e8e8ea] p-[2px] text-[13px] font-medium text-[#4c4d52]">
          {["Day", "Week", "Month", "Year"].map((view) => (
            <button
              key={view}
              className={`rounded-[6px] px-[13px] ${
                view === "Month"
                  ? "bg-white text-[#1d1d1f] shadow-[0_1px_3px_rgba(0,0,0,0.16)]"
                  : ""
              }`}
            >
              {view}
            </button>
          ))}
        </div>
        <div className="ml-[14px] flex h-[32px] w-[184px] items-center rounded-[8px] border border-black/10 bg-white/72 px-[10px] text-[#8a8d95]">
          <Search size={15} />
          <span className="ml-[7px] text-[13px]">Search</span>
        </div>
        <button className="ml-[10px] flex size-[32px] items-center justify-center rounded-[8px] bg-[#007aff] text-white shadow-[0_4px_10px_rgba(0,122,255,0.24)]">
          <Plus size={19} />
        </button>
        <button className="ml-[8px] flex size-[32px] items-center justify-center rounded-[8px] text-[#60636c] hover:bg-black/6">
          <MoreHorizontal size={19} />
        </button>
      </div>

      <div className="flex h-[730px]">
        <aside className="w-[260px] border-r border-black/10 bg-[#ececef]/72 p-[14px] backdrop-blur-[24px]">
          <MiniMonthPicker />
          <div className="mt-[16px] flex items-center justify-between px-[4px]">
            <span className="text-[12px] font-semibold tracking-[0.08em] text-[#7b7d85] uppercase">
              Calendars
            </span>
            <button className="flex size-[22px] items-center justify-center rounded-full text-[#007aff] hover:bg-white/70">
              <Plus size={15} />
            </button>
          </div>
          <div className="mt-[8px] space-y-[14px]">
            {calendarSources.map((source) => (
              <div key={source.group}>
                <div className="px-[4px] text-[12px] font-semibold text-[#8a8d95]">
                  {source.group}
                </div>
                <div className="mt-[6px] space-y-[2px]">
                  {source.items.map((item) => (
                    <div
                      key={item.name}
                      className="flex h-[29px] items-center rounded-[7px] px-[6px] text-[13px] text-[#292a2f] hover:bg-white/65"
                    >
                      {item.checked ? (
                        <span
                          className="mr-[8px] flex size-[12px] rounded-full"
                          style={{ backgroundColor: item.color }}
                        />
                      ) : (
                        <Circle size={12} className="mr-[8px] text-[#b4b6bd]" />
                      )}
                      <span className="min-w-0 flex-1 truncate">
                        {item.name}
                      </span>
                      {item.shared && (
                        <Users size={13} className="ml-[5px] text-[#8c8f98]" />
                      )}
                      {item.subscribed && (
                        <Repeat size={13} className="ml-[5px] text-[#8c8f98]" />
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
          <div className="mt-[18px] rounded-[12px] border border-black/7 bg-white/56 p-[10px] text-[12px] text-[#646770]">
            <div className="mb-[8px] flex items-center gap-[7px] font-semibold text-[#3b3d43]">
              <Bell size={14} className="text-[#ff9500]" />
              Time to Leave
            </div>
            Airport traffic is heavier than usual. Leave by 3:40 PM.
          </div>
        </aside>

        <div className="relative flex-1 bg-[#fbfbfc]">
          <div className="grid h-[36px] grid-cols-7 border-b border-black/10 bg-[#f7f7f8] text-center text-[12px] font-semibold tracking-[0.04em] text-[#777a82] uppercase">
            {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((day) => (
              <div key={day} className="flex items-center justify-center">
                {day}
              </div>
            ))}
          </div>
          <div className="grid h-[694px] grid-cols-7 grid-rows-5">
            {monthCells.map((cell) => {
              const events = cell.currentMonth
                ? (calendarEvents[cell.day] ?? [])
                : []
              return (
                <div
                  key={`${cell.currentMonth ? "current" : "next"}-${cell.day}`}
                  className={`relative border-r border-b border-black/8 p-[7px] ${
                    cell.weekend ? "bg-[#f5f6f8]" : "bg-white"
                  }`}
                >
                  <div
                    className={`ml-auto flex size-[25px] items-center justify-center rounded-full text-[13px] font-medium ${
                      cell.today
                        ? "bg-[#007aff] text-white"
                        : cell.selected
                          ? "bg-[#cfe5ff] text-[#005ac1]"
                          : cell.currentMonth
                            ? "text-[#303136]"
                            : "text-[#b7bac2]"
                    }`}
                  >
                    {cell.day}
                  </div>
                  <div className="mt-[5px] space-y-[4px]">
                    {events.map((event) => (
                      <div
                        key={`${cell.day}-${event.title}`}
                        className={`flex h-[20px] items-center rounded-[5px] px-[6px] text-[11px] font-medium ${
                          event.reminder
                            ? "border border-black/9 bg-white text-[#4b4d55]"
                            : "text-white"
                        }`}
                        style={
                          event.reminder
                            ? undefined
                            : { backgroundColor: event.color }
                        }
                      >
                        {event.reminder && (
                          <Circle
                            size={8}
                            className="mr-[5px]"
                            style={{ color: event.color }}
                          />
                        )}
                        <span className="truncate">
                          {event.time ? `${event.time} ` : ""}
                          {event.title}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>

          <div className="absolute top-[142px] left-[456px] w-[342px] rounded-[16px] border border-black/10 bg-white/95 p-[14px] shadow-[0_24px_64px_rgba(0,0,0,0.24)] backdrop-blur-[24px]">
            <div className="mb-[10px] flex items-center justify-between">
              <h3 className="text-[15px] font-semibold">New Event</h3>
              <button className="rounded-[6px] bg-[#007aff] px-[10px] py-[4px] text-[12px] font-semibold text-white">
                Add
              </button>
            </div>
            <input
              value="Project planning"
              readOnly
              className="h-[34px] w-full rounded-[8px] border border-black/10 bg-[#f7f7f8] px-[10px] text-[13px] ring-[#007aff]/30 outline-none focus:ring-3"
            />
            <div className="mt-[10px] space-y-[7px] text-[12px] text-[#4d5057]">
              <div className="flex items-center justify-between">
                <span>Calendar</span>
                <span className="rounded-full bg-[#dbeafe] px-[9px] py-[3px] text-[#005ac1]">
                  Product Team
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span>All-day</span>
                <span className="h-[18px] w-[32px] rounded-full bg-[#d1d1d6]" />
              </div>
              {[
                ["Starts", "Jun 18, 9:00 AM"],
                ["Ends", "Jun 18, 10:30 AM"],
                ["Repeat", "Weekly"],
                ["Alert", "10 minutes before"],
                ["Second alert", "Time to leave"],
                ["Travel time", "25 minutes"],
                ["Time zone", "Pacific Time"],
                ["Availability", "Busy"],
              ].map(([label, value]) => (
                <div key={label} className="flex items-center justify-between">
                  <span>{label}</span>
                  <span className="font-medium text-[#22242a]">{value}</span>
                </div>
              ))}
              <div className="grid grid-cols-2 gap-[7px] pt-[3px]">
                {[
                  "Location",
                  "Invitees",
                  "FaceTime",
                  "Notes",
                  "URL",
                  "Attach",
                ].map((item) => (
                  <button
                    key={item}
                    className="h-[28px] rounded-[7px] border border-black/8 bg-[#f7f7f8] text-[#3a3d44]"
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="absolute top-[118px] right-[34px] w-[286px] rounded-[16px] border border-black/10 bg-white/96 p-[14px] shadow-[0_22px_54px_rgba(0,0,0,0.2)] backdrop-blur-[24px]">
            <div className="flex items-start gap-[10px]">
              <span className="mt-[4px] size-[11px] rounded-full bg-[#007aff]" />
              <div>
                <h3 className="text-[15px] font-semibold">
                  Quarterly planning
                </h3>
                <p className="mt-[3px] text-[12px] text-[#6f727a]">
                  Thu, Jun 18, 9:00 AM - 10:30 AM
                </p>
              </div>
            </div>
            <div className="mt-[13px] space-y-[9px] text-[12px] text-[#4f535b]">
              <div className="flex items-center gap-[8px]">
                <Repeat size={14} className="text-[#8b8e96]" />
                Weekly, edit this event or all future events
              </div>
              <div className="flex items-center gap-[8px]">
                <MapPin size={14} className="text-[#8b8e96]" />1 Infinite Loop,
                Cupertino
              </div>
              <div className="h-[74px] rounded-[10px] bg-[linear-gradient(135deg,#dfeefc,#d7f0dd_48%,#f5e6cd)] p-[9px] text-[11px] text-[#39505f]">
                Map preview · 25 min drive · directions available
              </div>
              <div className="flex items-center gap-[8px]">
                <Video size={14} className="text-[#007aff]" />
                Join video meeting
              </div>
              <div className="flex items-center gap-[8px]">
                <Users size={14} className="text-[#8b8e96]" />
                Maya accepted · Ben tentative · Ari declined
              </div>
              <div className="flex items-center gap-[8px]">
                <Link size={14} className="text-[#8b8e96]" />
                roadmap.example.com
              </div>
              <div className="flex items-center gap-[8px]">
                <Paperclip size={14} className="text-[#8b8e96]" />
                agenda.pdf
              </div>
              <div className="flex gap-[8px] pt-[4px]">
                <button className="h-[30px] flex-1 rounded-[8px] bg-[#007aff] text-[12px] font-semibold text-white">
                  Edit
                </button>
                <button className="h-[30px] flex-1 rounded-[8px] bg-[#ff3b30]/10 text-[12px] font-semibold text-[#d70015]">
                  Delete
                </button>
              </div>
            </div>
          </div>

          <div className="absolute right-[24px] bottom-[18px] flex w-[360px] items-start gap-[12px] rounded-[16px] border border-black/10 bg-white/92 p-[13px] shadow-[0_18px_48px_rgba(0,0,0,0.2)] backdrop-blur-[24px]">
            <div className="flex size-[42px] shrink-0 items-center justify-center rounded-[10px] bg-[#f2f2f7] text-[#ff3b30]">
              <CalendarDays size={25} />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <h3 className="text-[13px] font-semibold">Calendar</h3>
                <span className="text-[11px] text-[#8d9098]">now</span>
              </div>
              <p className="mt-[2px] text-[12px] text-[#3b3d43]">
                Flight to SFO starts soon
              </p>
              <div className="mt-[8px] flex gap-[7px]">
                {["Snooze", "Directions", "Join", "Dismiss"].map((item) => (
                  <button
                    key={item}
                    className="rounded-[6px] bg-[#f1f2f4] px-[8px] py-[4px] text-[11px] text-[#2e3035]"
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="absolute top-[590px] left-[24px] w-[252px] rounded-[13px] border border-black/9 bg-white/94 p-[12px] shadow-[0_16px_38px_rgba(0,0,0,0.16)]">
            <div className="mb-[9px] flex items-center gap-[7px] text-[13px] font-semibold">
              <Search size={14} className="text-[#8b8e96]" />
              Search Results
            </div>
            {[
              ["Design review", "Jun 4 · Work · Room 3A"],
              ["Product sync", "Jun 12 · Launches · Zoom"],
              ["Dad birthday", "Jun 21 · Family"],
            ].map(([title, meta]) => (
              <div
                key={title}
                className="rounded-[8px] px-[8px] py-[6px] text-[12px] hover:bg-[#f2f6ff]"
              >
                <div className="font-medium text-[#1d1d1f]">{title}</div>
                <div className="mt-[1px] text-[#757983]">{meta}</div>
              </div>
            ))}
          </div>

          <div className="absolute bottom-[20px] left-[298px] flex items-center gap-[8px] rounded-full border border-black/8 bg-white/86 px-[12px] py-[7px] text-[12px] text-[#5d6068] shadow-[0_10px_26px_rgba(0,0,0,0.12)]">
            <Clock3 size={14} />
            Time zone: Floating · Pacific Time
            <span className="h-[14px] w-px bg-black/12" />
            Print · Import · Export · Subscribe
          </div>
        </div>
      </div>
    </section>
  )
}

function Dock({
  onActivateWindow,
  windowStates,
}: {
  onActivateWindow: (id: WindowId) => void
  windowStates: Record<WindowId, DesktopWindowState>
}) {
  return (
    <nav className="absolute bottom-[20px] left-1/2 z-[100] flex h-[83px] w-fit -translate-x-1/2 items-center rounded-[18px] border border-white/16 bg-[#1b1b20]/84 px-[14px] shadow-[0_24px_60px_rgba(0,0,0,0.44)] backdrop-blur-[28px]">
      <div className="flex items-center gap-[9px]">
        {dockApps.map((app) => {
          const Icon = app.icon
          const windowId = "id" in app ? (app.id as WindowId) : undefined
          const active = windowId
            ? windowStates[windowId].open || windowStates[windowId].minimized
            : app.active

          return (
            <button
              key={app.label}
              aria-label={app.label}
              onClick={windowId ? () => onActivateWindow(windowId) : undefined}
              className="group relative flex size-[58px] items-center justify-center"
            >
              <span
                className={`flex size-[58px] items-center justify-center rounded-[13px] border border-white/20 shadow-[0_7px_13px_rgba(0,0,0,0.34),inset_0_1px_0_rgba(255,255,255,0.28)] transition-transform group-hover:-translate-y-1 ${app.className}`}
              >
                {windowId === "calendar" ? (
                  <span className="relative flex size-[44px] flex-col overflow-hidden rounded-[8px] bg-white shadow-[inset_0_0_0_1px_rgba(0,0,0,0.16)]">
                    <span className="flex h-[13px] items-center justify-center bg-[#ff3b30] text-[7px] font-bold tracking-[0.08em] text-white">
                      JUN
                    </span>
                    <span className="flex flex-1 items-center justify-center text-[24px] leading-none font-semibold text-[#1d1d1f]">
                      1
                    </span>
                  </span>
                ) : windowId === "motion" ? (
                  <img
                    src="/neurafit_logo_transparent_cropped.png"
                    alt="Motion"
                    className="size-[44px] object-contain"
                    draggable={false}
                  />
                ) : (
                  <Icon
                    size={32}
                    strokeWidth={2.2}
                    className={app.iconClassName}
                  />
                )}
              </span>
              {active && (
                <span className="absolute bottom-[-7px] size-[4px] rounded-full bg-[#d8d8d9]" />
              )}
            </button>
          )
        })}
      </div>
      <div className="mx-[14px] h-[55px] w-px bg-white/15" />
      <button
        aria-label="System Settings"
        className="group relative flex size-[58px] items-center justify-center"
      >
        <span className="flex size-[58px] items-center justify-center rounded-[13px] border border-white/18 bg-gradient-to-br from-[#6d7d92] to-[#2d3644] shadow-[0_7px_13px_rgba(0,0,0,0.34),inset_0_1px_0_rgba(255,255,255,0.24)] transition-transform group-hover:-translate-y-1">
          <Settings size={33} strokeWidth={2.2} className="text-white" />
        </span>
      </button>
    </nav>
  )
}

function AiAssistantCard({
  onOpenApp,
  onPlayMusic,
}: {
  onOpenApp: (appId: WindowId) => void
  onPlayMusic: (query: string) => void
}) {
  const speechRecognitionSupported = Boolean(getSpeechRecognitionConstructor())
  const [transcript, setTranscript] = useState("")
  const [voiceStatus, setVoiceStatus] = useState<
    "idle" | "listening" | "requesting" | "error" | "unsupported"
  >(speechRecognitionSupported ? "idle" : "unsupported")
  const [voiceError, setVoiceError] = useState("")
  const [messages, setMessages] = useState<{ role: string; content: string }[]>(
    []
  )
  const [currentReasoning, setCurrentReasoning] = useState("")
  const [currentAnswer, setCurrentAnswer] = useState("")
  const [isGenerating, setIsGenerating] = useState(false)
  const recognitionRef = useRef<BrowserSpeechRecognition | null>(null)
  const shouldRestartListeningRef = useRef(false)
  const isStoppingRecognitionRef = useRef(false)
  const baseTranscriptRef = useRef("")
  const finalTranscriptRef = useRef("")
  const autoSendTimeoutRef = useRef<number | null>(null)
  const submitTranscriptRef = useRef<(text: string) => void>(() => undefined)
  const scrollRef = useRef<HTMLDivElement>(null)
  const isListening = voiceStatus === "listening" || voiceStatus === "requesting"

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages, currentReasoning, currentAnswer])

  const clearAutoSendTimeout = useCallback(() => {
    if (autoSendTimeoutRef.current != null) {
      window.clearTimeout(autoSendTimeoutRef.current)
      autoSendTimeoutRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!speechRecognitionSupported) {
      return
    }

    const SpeechRecognitionAPI = getSpeechRecognitionConstructor()
    if (!SpeechRecognitionAPI) {
      return
    }

    const recognition = new SpeechRecognitionAPI()
    recognitionRef.current = recognition

    recognition.continuous = false
    recognition.interimResults = true
    recognition.lang = "zh-CN"
    recognition.onstart = () => {
      setVoiceStatus("listening")
      setVoiceError("")
    }

    recognition.onresult = (event: BrowserSpeechRecognitionEvent) => {
      let finalTranscript = ""
      let interimTranscript = ""

      for (let index = 0; index < event.results.length; index++) {
        const result = event.results[index]
        const segment = result[0]?.transcript ?? ""
        if (!segment) {
          continue
        }
        if (result.isFinal) {
          finalTranscript += segment
        } else {
          interimTranscript += segment
        }
      }

      if (finalTranscript) {
        finalTranscriptRef.current = `${finalTranscriptRef.current}${finalTranscript}`
      }

      const mergedTranscript = joinVoiceInput(
        baseTranscriptRef.current,
        `${finalTranscriptRef.current}${interimTranscript}`
      )

      setTranscript(
        mergedTranscript
      )

      if (mergedTranscript.trim()) {
        clearAutoSendTimeout()
        autoSendTimeoutRef.current = window.setTimeout(() => {
          shouldRestartListeningRef.current = false
          isStoppingRecognitionRef.current = true
          recognition.stop()
          submitTranscriptRef.current(mergedTranscript.trim())
        }, 1200)
      }
    }

    recognition.onerror = (event: BrowserSpeechRecognitionEvent) => {
      console.warn("Speech recognition error:", event.error)

      if (event.error === "aborted" || isStoppingRecognitionRef.current) {
        isStoppingRecognitionRef.current = false
        return
      }

      const stableTranscript = joinVoiceInput(
        baseTranscriptRef.current,
        finalTranscriptRef.current
      ).trim()

      if (event.error === "network" && stableTranscript) {
        clearAutoSendTimeout()
        shouldRestartListeningRef.current = false
        submitTranscriptRef.current(stableTranscript)
        return
      }

      const nextMessage = getSpeechRecognitionErrorMessage(event.error)

      shouldRestartListeningRef.current = false
      setVoiceStatus(nextMessage ? "error" : "idle")
      setVoiceError(nextMessage)
    }

    recognition.onend = () => {
      isStoppingRecognitionRef.current = false
      if (!shouldRestartListeningRef.current) {
        setVoiceStatus((current) =>
          current === "requesting" || current === "listening" ? "idle" : current
        )
        return
      }

      try {
        recognition.start()
      } catch {
        shouldRestartListeningRef.current = false
        setVoiceStatus("error")
        setVoiceError("语音输入启动失败，请重新点击麦克风。")
      }
    }

    return () => {
      clearAutoSendTimeout()
      shouldRestartListeningRef.current = false
      isStoppingRecognitionRef.current = true
      recognition.stop()
      recognitionRef.current = null
    }
  }, [clearAutoSendTimeout, speechRecognitionSupported])

  const stopListening = useCallback(() => {
    clearAutoSendTimeout()
    shouldRestartListeningRef.current = false
    isStoppingRecognitionRef.current = true
    recognitionRef.current?.stop()
    setVoiceStatus("idle")
  }, [clearAutoSendTimeout])

  const startListening = useCallback(() => {
    if (!speechRecognitionSupported || !recognitionRef.current) {
      setVoiceStatus("unsupported")
      setVoiceError("当前浏览器不支持语音输入。")
      return
    }

    baseTranscriptRef.current = transcript
    finalTranscriptRef.current = ""
    clearAutoSendTimeout()
    isStoppingRecognitionRef.current = false
    shouldRestartListeningRef.current = true
    setVoiceError("")
    setVoiceStatus("requesting")

    try {
      recognitionRef.current.start()
    } catch (error) {
      shouldRestartListeningRef.current = false
      setVoiceStatus("error")
      setVoiceError("语音输入启动失败，请重新点击麦克风。")
      console.warn("Could not start speech recognition:", error)
    }
  }, [clearAutoSendTimeout, speechRecognitionSupported, transcript])

  const toggleListening = () => {
    if (isListening) {
      stopListening()
      return
    }

    startListening()
  }

  const handleSubmit = useCallback(async (text: string) => {
    if (!text.trim() || isGenerating) return

    // Stop listening if voice is active
    if (isListening) {
      stopListening()
    }

    const newMessages = [...messages, { role: "user", content: text }]
    setMessages(newMessages)
    setTranscript("")
    setCurrentReasoning("")
    setCurrentAnswer("")

    // 拦截应用打开指令
    const lowerText = text.toLowerCase()
    const musicQuery = extractMusicQuery(text)
    let targetApp: WindowId | null = null
    let responseText = ""

    if (musicQuery) {
      onPlayMusic(musicQuery)
      setMessages([
        ...newMessages,
        {
          role: "assistant",
          content: `好的，正在为你搜索并播放“${musicQuery}”。`,
        },
      ])
      return
    }

    if (lowerText.includes("打开日历") || lowerText.includes("看下日历") || lowerText.includes("日历")) {
      targetApp = "calendar"
      responseText = "好的，正在为您打开日历。"
    } else if (lowerText.includes("打开motion") || lowerText.includes("打开运动") || lowerText.includes("motion")) {
      targetApp = "motion"
      responseText = "没问题，已为您启动 Motion 运动应用。"
    } else if (lowerText.includes("打开天气") || lowerText.includes("看下天气") || lowerText.includes("天气")) {
      targetApp = "weather"
      responseText = "好的，为您展示天气信息。"
    } else if (lowerText.includes("打开webrtc") || lowerText.includes("打开测试") || lowerText.includes("webrtc")) {
      targetApp = "webrtcTest"
      responseText = "已为您打开 WebRTC 测试面板。"
    }

    if (targetApp) {
      onOpenApp(targetApp)
      setMessages([...newMessages, { role: "assistant", content: responseText }])
      return
    }

    setIsGenerating(true)

    try {
      const res = await fetch("/api/ai/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: newMessages })
      })

      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.message || "API request failed")
      }

      const reader = res.body?.getReader()
      if (!reader) throw new Error("No response body")

      const decoder = new TextDecoder()
      let buffer = ""
      let finalReasoning = ""
      let finalAnswer = ""

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ""

        for (const line of lines) {
          if (line.startsWith("data: ")) {
            const data = line.slice(6)
            if (data === "[DONE]") continue

            try {
              const parsed = JSON.parse(data)
              const delta = parsed.choices?.[0]?.delta

              if (delta?.reasoning_content) {
                finalReasoning += delta.reasoning_content
                setCurrentReasoning(finalReasoning)
              }
              if (delta?.content) {
                finalAnswer += delta.content
                setCurrentAnswer(finalAnswer)
              }
            } catch {
              // ignore parse error for incomplete chunks
            }
          }
        }
      }

      setMessages([...newMessages, {
        role: "assistant",
        content: finalAnswer || "No response"
      }])
    } catch (error) {
      console.error(error)
      setMessages([...newMessages, { role: "assistant", content: error instanceof Error ? error.message : "Error connecting to AI." }])
    } finally {
      setCurrentReasoning("")
      setCurrentAnswer("")
      setIsGenerating(false)
    }
  }, [currentAnswer, currentReasoning, isGenerating, isListening, messages, onOpenApp, onPlayMusic, stopListening])

  useEffect(() => {
    submitTranscriptRef.current = (text: string) => {
      void handleSubmit(text)
    }
  }, [handleSubmit])

  const assistantStatusText = speechRecognitionSupported
    ? isListening
      ? "我在听，请直接说。"
      : voiceError || "点击麦克风开始语音输入"
    : "当前浏览器不支持语音输入"

  return (
    <motion.div
      initial={{ opacity: 0, y: -20, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -10, scale: 0.95 }}
      transition={{ type: "spring", stiffness: 400, damping: 30 }}
      className="absolute left-1/2 top-[55px] z-[300] -translate-x-1/2 w-[420px] overflow-hidden rounded-[24px] border border-white/20 bg-black/40 p-5 text-white shadow-[0_32px_64px_rgba(0,0,0,0.4)] backdrop-blur-[40px] saturate-[1.2]"
    >
      <div className="mb-4 flex items-center gap-3">
        <div className="relative flex size-10 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 shadow-[inset_0_2px_4px_rgba(255,255,255,0.3)]">
          {isListening && (
            <div className="absolute inset-0 rounded-full animate-ping opacity-20 shadow-[0_0_15px_rgba(255,255,255,0.8)]" style={{ animationDuration: '3s' }} />
          )}
          <img
            src="/neurafit_logo_transparent_cropped.png"
            alt="AI"
            className="relative z-10 size-[20px] object-contain"
            style={{ filter: "brightness(0) invert(1)" }}
          />
        </div>
        <div className="flex-1">
          <h3 className="text-[15px] font-medium text-white/90">
            neurafitOS Assistant
          </h3>
          <p className="text-[12px] text-white/60">
            {assistantStatusText}
          </p>
        </div>
        <button
          aria-label={isListening ? "停止语音输入" : "开始语音输入"}
          onClick={toggleListening}
          disabled={!speechRecognitionSupported}
          className={`flex size-8 items-center justify-center rounded-full transition-colors ${
            isListening
              ? "bg-white/20 text-white"
              : "bg-white/10 text-white/60 hover:bg-white/15"
          }`}
          type="button"
        >
          <Mic size={14} />
        </button>
      </div>

      <div className="relative">
        {(messages.length > 0 || currentReasoning || currentAnswer) && (
          <div ref={scrollRef} className="max-h-[250px] overflow-y-auto mb-4 space-y-3 pr-2 text-[13px] scrollbar-thin scrollbar-thumb-white/10">
            {messages.map((msg, i) => (
              <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`rounded-2xl px-3 py-2 max-w-[85%] ${msg.role === 'user' ? 'bg-indigo-500/80 text-white' : 'bg-white/10 text-white/90'}`}>
                  {msg.content}
                </div>
              </div>
            ))}
            {(currentReasoning || currentAnswer) && (
              <div className="flex justify-start">
                <div className="rounded-2xl px-3 py-2 max-w-[85%] bg-white/10 text-white/90">
                  {currentReasoning && <div className="text-white/50 italic mb-1 text-[11px] whitespace-pre-wrap">{currentReasoning}</div>}
                  {currentAnswer && <div className="whitespace-pre-wrap">{currentAnswer}</div>}
                </div>
              </div>
            )}
          </div>
        )}

        <div className="relative flex items-center">
          <input
            autoFocus
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault()
                void handleSubmit(transcript)
              }
            }}
            placeholder={
              isListening ? "正在识别语音..." : "例如：播放周杰伦的稻香"
            }
            className="w-full rounded-[14px] border border-white/10 bg-white/10 px-4 py-3 pr-10 text-[14px] text-white placeholder:text-white/40 outline-none transition-all focus:border-white/20 focus:bg-white/15"
          />
          <button
            onClick={() => { void handleSubmit(transcript) }}
            disabled={!transcript.trim() || isGenerating}
            className="absolute right-2 flex size-7 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20 disabled:opacity-50"
          >
            <Send size={14} />
          </button>
        </div>
        
        {messages.length === 0 && !currentReasoning && !currentAnswer && (
          <div className="mt-4 flex flex-wrap gap-2">
            {["播放周杰伦的稻香", "帮我打开日历", "看下天气"].map(
              (suggestion) => (
                <button
                  key={suggestion}
                  onClick={() => { void handleSubmit(suggestion) }}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] text-white/70 transition-colors hover:bg-white/10 hover:text-white"
                  type="button"
                >
                  {suggestion}
                </button>
              )
            )}
          </div>
        )}
      </div>
      
      {/* 底部 Siri 风格动态光晕 */}
      <div className="absolute -bottom-[60px] left-1/2 h-[120px] w-[200px] -translate-x-1/2 rounded-full bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 opacity-20 blur-[40px] transition-all duration-700" 
           style={{
             transform: `translateX(-50%) scale(${isListening || isGenerating ? 1.2 : 0.8})`,
             opacity: isListening || isGenerating ? 0.3 : 0.1
           }}
      />
    </motion.div>
  )
}

export function MacosDesktopExperience({
  onReturnToManual,
}: {
  onReturnToManual?: () => void
}) {
  const autoOpenMotionRef = useRef(false)
  const { canvasHeight, desktopHeight, scale } = useDesktopScale()
  const [windows, setWindows] = useState(createInitialWindowState)
  const [desktopPersistenceReady, setDesktopPersistenceReady] = useState(false)
  const [isAiCardOpen, setIsAiCardOpen] = useState(false)
  const [musicCommand, setMusicCommand] = useState<MusicSearchCommand | null>(
    null
  )
  const zIndexRef = useRef(10)
  const normalizedWindows = useMemo(
    () => normalizeWindowsForDesktop(windows, desktopHeight),
    [desktopHeight, windows]
  )

  useEffect(() => {
    let cancelled = false

    getDesktopState()
      .then((desktopState) => {
        if (cancelled) {
          return
        }

        setWindows((currentWindows) => {
          const remoteState = mergeRemoteWindowState(
            currentWindows,
            desktopState.windows,
            desktopHeight
          )
          zIndexRef.current = remoteState.maxZIndex
          return remoteState.windows
        })
        setDesktopPersistenceReady(true)
      })
      .catch((error) => {
        console.warn("Desktop state API fallback:", error)
      })

    return () => {
      cancelled = true
    }
  }, [desktopHeight])

  const bringToFront = useCallback((id: WindowId) => {
    const nextZIndex = zIndexRef.current++

    setWindows((currentWindows) => ({
      ...currentWindows,
      [id]: {
        ...currentWindows[id],
        zIndex: nextZIndex,
      },
    }))
  }, [])

  const activateWindow = useCallback((id: WindowId) => {
    const nextZIndex = zIndexRef.current++

    setWindows((currentWindows) => {
      const currentWindow = currentWindows[id]
      const nextWindow = currentWindow.open
        ? currentWindow
        : createWindowState(id, true)

      return {
        ...currentWindows,
        [id]: {
          ...nextWindow,
          minimized: false,
          open: true,
          zIndex: nextZIndex,
        },
      }
    })
  }, [])

  const closeWindow = useCallback((id: WindowId) => {
    setWindows((currentWindows) => ({
      ...currentWindows,
      [id]: createWindowState(id, false),
    }))
  }, [])

  const minimizeWindow = useCallback((id: WindowId) => {
    setWindows((currentWindows) => ({
      ...currentWindows,
      [id]: {
        ...currentWindows[id],
        minimized: true,
      },
    }))
  }, [])

  const moveWindow = useCallback((id: WindowId, bounds: WindowBounds) => {
    setWindows((currentWindows) => ({
      ...currentWindows,
      [id]: {
        ...currentWindows[id],
        ...bounds,
        maximized: false,
        restoreBounds: null,
      },
    }))
  }, [])

  const toggleMaximizeWindow = useCallback(
    (id: WindowId) => {
      const nextZIndex = zIndexRef.current++

      setWindows((currentWindows) => {
        const currentWindow = currentWindows[id]

        if (currentWindow.maximized) {
          return {
            ...currentWindows,
            [id]: {
              ...currentWindow,
              ...clampWindowBounds(
                currentWindow.restoreBounds ?? defaultWindowBounds[id],
                desktopHeight
              ),
              maximized: false,
              restoreBounds: null,
              zIndex: nextZIndex,
            },
          }
        }

        return {
          ...currentWindows,
          [id]: {
            ...currentWindow,
            ...getMaximizedBounds(desktopHeight),
            maximized: true,
            minimized: false,
            open: true,
            restoreBounds: {
              height: currentWindow.height,
              width: currentWindow.width,
              x: currentWindow.x,
              y: currentWindow.y,
            },
            zIndex: nextZIndex,
          },
        }
      })
    },
    [desktopHeight]
  )

  const playMusic = useCallback((query: string) => {
    setMusicCommand({
      autoPlay: true,
      query,
      requestId: `music-${Date.now()}`,
    })
  }, [])

  const handleMusicCommandHandled = useCallback((requestId: string) => {
    setMusicCommand((current) =>
      current?.requestId === requestId ? null : current
    )
  }, [])

  useEffect(() => {
    if (!desktopPersistenceReady) {
      return
    }

    const timeoutId = window.setTimeout(() => {
      for (const appKey of persistedWindowIds) {
        const state = normalizedWindows[appKey]
        void saveDesktopWindowState(appKey, state).catch((error) => {
          console.warn("Desktop window state save failed:", error)
        })
      }
    }, 350)

    return () => window.clearTimeout(timeoutId)
  }, [desktopPersistenceReady, normalizedWindows])

  useEffect(() => {
    if (!desktopPersistenceReady || autoOpenMotionRef.current) {
      return
    }

    autoOpenMotionRef.current = true
    activateWindow("motion")
  }, [activateWindow, desktopPersistenceReady])

  const visibleWindows = (
    Object.entries(normalizedWindows) as [WindowId, DesktopWindowState][]
  ).filter(([, windowState]) => windowState.open && !windowState.minimized)
  const focusedWindow = visibleWindows.reduce<
    [WindowId, DesktopWindowState] | null
  >((topWindow, windowEntry) => {
    if (!topWindow || windowEntry[1].zIndex > topWindow[1].zIndex) {
      return windowEntry
    }

    return topWindow
  }, null)
  const activeApp =
    focusedWindow?.[0] === "calendar"
      ? "Calendar"
      : focusedWindow?.[0] === "motion"
        ? "Motion"
        : focusedWindow?.[0] === "weather"
          ? "Weather"
          : focusedWindow?.[0] === "webrtcTest"
            ? "WebRTC"
            : "File"

  return (
    <MotionWebRtcProvider>
      <main
        className="relative h-[100svh] w-screen overflow-hidden bg-[#0c0d12] text-white"
        style={{
          fontFamily:
            "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
          height: canvasHeight,
        }}
      >
        <div
          className="absolute top-0 left-1/2 overflow-hidden bg-[#101116]"
          style={{
            width: DESIGN_WIDTH,
            height: desktopHeight,
            transform: `translateX(-50%) scale(${scale})`,
            transformOrigin: "top center",
          }}
        >
          <div className="absolute inset-0 bg-[linear-gradient(180deg,#101116_0%,#0c0d12_54%,#0f1015_100%)]" />
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_53%_48%,rgba(54,61,78,0.24)_0%,rgba(20,22,30,0.2)_34%,rgba(7,8,12,0)_70%)]" />
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(255,255,255,0.04)_0%,rgba(255,255,255,0)_62%)]" />

          <TopMenuBar
            activeApp={activeApp}
            onReturnToManual={onReturnToManual}
            onToggleAi={() => setIsAiCardOpen((prev) => !prev)}
          />
          <AnimatePresence>
            {isAiCardOpen && (
              <AiAssistantCard
                onOpenApp={activateWindow}
                onPlayMusic={playMusic}
              />
            )}
          </AnimatePresence>
          <CalendarWidget />
          <MusicWidget
            command={musicCommand}
            onCommandHandled={handleMusicCommandHandled}
          />
          <DraggableWindowFrame
            desktopHeight={desktopHeight}
            scale={scale}
            state={normalizedWindows.calendar}
            onFocus={() => bringToFront("calendar")}
            onMove={(bounds) => moveWindow("calendar", bounds)}
          >
            <CalendarAppWindow
              windowControls={
                <WindowControls
                  onClose={() => closeWindow("calendar")}
                  onMinimize={() => minimizeWindow("calendar")}
                  onToggleMaximize={() => toggleMaximizeWindow("calendar")}
                />
              }
            />
          </DraggableWindowFrame>
          <DraggableWindowFrame
            desktopHeight={desktopHeight}
            scale={scale}
            state={normalizedWindows.motion}
            onFocus={() => bringToFront("motion")}
            onMove={(bounds) => moveWindow("motion", bounds)}
          >
            <MotionAppWindow
              windowControls={
                <WindowControls
                  onClose={() => closeWindow("motion")}
                  onMinimize={() => minimizeWindow("motion")}
                  onToggleMaximize={() => toggleMaximizeWindow("motion")}
                />
              }
            />
          </DraggableWindowFrame>
          <DraggableWindowFrame
            desktopHeight={desktopHeight}
            scale={scale}
            state={normalizedWindows.weather}
            onFocus={() => bringToFront("weather")}
            onMove={(bounds) => moveWindow("weather", bounds)}
          >
            <WeatherAppWindow
              windowControls={
                <WindowControls
                  onClose={() => closeWindow("weather")}
                  onMinimize={() => minimizeWindow("weather")}
                  onToggleMaximize={() => toggleMaximizeWindow("weather")}
                />
              }
            />
          </DraggableWindowFrame>
          <DraggableWindowFrame
            desktopHeight={desktopHeight}
            scale={scale}
            state={normalizedWindows.webrtcTest}
            onFocus={() => bringToFront("webrtcTest")}
            onMove={(bounds) => moveWindow("webrtcTest", bounds)}
          >
            <WebRtcTestWindow
              windowControls={
                <WindowControls
                  onClose={() => closeWindow("webrtcTest")}
                  onMinimize={() => minimizeWindow("webrtcTest")}
                  onToggleMaximize={() => toggleMaximizeWindow("webrtcTest")}
                />
              }
            />
          </DraggableWindowFrame>
          <Dock windowStates={normalizedWindows} onActivateWindow={activateWindow} />
        </div>
      </main>
    </MotionWebRtcProvider>
  )
}

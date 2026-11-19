import { Flame, Gauge, Radio, Sparkles } from "lucide-react"
import type { ChangeEvent, MutableRefObject, ReactNode } from "react"

import {
  type MotionCorrection,
  type MotionPoseFrame,
} from "@/hooks/use-motion-webrtc"
import { MotionBottomControl } from "@/components/motion/motion-bottom-control"
import { MotionRoutePanel } from "@/components/motion/motion-route-panel"

const backgroundImageUrl = "/motion-park-route-background.png"

type StatusChip = {
  label: string
  value: string
}

type MotionOutdoorDashboardProps = {
  calories: string
  confidence: string
  connect: () => void | Promise<void>
  corrections: MotionCorrection[]
  dataChannelState: RTCDataChannelState
  fps: number
  latencyMs: number
  poseFrame: MotionPoseFrame | null
  remoteStream: MediaStream | null
  roomId: string
  remoteMirrored: boolean
  setRoomId: (roomId: string) => void
  signalingState: string
  startDemo: () => void | Promise<void>
  statusLabel: string
  stop: () => void
  videoRef: MutableRefObject<HTMLVideoElement | null>
  windowControls?: ReactNode
}

function MotionStatusChip({ label, value }: StatusChip) {
  return (
    <div className="rounded-full border border-white/18 bg-black/24 px-[12px] py-[7px] text-[12px] font-medium text-white/82 shadow-[0_12px_30px_rgba(0,0,0,0.16)] backdrop-blur-[22px]">
      <span className="text-white/48">{label}</span>
      <span className="ml-[6px] text-white">{value}</span>
    </div>
  )
}

function GlassButton({
  children,
  onClick,
  primary = false,
}: {
  children: ReactNode
  onClick: () => void
  primary?: boolean
}) {
  return (
    <button
      className={`h-[34px] rounded-full border px-[14px] text-[13px] font-semibold text-white shadow-[0_14px_36px_rgba(0,0,0,0.18)] backdrop-blur-[22px] transition-transform duration-200 hover:scale-[1.03] active:scale-95 ${
        primary
          ? "border-white/28 bg-white/26"
          : "border-white/16 bg-black/24 text-white/84"
      }`}
      data-window-no-drag
      type="button"
      onClick={onClick}
    >
      {children}
    </button>
  )
}

function localizeCoachingText(text: string) {
  const normalized = text.trim()

  if (!normalized) {
    return "等待安卓端纠错提示..."
  }

  switch (normalized) {
    case "Squat is shallow. Keep sitting back.":
      return "下蹲不足，注意重心继续向后坐。"
    case "Core is swaying. Brace and keep control.":
      return "核心有些晃动，收紧腹部并保持控制。"
    case "Stable tempo. Knee tracking looks good.":
      return "动作节奏稳定，膝盖轨迹良好。"
    case "On the next rep, keep knees and toes moving in the same direction.":
      return "下一次动作时，膝盖和脚尖保持同向。"
    case "Keep the rhythm and stand up through the exhale.":
      return "保持节奏，起身时顺势呼气。"
    default:
      return normalized
  }
}

function CoachingOverlay({
  advice,
  correction,
}: {
  advice: string
  correction?: MotionCorrection
}) {
  const hasWarning = correction && correction.severity !== "info"

  return (
    <div
      className={`min-w-[420px] max-w-[680px] whitespace-pre-line rounded-[28px] border px-[28px] py-[18px] text-center text-[18px] leading-[1.45] font-semibold text-white shadow-[0_22px_54px_rgba(0,0,0,0.28)] backdrop-blur-[28px] ${
        hasWarning
          ? "border-[#ffd7d7]/26 bg-[#7b1717]/62"
          : "border-white/18 bg-black/30"
      }`}
    >
      {localizeCoachingText(correction?.message ?? advice)}
    </div>
  )
}

function RouteFlowOverlay() {
  return (
    <div className="pointer-events-none absolute inset-0 z-[2]">
      <div className="absolute bottom-[142px] left-[34%] h-[56px] w-[116px] -rotate-[10deg] rounded-[999px] border-y-[18px] border-l-[18px] border-white/22 opacity-70 blur-[0.2px]" />
      <div className="absolute bottom-[183px] left-[46%] h-[44px] w-[92px] -rotate-[8deg] rounded-[999px] border-y-[15px] border-l-[15px] border-white/16 opacity-55 blur-[0.2px]" />
      <div className="absolute bottom-[100px] left-[28%] h-[26px] w-[54px] -rotate-[12deg] rounded-[4px] bg-white/16 [clip-path:polygon(0_0,100%_50%,0_100%,16%_50%)]" />
    </div>
  )
}

export function MotionOutdoorDashboard({
  calories,
  confidence,
  connect,
  corrections,
  dataChannelState,
  fps,
  latencyMs,
  poseFrame,
  remoteStream,
  remoteMirrored,
  roomId,
  setRoomId,
  signalingState,
  startDemo,
  statusLabel,
  stop,
  videoRef,
  windowControls,
}: MotionOutdoorDashboardProps) {
  const latestCorrection = corrections[0]
  const isLive = Boolean(remoteStream)
  const actionDestination =
    poseFrame?.actionName === "PUSH_UP"
      ? "俯卧撑训练"
      : poseFrame?.actionName === "SQUAT"
        ? "蹲起训练"
        : "动作训练"
  const remotePreviewStyle = remoteMirrored
    ? { transform: "scaleX(-1)" }
    : undefined
  const lastFrame = poseFrame
    ? new Date(poseFrame.timestamp).toLocaleTimeString()
    : "--"
  const coachingOverlayText =
    poseFrame?.correctionMessage ||
    poseFrame?.advice ||
    latestCorrection?.message ||
    "等待安卓端纠错提示..."

  return (
    <section className="relative h-full w-full overflow-hidden rounded-[18px] border border-white/16 bg-[#11120f] text-white shadow-[0_42px_110px_rgba(0,0,0,0.45)]">
      <img
        src={backgroundImageUrl}
        alt=""
        className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-500 ${
          remoteStream ? "opacity-0" : "opacity-100"
        }`}
      />
      <video
        ref={videoRef}
        autoPlay
        muted
        playsInline
        poster={backgroundImageUrl}
        style={remotePreviewStyle}
        className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-500 ${
          remoteStream ? "opacity-100" : "opacity-0"
        }`}
      />

      <div className="absolute inset-0 z-[1] bg-[linear-gradient(180deg,rgba(0,0,0,0.46)_0%,rgba(0,0,0,0.1)_38%,rgba(0,0,0,0.48)_100%)]" />
      <div className="absolute inset-0 z-[1] bg-[radial-gradient(circle_at_48%_44%,rgba(255,255,255,0.05),rgba(0,0,0,0)_42%),linear-gradient(90deg,rgba(0,0,0,0.34)_0%,rgba(0,0,0,0.02)_45%,rgba(0,0,0,0.28)_100%)]" />
      <RouteFlowOverlay />

      <div
        className="absolute top-0 right-0 left-0 z-[8] flex h-[70px] items-center px-[18px]"
        data-window-drag-handle
      >
        {windowControls}
        <div className="ml-[22px] flex items-center gap-[10px] rounded-full border border-white/16 bg-black/22 px-[13px] py-[8px] text-[13px] font-semibold text-white/88 backdrop-blur-[22px]">
          <Sparkles size={15} />
          Motion Coach
        </div>
        <div
          className="ml-auto flex items-center gap-[8px]"
          data-window-no-drag
        >
          <div className="flex h-[34px] items-center rounded-full border border-white/16 bg-black/24 px-[12px] text-white/84 backdrop-blur-[22px]">
            <Radio size={14} className="mr-[7px] text-white/62" />
            <input
              className="w-[112px] bg-transparent text-[13px] font-medium outline-none placeholder:text-white/36"
              value={roomId}
              aria-label="Motion room"
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setRoomId(event.target.value)
              }
            />
          </div>
          <GlassButton onClick={() => void connect()}>Connect</GlassButton>
          <GlassButton primary onClick={() => void startDemo()}>
            Start demo
          </GlassButton>
          <GlassButton onClick={() => stop()}>Stop</GlassButton>
        </div>
      </div>

      <div className="absolute top-[88px] left-[44px] z-[6]">
        <MotionRoutePanel
          destination={actionDestination}
          distance=""
          direction=""
        />
      </div>

      <div className="absolute top-[88px] right-[32px] z-[6]">
        <CoachingOverlay
          advice={coachingOverlayText}
          correction={latestCorrection}
        />
      </div>

      <div className="absolute bottom-[28px] left-[32px] z-[6] flex max-w-[520px] flex-wrap gap-[8px]">
        <MotionStatusChip label="WebRTC" value={statusLabel} />
        <MotionStatusChip label="Channel" value={dataChannelState} />
        <MotionStatusChip label="Signal" value={signalingState} />
        <MotionStatusChip label="FPS" value={fps ? `${fps}` : "--"} />
        <MotionStatusChip
          label="Latency"
          value={latencyMs ? `${latencyMs}ms` : "--"}
        />
        <MotionStatusChip label="Last" value={lastFrame} />
      </div>

      <div className="absolute right-[32px] bottom-[32px] z-[6] w-[310px] rounded-[28px] border border-white/16 bg-black/24 p-[16px] text-white shadow-[0_24px_62px_rgba(0,0,0,0.24)] backdrop-blur-[26px]">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-[9px] text-[13px] font-semibold tracking-[0.08em] text-white/56 uppercase">
            <Gauge size={15} />
            实时体态
          </div>
          <div className="rounded-full bg-white/14 px-[9px] py-[4px] text-[12px] font-semibold text-white/86">
            {confidence}
          </div>
        </div>
        <div className="mt-[13px] grid grid-cols-3 gap-[8px]">
          <div>
            <div className="text-[11px] text-white/48">Calories</div>
            <div className="mt-[3px] text-[19px] font-semibold tracking-[-0.04em]">
              {calories}
            </div>
          </div>
          <div>
            <div className="text-[11px] text-white/48">Knee</div>
            <div className="mt-[3px] text-[19px] font-semibold tracking-[-0.04em]">
              {poseFrame ? `${poseFrame.angles.kneeLeft}deg` : "--"}
            </div>
          </div>
          <div>
            <div className="text-[11px] text-white/48">Hip</div>
            <div className="mt-[3px] text-[19px] font-semibold tracking-[-0.04em]">
              {poseFrame ? `${poseFrame.angles.hipLeft}deg` : "--"}
            </div>
          </div>
        </div>
        <div className="mt-[13px] flex items-start gap-[8px] border-t border-white/12 pt-[12px] text-[12px] leading-[17px] text-white/72">
          <Flame size={15} className="mt-[1px] shrink-0 text-white/70" />
          <span className="whitespace-pre-line">
            {localizeCoachingText(
              poseFrame?.correctionMessage ??
                poseFrame?.advice ??
                "连接安卓设备后，这里会同步显示中文纠错建议。"
            )}
          </span>
        </div>
      </div>

      <MotionBottomControl
        isActive={isLive}
        onPause={() => stop()}
        onResume={() => void startDemo()}
      />
    </section>
  )
}

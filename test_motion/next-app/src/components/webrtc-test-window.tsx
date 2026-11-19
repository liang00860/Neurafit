import { useEffect, useMemo, useRef, type ReactNode } from "react"
import {
  Activity,
  AlertTriangle,
  PlugZap,
  Radio,
  Square,
  Wifi,
} from "lucide-react"

import {
  useMotionWebRtc,
  type MotionPoseFrame,
} from "@/hooks/use-motion-webrtc"
import { getSignalingUrl } from "@/lib/signaling-url"

function statusLabel(status: string) {
  if (status === "connected") return "已连接"
  if (status === "connecting") return "连接中"
  if (status === "demo") return "本地 Demo"
  if (status === "error") return "异常"
  if (status === "waiting") return "等待安卓端发流"
  return "空闲"
}

function toneForState(value: string) {
  if (value === "connected" || value === "open" || value === "online") {
    return "border-emerald-300/30 bg-emerald-300/14 text-emerald-100"
  }

  if (
    value === "connecting" ||
    value === "waiting" ||
    value === "demo" ||
    value === "demo loopback"
  ) {
    return "border-sky-300/30 bg-sky-300/14 text-sky-100"
  }

  if (
    value === "closed" ||
    value === "disconnected" ||
    value === "error" ||
    value === "offline"
  ) {
    return "border-rose-300/30 bg-rose-300/14 text-rose-100"
  }

  return "border-white/14 bg-white/8 text-white/72"
}

function formatNumber(value: unknown, digits: number, fallback: string) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toFixed(digits)
    : fallback
}

function drawPoseOverlay(
  canvas: HTMLCanvasElement,
  frame: MotionPoseFrame | null
) {
  const context = canvas.getContext("2d")
  if (!context) {
    return
  }

  const rect = canvas.getBoundingClientRect()
  const ratio = window.devicePixelRatio || 1
  canvas.width = Math.max(1, Math.round(rect.width * ratio))
  canvas.height = Math.max(1, Math.round(rect.height * ratio))
  context.setTransform(1, 0, 0, 1, 0, 0)
  context.scale(ratio, ratio)
  context.clearRect(0, 0, rect.width, rect.height)

  if (!frame) {
    return
  }

  context.lineCap = "round"
  context.lineJoin = "round"
  context.lineWidth = 3
  context.strokeStyle = "rgba(255,255,255,0.82)"
  const landmarksById = new Map(
    frame.landmarks.map((landmark) => [landmark.id, landmark])
  )

  for (const [from, to] of frame.connections) {
    const start = landmarksById.get(from)
    const end = landmarksById.get(to)
    if (!start || !end) {
      continue
    }

    context.beginPath()
    context.moveTo(start.x * rect.width, start.y * rect.height)
    context.lineTo(end.x * rect.width, end.y * rect.height)
    context.stroke()
  }

  for (const landmark of frame.landmarks) {
    const x = landmark.x * rect.width
    const y = landmark.y * rect.height
    context.fillStyle =
      landmark.score > 0.94
        ? "rgba(255,255,255,0.96)"
        : "rgba(245, 235, 190, 0.92)"
    context.beginPath()
    context.arc(x, y, 4.5, 0, Math.PI * 2)
    context.fill()
  }
}

function StatePill({ label, value }: { label: string; value: string }) {
  return (
    <div
      className={`rounded-full border px-[12px] py-[7px] text-[12px] font-semibold ${toneForState(value)}`}
    >
      <span className="text-white/46">{label}</span>
      <span className="ml-[7px] text-white/92">{value}</span>
    </div>
  )
}

function MetricTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-h-[86px] rounded-[14px] border border-white/12 bg-white/[0.06] p-[14px] shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]">
      <div className="text-[11px] font-semibold tracking-[0.12em] text-white/42 uppercase">
        {label}
      </div>
      <div className="mt-[14px] truncate text-[20px] font-semibold tracking-[-0.01em] text-white">
        {value}
      </div>
    </div>
  )
}

function ActionButton({
  children,
  disabled = false,
  onClick,
  primary = false,
}: {
  children: ReactNode
  disabled?: boolean
  onClick: () => void
  primary?: boolean
}) {
  return (
    <button
      className={`inline-flex h-[38px] items-center gap-[8px] rounded-[10px] border px-[14px] text-[13px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45 ${
        primary
          ? "border-white/26 bg-white text-[#121317] shadow-[0_14px_34px_rgba(255,255,255,0.12)] hover:bg-white/92"
          : "border-white/14 bg-white/[0.07] text-white/82 hover:bg-white/[0.12]"
      }`}
      data-window-no-drag
      disabled={disabled}
      type="button"
      onClick={onClick}
    >
      {children}
    </button>
  )
}

export function WebRtcTestWindow({
  className = "",
  windowControls,
}: {
  className?: string
  windowControls?: ReactNode
}) {
  const signalingUrl = useMemo(getSignalingUrl, [])
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const {
    connect,
    dataChannelState,
    error,
    fps,
    latencyMs,
    poseFrame,
    remoteStream,
    roomId,
    setRoomId,
    signalingState,
    startDemo,
    status,
    stop,
  } = useMotionWebRtc()

  useEffect(() => {
    const video = videoRef.current
    if (!video) {
      return
    }

    if (video.srcObject !== remoteStream) {
      video.srcObject = remoteStream
    }

    if (!remoteStream) {
      video.pause()
      return
    }

    let cancelled = false
    void video.play().catch((error: unknown) => {
      if (
        cancelled ||
        (error instanceof DOMException && error.name === "AbortError")
      ) {
        return
      }

      console.error("Failed to start remote stream playback", error)
    })

    return () => {
      cancelled = true
    }
  }, [remoteStream])

  useEffect(() => {
    if (!canvasRef.current) {
      return
    }

    drawPoseOverlay(canvasRef.current, poseFrame)
  }, [poseFrame, remoteStream])

  const busy = status === "connecting" || status === "waiting"
  const lastFrameTime = poseFrame
    ? new Date(poseFrame.timestamp).toLocaleTimeString()
    : "--"
  const latestCorrection =
    poseFrame?.corrections[0]?.message ?? "暂无姿态纠错数据"
  const kneeValue = poseFrame ? `${poseFrame.angles?.kneeLeft ?? 0}deg` : "--"
  const hipValue = poseFrame ? `${poseFrame.angles?.hipLeft ?? 0}deg` : "--"
  const confidenceValue = poseFrame
    ? `${Math.round(poseFrame.confidence * 100)}%`
    : "--"
  const shouldMirrorRemotePreview =
    status === "demo"
      ? Boolean(poseFrame?.image.mirrored)
      : !(
          poseFrame?.image.mirrored ?? Boolean(remoteStream)
        )
  const remotePreviewStyle = shouldMirrorRemotePreview
    ? { transform: "scaleX(-1)" }
    : undefined

  return (
    <section
      className={`relative h-full w-full overflow-hidden rounded-[18px] border border-white/16 bg-[#0b0d10] text-white shadow-[0_42px_110px_rgba(0,0,0,0.46)] ${className}`}
    >
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_24%_0%,rgba(79,209,197,0.18),transparent_36%),radial-gradient(circle_at_84%_18%,rgba(96,165,250,0.16),transparent_32%),linear-gradient(180deg,#11151b_0%,#090a0c_100%)]" />
      <div className="absolute inset-0 [background-image:linear-gradient(rgba(255,255,255,0.08)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.08)_1px,transparent_1px)] [background-size:42px_42px] opacity-[0.18]" />

      <div
        className="absolute top-0 right-0 left-0 z-[8] flex h-[64px] items-center border-b border-white/10 bg-black/24 px-[18px] backdrop-blur-[24px]"
        data-window-drag-handle
      >
        {windowControls}
        <div className="ml-[22px] flex items-center gap-[10px] text-[15px] font-semibold text-white/92">
          <span className="flex size-[34px] items-center justify-center rounded-[10px] border border-white/14 bg-white/10">
            <Radio size={18} />
          </span>
          WebRTC Test Receiver
        </div>
        <div
          className="ml-auto flex items-center gap-[8px]"
          data-window-no-drag
        >
          <StatePill label="状态" value={statusLabel(status)} />
          <StatePill label="信道" value={dataChannelState} />
        </div>
      </div>

      <div className="relative z-[2] flex h-full min-h-0 flex-col pt-[64px]">
        <div className="grid min-h-0 flex-1 grid-cols-[320px_1fr] gap-[18px] p-[18px]">
          <aside className="flex min-h-0 flex-col rounded-[16px] border border-white/12 bg-black/22 p-[16px] backdrop-blur-[18px]">
            <div className="text-[11px] font-semibold tracking-[0.12em] text-white/44 uppercase">
              Receiver Setup
            </div>
            <label className="mt-[16px] text-[12px] font-semibold text-white/58">
              Room ID
              <input
                aria-label="WebRTC receiver room"
                className="mt-[8px] h-[40px] w-full rounded-[10px] border border-white/12 bg-white/[0.07] px-[12px] text-[14px] font-semibold text-white transition outline-none placeholder:text-white/32 focus:border-white/28"
                data-window-no-drag
                value={roomId}
                onChange={(event) => setRoomId(event.target.value)}
              />
            </label>
            <div className="mt-[14px] rounded-[12px] border border-white/10 bg-black/20 p-[12px]">
              <div className="mb-[7px] flex items-center gap-[7px] text-[11px] font-semibold tracking-[0.1em] text-white/42 uppercase">
                <Wifi size={13} />
                Signaling URL
              </div>
              <div className="text-[12px] leading-[17px] break-all text-white/72">
                {signalingUrl}
              </div>
            </div>
            <div className="mt-[16px] flex flex-col gap-[10px]">
              <ActionButton
                disabled={busy || status === "connected"}
                onClick={() => void connect()}
                primary
              >
                <PlugZap size={15} />
                连接接收端
              </ActionButton>
              <ActionButton disabled={busy} onClick={() => void startDemo()}>
                <Activity size={15} />
                启动本地 Demo
              </ActionButton>
              <ActionButton onClick={() => stop()}>
                <Square size={14} />
                停止
              </ActionButton>
            </div>
            <div className="mt-[16px] rounded-[12px] border border-white/10 bg-white/[0.05] p-[12px] text-[12px] leading-[18px] text-white/56">
              <div className="mb-[8px] font-semibold text-white/78">
                安卓联调流程
              </div>
              <div>1. 安卓模拟器进入 `stream to motion coach`。</div>
              <div>2. 保持与此面板相同的 `Room ID`。</div>
              <div>3. 安卓端点击 `Stream` 发起推流。</div>
              <div>4. 当前面板作为 receive 端接收视频与姿态数据。</div>
            </div>
            <div className="mt-auto rounded-[12px] border border-white/10 bg-black/20 p-[12px] text-[12px] leading-[18px] text-white/56">
              如果只想验证 Web 端接收链路，可先点击“启动本地 Demo”。
            </div>
          </aside>

          <main className="flex min-h-0 flex-col gap-[14px]">
            <div className="grid grid-cols-4 gap-[12px]">
              <MetricTile
                label="Latency"
                value={latencyMs ? `${latencyMs}ms` : "--"}
              />
              <MetricTile label="FPS" value={fps ? `${fps}` : "--"} />
              <MetricTile label="Knee" value={kneeValue} />
              <MetricTile label="Hip" value={hipValue} />
            </div>

            <div className="grid grid-cols-3 gap-[12px]">
              <StatePill label="Signal" value={signalingState} />
              <StatePill label="Last Frame" value={lastFrameTime} />
              <StatePill label="Confidence" value={confidenceValue} />
            </div>

            <div className="grid min-h-0 flex-1 grid-cols-[1.3fr_0.7fr] gap-[14px]">
              <div className="relative min-h-0 overflow-hidden rounded-[16px] border border-white/12 bg-black/30">
                <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_20%,rgba(255,255,255,0.08),transparent_48%)]" />
                <video
                  ref={videoRef}
                  autoPlay
                  muted
                  playsInline
                  style={remotePreviewStyle}
                  className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-300 ${
                    remoteStream ? "opacity-100" : "opacity-0"
                  }`}
                />
                <canvas
                  ref={canvasRef}
                  style={remotePreviewStyle}
                  className="absolute inset-0 h-full w-full"
                />
                {!remoteStream && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center px-[24px] text-center">
                    <div className="rounded-full border border-white/14 bg-white/8 px-[14px] py-[8px] text-[12px] font-semibold text-white/76">
                      Receiver Preview
                    </div>
                    <div className="mt-[14px] text-[24px] font-semibold text-white">
                      等待安卓端视频流
                    </div>
                    <div className="mt-[8px] max-w-[420px] text-[13px] leading-[20px] text-white/50">
                      安卓端点击 `stream to motion coach` 的 `Stream`
                      后，这里会显示远端画面；姿态关键点会叠加在视频上用于测试接收链路。
                    </div>
                  </div>
                )}
              </div>

              <div className="flex min-h-0 flex-col gap-[12px]">
                <div className="rounded-[16px] border border-white/12 bg-black/24 p-[16px]">
                  <div className="text-[11px] font-semibold tracking-[0.12em] text-white/42 uppercase">
                    当前建议
                  </div>
                  <div className="mt-[12px] text-[15px] leading-[23px] text-white/88">
                    {poseFrame?.advice ?? "尚未收到安卓端姿态帧。"}
                  </div>
                </div>

                <div className="rounded-[16px] border border-white/12 bg-black/24 p-[16px]">
                  <div className="text-[11px] font-semibold tracking-[0.12em] text-white/42 uppercase">
                    姿态纠错
                  </div>
                  <div className="mt-[12px] text-[14px] leading-[22px] text-white/82">
                    {latestCorrection}
                  </div>
                </div>

                <div className="rounded-[16px] border border-white/12 bg-black/24 p-[16px]">
                  <div className="text-[11px] font-semibold tracking-[0.12em] text-white/42 uppercase">
                    数据面
                  </div>
                  <div className="mt-[12px] space-y-[8px] text-[13px] leading-[20px] text-white/72">
                    <div>
                      Remote stream: {remoteStream ? "已收到" : "未收到"}
                    </div>
                    <div>Data channel: {dataChannelState}</div>
                    <div>
                      Pose frame:{" "}
                      {poseFrame ? `${poseFrame.landmarks.length} 点` : "--"}
                    </div>
                    <div>
                      Calories:{" "}
                      {poseFrame
                        ? formatNumber(poseFrame.calories, 2, "--")
                        : "--"}
                    </div>
                  </div>
                </div>

                {error && (
                  <div className="flex items-start gap-[10px] rounded-[14px] border border-rose-300/20 bg-rose-300/10 p-[12px] text-[13px] leading-[19px] text-rose-50">
                    <AlertTriangle className="mt-[1px] shrink-0" size={16} />
                    <span>{error}</span>
                  </div>
                )}
              </div>
            </div>
          </main>
        </div>
      </div>
    </section>
  )
}

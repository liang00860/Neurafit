import { useEffect, useRef, type ReactNode } from "react"

import { MotionOutdoorDashboard } from "@/components/motion/motion-outdoor-dashboard"
import {
  useMotionWebRtc,
} from "@/hooks/use-motion-webrtc"

function statusLabel(status: string) {
  if (status === "connected") return "Connected"
  if (status === "connecting") return "Connecting"
  if (status === "demo") return "Demo live"
  if (status === "error") return "Needs attention"
  if (status === "waiting") return "Waiting for phone"
  return "Idle"
}

function formatNumber(value: unknown, digits: number, fallback: string) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toFixed(digits)
    : fallback
}

export function MotionAppWindow({
  className = "",
  windowControls,
}: {
  className?: string
  windowControls?: ReactNode
}) {
  const autoConnectAttemptedRef = useRef(false)
  const videoRef = useRef<HTMLVideoElement>(null)
  const {
    connect,
    dataChannelState,
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
    if (autoConnectAttemptedRef.current) {
      return
    }
    if (
      remoteStream ||
      status === "connected" ||
      status === "connecting" ||
      status === "demo"
    ) {
      return
    }

    autoConnectAttemptedRef.current = true
    void connect()
  }, [connect, remoteStream, status])

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

  const confidence = poseFrame
    ? `${Math.round(poseFrame.confidence * 100)}%`
    : "--"
  const calories = poseFrame
    ? formatNumber(poseFrame.calories, 2, "0.00")
    : "0.00"
  const corrections = poseFrame?.corrections.length ? poseFrame.corrections : []
  const remoteMirrored =
    status === "demo"
      ? Boolean(poseFrame?.image.mirrored)
      : !(
          poseFrame?.image.mirrored ?? Boolean(remoteStream)
        )

  return (
    <div className={`h-full w-full ${className}`}>
      <MotionOutdoorDashboard
        calories={calories}
        confidence={confidence}
        connect={connect}
        corrections={corrections}
        dataChannelState={dataChannelState}
        fps={fps}
        latencyMs={latencyMs}
        poseFrame={poseFrame}
        remoteMirrored={remoteMirrored}
        remoteStream={remoteStream}
        roomId={roomId}
        setRoomId={setRoomId}
        signalingState={signalingState}
        startDemo={startDemo}
        statusLabel={statusLabel(status)}
        stop={stop}
        videoRef={videoRef}
        windowControls={windowControls}
      />
    </div>
  )
}

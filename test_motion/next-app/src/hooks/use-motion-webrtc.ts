import {
  createContext,
  createElement,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react"

import { saveMotionTrainingSession } from "@/components/motion-api"
import { getSignalingUrl } from "@/lib/signaling-url"

export type MotionCorrection = {
  message: string
  severity: "danger" | "info" | "warning"
  type?: string
}

export type MotionLandmark = {
  id: number
  score: number
  x: number
  y: number
  z?: number | null
}

export type MotionPoseAngles = {
  bodyLine?: number | null
  hipLeft: number
  hipRight: number
  kneeLeft: number
  kneeRight: number
  leftElbow?: number | null
  leftHip?: number | null
  leftKnee?: number | null
  rightElbow?: number | null
  rightHip?: number | null
  rightKnee?: number | null
  shoulderLeft: number
  shoulderRight: number
  torsoTilt?: number | null
}

export type MotionPoseFrame = {
  actionName?: "PUSH_UP" | "SQUAT" | string
  advice: string
  angles: MotionPoseAngles
  calories: number
  confidence: number
  connections: [number, number][]
  correctionMessage?: string
  corrections: MotionCorrection[]
  counters?: {
    lastRepDurationMs?: number | null
    repCount?: number
    stage?: string
  }
  frameId?: number
  image: {
    height: number
    mirrored: boolean
    width: number
  }
  landmarks: MotionLandmark[]
  score?: number
  source?: string
  timestamp: number
  type: "pose-frame"
  version?: number
}

type SignalMessage =
  | {
      role?: "receiver" | "sender"
      roomId?: string
      type: "join" | "joined" | "peer-joined" | "peer-left"
    }
  | {
      candidate: RTCIceCandidateInit
      roomId?: string
      type: "ice-candidate"
    }
  | {
      roomId?: string
      sdp: RTCSessionDescriptionInit
      type: "answer" | "offer"
    }
  | {
      message: string
      type: "error" | "room-full"
    }

type MotionConnectionStatus =
  | "connected"
  | "connecting"
  | "demo"
  | "error"
  | "idle"
  | "waiting"

export type MotionWebRtcController = {
  connect: () => Promise<void>
  dataChannelState: RTCDataChannelState
  error: string
  fps: number
  latencyMs: number
  poseFrame: MotionPoseFrame | null
  remoteStream: MediaStream | null
  roomId: string
  setRoomId: (roomId: string) => void
  signalingState: string
  startDemo: () => void | Promise<void>
  status: MotionConnectionStatus
  stop: () => void
}

const DEFAULT_ROOM_ID = "motion-2026"
const DEFAULT_ICE_SERVER: RTCIceServer = { urls: "stun:stun.l.google.com:19302" }
const DEBUG_SERVER_URL = import.meta.env.VITE_DEBUG_SERVER_URL?.trim() ?? ""
const DEBUG_SESSION_ID = "webrtc-quality-latency"
const MotionWebRtcContext = createContext<MotionWebRtcController | null>(null)

const DEMO_CONNECTIONS: [number, number][] = [
  [0, 11],
  [11, 12],
  [11, 13],
  [13, 15],
  [12, 14],
  [14, 16],
  [11, 23],
  [12, 24],
  [23, 24],
  [23, 25],
  [25, 27],
  [24, 26],
  [26, 28],
]

function getIceServers(): RTCIceServer[] {
  const configuredServers = import.meta.env.VITE_RTC_ICE_SERVERS

  if (!configuredServers) {
    return [DEFAULT_ICE_SERVER]
  }

  try {
    const parsedServers = JSON.parse(configuredServers) as RTCIceServer[]
    if (Array.isArray(parsedServers) && parsedServers.length > 0) {
      return parsedServers
    }
  } catch {
    return configuredServers
      .split(",")
      .map((url: string) => url.trim())
      .filter(Boolean)
      .map((url: string) => ({ urls: url }))
  }

  return [DEFAULT_ICE_SERVER]
}

function reportMotionDebugEvent(
  hypothesisId: "A" | "B" | "C" | "D" | "E",
  location: string,
  msg: string,
  data: Record<string, unknown> = {}
) {
  if (!DEBUG_SERVER_URL) return
  fetch(DEBUG_SERVER_URL, {
    method: "POST",
    body: JSON.stringify({
      data,
      hypothesisId,
      location,
      msg: `[DEBUG] ${msg}`,
      runId: "pre-fix",
      sessionId: DEBUG_SESSION_ID,
      ts: Date.now(),
    }),
  }).catch(() => {})
}

function getIceTransportPolicy(): RTCIceTransportPolicy {
  if (typeof window !== "undefined") {
    const searchParams = new URLSearchParams(window.location.search)
    if (searchParams.get("debugDesktop") === "1") {
      return "all"
    }
  }

  return "all"
}

function getStreamFrameRate(stream: MediaStream | null) {
  const videoTrack = stream?.getVideoTracks()[0]
  const frameRate = videoTrack?.getSettings().frameRate

  if (
    typeof frameRate === "number" &&
    Number.isFinite(frameRate) &&
    frameRate > 0
  ) {
    return Math.round(frameRate)
  }

  return 0
}

function summarizeWebRtcReceiverStats(report: RTCStatsReport) {
  let selectedCandidatePairId = ""
  let inboundVideo: Record<string, unknown> | null = null
  let trackStats: Record<string, unknown> | null = null
  let candidatePairStats: Record<string, unknown> | null = null
  const candidateStats = new Map<string, Record<string, unknown>>()

  report.forEach((stat) => {
    if (stat.type === "transport") {
      selectedCandidatePairId = readString(
        (stat as Record<string, unknown>).selectedCandidatePairId
      )
      return
    }

    if (stat.type === "inbound-rtp") {
      const kind = readString((stat as Record<string, unknown>).kind)
      const mediaType = readString((stat as Record<string, unknown>).mediaType)
      if (kind === "video" || mediaType === "video") {
        inboundVideo = stat as Record<string, unknown>
      }
      return
    }

    if (stat.type === "track") {
      const kind = readString((stat as Record<string, unknown>).kind)
      if (kind === "video") {
        trackStats = stat as Record<string, unknown>
      }
      return
    }

    if (stat.type === "candidate-pair") {
      const statRecord = stat as Record<string, unknown>
      const isSelected =
        readBoolean(statRecord.selected, false) ||
        readBoolean(statRecord.nominated, false)
      if (isSelected || stat.id === selectedCandidatePairId) {
        candidatePairStats = statRecord
      }
      return
    }

    if (stat.type === "local-candidate" || stat.type === "remote-candidate") {
      candidateStats.set(stat.id, stat as Record<string, unknown>)
    }
  })

  const selectedCandidatePairStats = candidatePairStats as Record<
    string,
    unknown
  > | null
  const inboundVideoStats = inboundVideo as Record<string, unknown> | null
  const trackStatsRecord = trackStats as Record<string, unknown> | null

  let candidatePair: {
    availableIncomingBitrate: number
    availableOutgoingBitrate: number
    currentRoundTripTime: number
    localCandidateType: string
    localNetworkType: string
    remoteCandidateType: string
    remoteProtocol: string
    state: string
  } | null = null

  if (selectedCandidatePairStats) {
    const localCandidate = candidateStats.get(
      readString(selectedCandidatePairStats.localCandidateId)
    )
    const remoteCandidate = candidateStats.get(
      readString(selectedCandidatePairStats.remoteCandidateId)
    )

    candidatePair = {
      availableIncomingBitrate: readNumber(
        selectedCandidatePairStats.availableIncomingBitrate,
        0
      ),
      availableOutgoingBitrate: readNumber(
        selectedCandidatePairStats.availableOutgoingBitrate,
        0
      ),
      currentRoundTripTime: readNumber(
        selectedCandidatePairStats.currentRoundTripTime,
        0
      ),
      localCandidateType: readString(localCandidate?.candidateType, "unknown"),
      localNetworkType: readString(localCandidate?.networkType, "unknown"),
      remoteCandidateType: readString(
        remoteCandidate?.candidateType,
        "unknown"
      ),
      remoteProtocol: readString(remoteCandidate?.protocol, "unknown"),
      state: readString(selectedCandidatePairStats.state, "unknown"),
    }
  }

  const inboundVideoSummary = inboundVideoStats
    ? {
        bytesReceived: readNumber(inboundVideoStats.bytesReceived, 0),
        frameHeight: readNumber(inboundVideoStats.frameHeight, 0),
        frameWidth: readNumber(inboundVideoStats.frameWidth, 0),
        framesDecoded: readNumber(inboundVideoStats.framesDecoded, 0),
        framesDropped: readNumber(inboundVideoStats.framesDropped, 0),
        framesPerSecond: readNumber(inboundVideoStats.framesPerSecond, 0),
        jitter: readNumber(inboundVideoStats.jitter, 0),
        packetsLost: readNumber(inboundVideoStats.packetsLost, 0),
        totalDecodeTime: readNumber(inboundVideoStats.totalDecodeTime, 0),
      }
    : null

  const trackSummary = trackStatsRecord
    ? {
        frameHeight: readNumber(trackStatsRecord.frameHeight, 0),
        frameWidth: readNumber(trackStatsRecord.frameWidth, 0),
        framesDecoded: readNumber(trackStatsRecord.framesDecoded, 0),
        framesDropped: readNumber(trackStatsRecord.framesDropped, 0),
        framesPerSecond: readNumber(trackStatsRecord.framesPerSecond, 0),
        jitterBufferDelay: readNumber(trackStatsRecord.jitterBufferDelay, 0),
        jitterBufferEmittedCount: readNumber(
          trackStatsRecord.jitterBufferEmittedCount,
          0
        ),
      }
    : null

  return {
    candidatePair,
    inboundVideo: inboundVideoSummary,
    track: trackSummary,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function readNumber(value: unknown, fallback = 0) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value
  }

  if (typeof value === "string" && value.trim()) {
    const parsedValue = Number(value)
    if (Number.isFinite(parsedValue)) {
      return parsedValue
    }
  }

  return fallback
}

function readNullableNumber(value: unknown): number | null {
  if (value === null || value === undefined) {
    return null
  }

  const numberValue = readNumber(value, Number.NaN)
  return Number.isFinite(numberValue) ? numberValue : null
}

function readString(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback
}

function readBoolean(value: unknown, fallback = false) {
  return typeof value === "boolean" ? value : fallback
}

function normalizeCorrectionSeverity(
  value: unknown
): MotionCorrection["severity"] {
  if (value === "danger" || value === "warning" || value === "info") {
    return value
  }

  return "info"
}

function normalizeCorrections(value: unknown): MotionCorrection[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.flatMap((correction) => {
    if (!isRecord(correction)) {
      return []
    }

    const message = readString(correction.message)
    if (!message) {
      return []
    }

    return [
      {
        message,
        severity: normalizeCorrectionSeverity(correction.severity),
        type: readString(correction.type, "form"),
      },
    ]
  })
}

function normalizeLandmarks(value: unknown): MotionLandmark[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .flatMap((landmark, index) => {
      if (!isRecord(landmark)) {
        return []
      }

      const id = Math.round(readNumber(landmark.id, index))
      const x = readNumber(landmark.x, Number.NaN)
      const y = readNumber(landmark.y, Number.NaN)
      if (!Number.isFinite(id) || !Number.isFinite(x) || !Number.isFinite(y)) {
        return []
      }

      return [
        {
          id,
          score: readNumber(landmark.score, 0),
          x,
          y,
          z: readNullableNumber(landmark.z),
        },
      ]
    })
    .sort((left, right) => left.id - right.id)
}

function normalizeConnections(value: unknown): [number, number][] {
  if (!Array.isArray(value)) {
    return DEMO_CONNECTIONS
  }

  const connections = value.flatMap((connection) => {
    if (!Array.isArray(connection) || connection.length < 2) {
      return []
    }

    const from = Math.round(readNumber(connection[0], Number.NaN))
    const to = Math.round(readNumber(connection[1], Number.NaN))
    if (!Number.isFinite(from) || !Number.isFinite(to)) {
      return []
    }

    return [[from, to] satisfies [number, number]]
  })

  return connections.length ? connections : DEMO_CONNECTIONS
}

function averageLandmarkScore(landmarks: MotionLandmark[]) {
  if (landmarks.length === 0) {
    return 0
  }

  const totalScore = landmarks.reduce(
    (total, landmark) => total + landmark.score,
    0
  )

  return Number((totalScore / landmarks.length).toFixed(2))
}

function normalizePoseFrame(payload: unknown): MotionPoseFrame | null {
  if (!isRecord(payload) || payload.type !== "pose-frame") {
    return null
  }

  const rawAngles = isRecord(payload.angles) ? payload.angles : {}
  const rawImage = isRecord(payload.image) ? payload.image : {}
  const landmarks = normalizeLandmarks(payload.landmarks)
  const corrections = normalizeCorrections(payload.corrections)
  const kneeLeft = readNumber(
    rawAngles.kneeLeft,
    readNumber(rawAngles.leftKnee, 0)
  )
  const kneeRight = readNumber(
    rawAngles.kneeRight,
    readNumber(rawAngles.rightKnee, kneeLeft)
  )
  const hipLeft = readNumber(
    rawAngles.hipLeft,
    readNumber(rawAngles.leftHip, 0)
  )
  const hipRight = readNumber(
    rawAngles.hipRight,
    readNumber(rawAngles.rightHip, hipLeft)
  )
  const shoulderLeft = readNumber(
    rawAngles.shoulderLeft,
    readNumber(rawAngles.leftShoulder, 0)
  )
  const shoulderRight = readNumber(
    rawAngles.shoulderRight,
    readNumber(rawAngles.rightShoulder, shoulderLeft)
  )
  const score = readNullableNumber(payload.score)
  const frameId = readNullableNumber(payload.frameId)
  const confidence = readNumber(
    payload.confidence,
    score == null ? averageLandmarkScore(landmarks) : score / 100
  )
  const counters = isRecord(payload.counters)
    ? {
        lastRepDurationMs: readNullableNumber(payload.counters.lastRepDurationMs),
        repCount: Math.round(readNumber(payload.counters.repCount, 0)),
        stage: readString(payload.counters.stage),
      }
    : undefined

  return {
    actionName: readString(payload.actionName) || undefined,
    advice: readString(payload.advice, corrections[0]?.message ?? ""),
    angles: {
      bodyLine: readNullableNumber(rawAngles.bodyLine),
      hipLeft,
      hipRight,
      kneeLeft,
      kneeRight,
      leftElbow: readNullableNumber(rawAngles.leftElbow),
      leftHip: readNullableNumber(rawAngles.leftHip) ?? hipLeft,
      leftKnee: readNullableNumber(rawAngles.leftKnee) ?? kneeLeft,
      rightElbow: readNullableNumber(rawAngles.rightElbow),
      rightHip: readNullableNumber(rawAngles.rightHip) ?? hipRight,
      rightKnee: readNullableNumber(rawAngles.rightKnee) ?? kneeRight,
      shoulderLeft,
      shoulderRight,
      torsoTilt: readNullableNumber(rawAngles.torsoTilt),
    },
    calories: readNumber(payload.calories, 0),
    confidence,
    connections: normalizeConnections(payload.connections),
    correctionMessage:
      readString(payload.correctionMessage) || readString(payload.message) || undefined,
    corrections,
    counters,
    frameId: frameId == null ? undefined : Math.round(frameId),
    image: {
      height: Math.round(readNumber(rawImage.height, 0)),
      mirrored: readBoolean(rawImage.mirrored, false),
      width: Math.round(readNumber(rawImage.width, 0)),
    },
    landmarks,
    score: score ?? Math.round(confidence * 100),
    source: readString(payload.source) || undefined,
    timestamp: readNumber(payload.timestamp, Date.now()),
    type: "pose-frame",
    version: Math.round(readNumber(payload.version, 1)),
  }
}

function stripPoseSkeleton(frame: MotionPoseFrame): MotionPoseFrame {
  return {
    ...frame,
    connections: [],
    landmarks: [],
  }
}

function landmarkMap(frame: MotionPoseFrame) {
  return new Map(frame.landmarks.map((landmark) => [landmark.id, landmark]))
}

function generatePoseFrame(startedAt: number, tick: number): MotionPoseFrame {
  const elapsed = (Date.now() - startedAt) / 1000
  const sway = Math.sin(elapsed * 2.4)
  const lift = Math.sin(elapsed * 3.1)
  const depth = (Math.sin(elapsed * 1.45) + 1) / 2
  const knee = Math.round(88 + depth * 54)
  const hip = Math.round(72 + depth * 42)
  const shoulder = Math.round(82 + Math.abs(sway) * 16)
  const confidence = Number((0.88 + depth * 0.1).toFixed(2))
  const corrections: MotionCorrection[] =
    knee < 105
      ? [
          {
            message: "下蹲不足，注意重心继续向后坐。",
            severity: "warning",
            type: "SQUAT_TOO_SHALLOW",
          },
        ]
      : Math.abs(sway) > 0.72
        ? [
            {
              message: "核心有些晃动，收紧腹部并保持控制。",
              severity: "info",
              type: "BALANCE",
            },
          ]
        : [
            {
              message: "动作节奏稳定，膝盖轨迹良好。",
              severity: "info",
              type: "NONE",
            },
          ]

  return {
    actionName: "SQUAT",
    advice:
      corrections[0].severity === "warning"
        ? "下一次动作时，膝盖和脚尖保持同向。"
        : "保持节奏，起身时顺势呼气。",
    angles: {
      hipLeft: hip,
      hipRight: hip + Math.round(sway * 4),
      kneeLeft: knee,
      kneeRight: knee + Math.round(lift * 5),
      leftHip: hip,
      leftKnee: knee,
      rightHip: hip + Math.round(sway * 4),
      rightKnee: knee + Math.round(lift * 5),
      shoulderLeft: shoulder,
      shoulderRight: shoulder + Math.round(sway * 3),
      torsoTilt: shoulder,
    },
    calories: Number((tick * 0.018).toFixed(2)),
    confidence,
    connections: DEMO_CONNECTIONS,
    corrections,
    counters: {
      repCount: Math.floor(tick / 34),
      stage: depth > 0.7 ? "BOTTOM" : depth > 0.35 ? "GOING_DOWN" : "STANDING",
    },
    frameId: tick,
    image: {
      height: 540,
      mirrored: true,
      width: 960,
    },
    landmarks: [
      { id: 0, score: 0.98, x: 0.5 + sway * 0.02, y: 0.18 },
      { id: 11, score: 0.97, x: 0.39 + sway * 0.015, y: 0.3 },
      { id: 13, score: 0.95, x: 0.33 - lift * 0.03, y: 0.45 },
      { id: 15, score: 0.93, x: 0.29 - lift * 0.04, y: 0.59 },
      { id: 12, score: 0.97, x: 0.61 + sway * 0.015, y: 0.3 },
      { id: 14, score: 0.95, x: 0.67 + lift * 0.03, y: 0.45 },
      { id: 16, score: 0.93, x: 0.71 + lift * 0.04, y: 0.59 },
      { id: 23, score: 0.96, x: 0.43 + sway * 0.012, y: 0.56 + depth * 0.06 },
      { id: 25, score: 0.94, x: 0.39 + lift * 0.025, y: 0.75 - depth * 0.04 },
      { id: 27, score: 0.92, x: 0.37 + lift * 0.02, y: 0.93 },
      { id: 24, score: 0.96, x: 0.57 + sway * 0.012, y: 0.56 + depth * 0.06 },
      { id: 26, score: 0.94, x: 0.61 - lift * 0.025, y: 0.75 - depth * 0.04 },
      { id: 28, score: 0.92, x: 0.63 - lift * 0.02, y: 0.93 },
    ],
    score: Math.round(confidence * 100),
    source: "web-demo",
    timestamp: Date.now(),
    type: "pose-frame",
    version: 1,
  }
}

function drawDemoFrame(
  context: CanvasRenderingContext2D,
  frame: MotionPoseFrame,
  tick: number
) {
  const { height, width } = context.canvas
  const landmarks = landmarkMap(frame)
  const gradient = context.createLinearGradient(0, 0, width, height)
  gradient.addColorStop(0, "#0f172a")
  gradient.addColorStop(0.52, "#102a36")
  gradient.addColorStop(1, "#0d1117")
  context.fillStyle = gradient
  context.fillRect(0, 0, width, height)

  context.fillStyle = "rgba(59, 130, 246, 0.12)"
  for (let index = 0; index < 8; index += 1) {
    const x = ((index * 140 + tick * 2) % (width + 120)) - 60
    context.beginPath()
    context.arc(x, 88 + index * 43, 46, 0, Math.PI * 2)
    context.fill()
  }

  context.strokeStyle = "rgba(255, 255, 255, 0.18)"
  context.lineWidth = 1
  for (let y = 64; y < height; y += 64) {
    context.beginPath()
    context.moveTo(0, y)
    context.lineTo(width, y)
    context.stroke()
  }

  context.lineWidth = 11
  context.lineCap = "round"
  context.strokeStyle = "#34d399"
  for (const [from, to] of frame.connections) {
    const start = landmarks.get(from)
    const end = landmarks.get(to)
    if (!start || !end) {
      continue
    }

    context.beginPath()
    context.moveTo(start.x * width, start.y * height)
    context.lineTo(end.x * width, end.y * height)
    context.stroke()
  }

  for (const landmark of frame.landmarks) {
    context.fillStyle = "#f8fafc"
    context.beginPath()
    context.arc(landmark.x * width, landmark.y * height, 9, 0, Math.PI * 2)
    context.fill()
    context.strokeStyle = "#0f766e"
    context.lineWidth = 3
    context.stroke()
  }

  context.fillStyle = "rgba(15, 23, 42, 0.72)"
  context.fillRect(26, 26, 266, 72)
  context.fillStyle = "#f8fafc"
  context.font = "700 28px Inter, sans-serif"
  context.fillText("Live squat analysis", 44, 59)
  context.font = "500 18px Inter, sans-serif"
  context.fillStyle = "#a7f3d0"
  context.fillText(
    `Knee ${frame.angles.kneeLeft}deg / ${frame.confidence}`,
    44,
    86
  )
}

function useMotionWebRtcController(): MotionWebRtcController {
  const [dataChannelState, setDataChannelState] =
    useState<RTCDataChannelState>("closed")
  const [error, setError] = useState("")
  const [fps, setFps] = useState(0)
  const [latencyMs, setLatencyMs] = useState(0)
  const [poseFrame, setPoseFrame] = useState<MotionPoseFrame | null>(null)
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null)
  const [roomId, setRoomId] = useState(DEFAULT_ROOM_ID)
  const [signalingState, setSignalingState] = useState("offline")
  const [status, setStatus] = useState<MotionConnectionStatus>("idle")

  const channelRef = useRef<RTCDataChannel | null>(null)
  const collectedFramesRef = useRef<MotionPoseFrame[]>([])
  const demoCleanupRef = useRef<(() => void) | null>(null)
  const lastFrameAtRef = useRef(0)
  const pendingIceCandidatesRef = useRef<RTCIceCandidateInit[]>([])
  const pcRef = useRef<RTCPeerConnection | null>(null)
  const sessionSourceRef = useRef("web-demo")
  const sessionStartedAtRef = useRef<string | null>(null)
  const socketRef = useRef<WebSocket | null>(null)

  const receivePoseFrame = useCallback((data: string) => {
    try {
      const parsedFrame = normalizePoseFrame(JSON.parse(data))
      if (!parsedFrame) {
        return
      }
      const sanitizedFrame = stripPoseSkeleton(parsedFrame)

      const receivedAt = Date.now()
      setLatencyMs(Math.max(0, receivedAt - sanitizedFrame.timestamp))
      if (lastFrameAtRef.current > 0) {
        setFps(
          Math.round(1000 / Math.max(1, receivedAt - lastFrameAtRef.current))
        )
      }
      lastFrameAtRef.current = receivedAt
      if (collectedFramesRef.current.length < 30) {
        collectedFramesRef.current.push(sanitizedFrame)
      } else {
        collectedFramesRef.current.shift()
        collectedFramesRef.current.push(sanitizedFrame)
      }
      setPoseFrame(sanitizedFrame)
      // #region debug-point E:web-pose-frame
      reportMotionDebugEvent("E", "use-motion-webrtc.ts:receivePoseFrame", "Web receiver parsed pose frame", {
        dataChannelState: channelRef.current?.readyState ?? "unknown",
        frameId: sanitizedFrame.frameId ?? null,
        landmarkCount: sanitizedFrame.landmarks.length,
        timestamp: sanitizedFrame.timestamp,
      })
      // #endregion
    } catch {
      setError("Pose data could not be parsed")
    }
  }, [])

  const attachDataChannel = useCallback(
    (channel: RTCDataChannel) => {
      channelRef.current = channel
      setDataChannelState(channel.readyState)
      channel.onopen = () => {
        setDataChannelState(channel.readyState)
        setStatus((currentStatus) =>
          currentStatus === "demo" ? "demo" : "connected"
        )
      }
      channel.onclose = () => setDataChannelState(channel.readyState)
      channel.onerror = () => {
        setDataChannelState(channel.readyState)
        setError("Pose data channel failed")
      }
      channel.onmessage = (event) => receivePoseFrame(String(event.data))
    },
    [receivePoseFrame]
  )

  const persistSession = useCallback(() => {
    const frames = collectedFramesRef.current
    const startedAt = sessionStartedAtRef.current
    if (!startedAt || frames.length === 0) {
      return
    }

    const endedAt = new Date().toISOString()
    const latestFrame = frames.at(-1)
    const durationSeconds = Math.max(
      1,
      Math.round(
        (new Date(endedAt).getTime() - new Date(startedAt).getTime()) / 1000
      )
    )

    void saveMotionTrainingSession({
      actionKey: "squat",
      calories: latestFrame?.calories ?? 0,
      durationSeconds,
      endedAt,
      poseFrames: frames,
      source: sessionSourceRef.current,
      startedAt,
      totalScore: latestFrame ? Math.round(latestFrame.confidence * 100) : 0,
    }).catch((saveError: unknown) => {
      console.warn("Motion session save failed", saveError)
    })
  }, [])

  const cleanup = useCallback(
    (nextStatus: MotionConnectionStatus = "idle") => {
      persistSession()
      demoCleanupRef.current?.()
      demoCleanupRef.current = null
      channelRef.current?.close()
      channelRef.current = null
      pcRef.current?.close()
      pcRef.current = null
      socketRef.current?.close()
      socketRef.current = null
      pendingIceCandidatesRef.current = []
      lastFrameAtRef.current = 0
      collectedFramesRef.current = []
      sessionStartedAtRef.current = null
      setDataChannelState("closed")
      setError("")
      setFps(0)
      setLatencyMs(0)
      setPoseFrame(null)
      setRemoteStream(null)
      setSignalingState("offline")
      setStatus(nextStatus)
    },
    [persistSession]
  )

  const createReceiverPeer = useCallback(() => {
    const peerConnection = new RTCPeerConnection({
      iceServers: getIceServers(),
      iceTransportPolicy: getIceTransportPolicy(),
    })
    pcRef.current = peerConnection

    peerConnection.onconnectionstatechange = () => {
      // #region debug-point D:web-connection-state
      reportMotionDebugEvent("D", "use-motion-webrtc.ts:onconnectionstatechange", "Web peer connection state changed", {
        connectionState: peerConnection.connectionState,
        iceConnectionState: peerConnection.iceConnectionState,
      })
      // #endregion
      if (peerConnection.connectionState === "connected") {
        setStatus("connected")
      }
      if (
        peerConnection.connectionState === "failed" ||
        peerConnection.connectionState === "disconnected"
      ) {
        setError("WebRTC connection lost. If signaling is online, add TURN servers.")
        setStatus("error")
      }
    }

    peerConnection.ondatachannel = (event) => attachDataChannel(event.channel)
    peerConnection.ontrack = (event) => {
      const [stream] = event.streams
      if (stream) {
        setRemoteStream(stream)
        const streamFrameRate = getStreamFrameRate(stream)
        // #region debug-point C:web-ontrack
        reportMotionDebugEvent("C", "use-motion-webrtc.ts:ontrack", "Web receiver got remote stream track", {
          frameRate: streamFrameRate,
          streamId: stream.id,
          trackCount: stream.getTracks().length,
          videoTrackState: stream.getVideoTracks()[0]?.readyState ?? "none",
        })
        // #endregion
        if (streamFrameRate > 0) {
          setFps((currentFps) => currentFps || streamFrameRate)
        }
      }
    }

    return peerConnection
  }, [attachDataChannel])

  const addRemoteIceCandidate = useCallback(
    async (
      peerConnection: RTCPeerConnection,
      candidate: RTCIceCandidateInit
    ) => {
      if (!peerConnection.remoteDescription) {
        pendingIceCandidatesRef.current.push(candidate)
        return
      }

      await peerConnection.addIceCandidate(candidate)
    },
    []
  )

  const drainRemoteIceCandidates = useCallback(
    async (peerConnection: RTCPeerConnection) => {
      const candidates = pendingIceCandidatesRef.current.splice(0)
      for (const candidate of candidates) {
        await peerConnection.addIceCandidate(candidate)
      }
    },
    []
  )

  const connect = useCallback(async () => {
    cleanup("connecting")
    sessionSourceRef.current = "webrtc-phone"
    sessionStartedAtRef.current = new Date().toISOString()
    setSignalingState("connecting")
    const activeRoomId = roomId.trim() || DEFAULT_ROOM_ID

    const peerConnection = createReceiverPeer()
    const socket = new WebSocket(getSignalingUrl())
    socketRef.current = socket

    const sendSignal = (message: SignalMessage & { roomId?: string }) => {
      if (socket.readyState === WebSocket.OPEN) {
        // #region debug-point B:web-send-signal
        reportMotionDebugEvent("B", "use-motion-webrtc.ts:sendSignal", "Web receiver sent signaling message", {
          roomId: activeRoomId,
          type: message.type,
        })
        // #endregion
        socket.send(JSON.stringify({ ...message, roomId: activeRoomId }))
      }
    }

    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        // #region debug-point D:web-local-ice
        reportMotionDebugEvent("D", "use-motion-webrtc.ts:onicecandidate", "Web receiver gathered local ICE candidate", {
          candidate: event.candidate.candidate,
          sdpMLineIndex: event.candidate.sdpMLineIndex,
          sdpMid: event.candidate.sdpMid,
        })
        // #endregion
        sendSignal({
          candidate: event.candidate.toJSON(),
          type: "ice-candidate",
        })
      }
    }

    socket.onopen = () => {
      setSignalingState("online")
      setStatus("waiting")
      // #region debug-point B:web-socket-open
      reportMotionDebugEvent("B", "use-motion-webrtc.ts:socket.onopen", "Web receiver signaling socket opened", {
        roomId: activeRoomId,
        signalingUrl: getSignalingUrl(),
      })
      // #endregion
      sendSignal({ role: "receiver", roomId: activeRoomId, type: "join" })
    }

    socket.onerror = () => {
      // #region debug-point B:web-socket-error
      reportMotionDebugEvent("B", "use-motion-webrtc.ts:socket.onerror", "Web receiver signaling socket errored", {
        roomId: activeRoomId,
      })
      // #endregion
      setError("Signaling server is unavailable")
      setSignalingState("error")
      setStatus("error")
    }

    socket.onclose = () => {
      // #region debug-point B:web-socket-close
      reportMotionDebugEvent("B", "use-motion-webrtc.ts:socket.onclose", "Web receiver signaling socket closed", {
        roomId: activeRoomId,
      })
      // #endregion
      setSignalingState("offline")
    }

    socket.onmessage = async (event) => {
      try {
        const message = JSON.parse(String(event.data)) as SignalMessage
        // #region debug-point B:web-signal-message
        reportMotionDebugEvent("B", "use-motion-webrtc.ts:socket.onmessage", "Web receiver received signaling message", {
          roomId: activeRoomId,
          type: message.type,
        })
        // #endregion

        if (message.type === "joined") {
          setStatus("waiting")
        }

        if (message.type === "offer") {
          const remoteOffer = message.sdp
          const remoteSdp = remoteOffer.sdp ?? ""
          // #region debug-point A:web-offer
          reportMotionDebugEvent("A", "use-motion-webrtc.ts:offer", "Web receiver received SDP offer", {
            roomId: activeRoomId,
            sdpType: remoteOffer.type,
            sdpLength: remoteSdp.length,
          })
          // #endregion
          await peerConnection.setRemoteDescription(remoteOffer)
          await drainRemoteIceCandidates(peerConnection)
          const answer = await peerConnection.createAnswer()
          await peerConnection.setLocalDescription(answer)
          sendSignal({ sdp: answer, type: "answer" })
        }

        if (message.type === "ice-candidate") {
          // #region debug-point D:web-remote-ice
          reportMotionDebugEvent("D", "use-motion-webrtc.ts:remote-ice", "Web receiver received remote ICE candidate", {
            candidate: message.candidate.candidate,
            sdpMLineIndex: message.candidate.sdpMLineIndex,
            sdpMid: message.candidate.sdpMid,
          })
          // #endregion
          await addRemoteIceCandidate(peerConnection, message.candidate)
        }

        if (message.type === "peer-joined") {
          setStatus("waiting")
        }

        if (message.type === "peer-left") {
          setStatus("waiting")
          setRemoteStream(null)
        }

        if (message.type === "error" || message.type === "room-full") {
          setError(message.message)
          setStatus("error")
        }
      } catch (signalError) {
        setError(
          signalError instanceof Error
            ? signalError.message
            : "Unable to process signaling message"
        )
        setStatus("error")
      }
    }
  }, [
    addRemoteIceCandidate,
    cleanup,
    createReceiverPeer,
    drainRemoteIceCandidates,
    roomId,
  ])

  const startDemo = useCallback(async () => {
    cleanup("connecting")
    sessionSourceRef.current = "web-demo"
    sessionStartedAtRef.current = new Date().toISOString()
    setStatus("demo")
    setSignalingState("demo loopback")

    const receiverPeer = createReceiverPeer()
    const senderPeer = new RTCPeerConnection({
      iceServers: getIceServers(),
      iceTransportPolicy: getIceTransportPolicy(),
    })
    const canvas = document.createElement("canvas")
    const context = canvas.getContext("2d")
    const startedAt = Date.now()
    let animationFrame = 0
    let stopped = false
    let tick = 0

    if (!context) {
      setError("Demo canvas could not start")
      setStatus("error")
      return
    }

    canvas.width = 960
    canvas.height = 540

    const channel = senderPeer.createDataChannel("pose", {
      maxRetransmits: 0,
      ordered: false,
    })
    receiverPeer.ondatachannel = (event) => attachDataChannel(event.channel)
    senderPeer.onicecandidate = (event) => {
      if (event.candidate) {
        void receiverPeer.addIceCandidate(event.candidate)
      }
    }
    receiverPeer.onicecandidate = (event) => {
      if (event.candidate) {
        void senderPeer.addIceCandidate(event.candidate)
      }
    }

    const draw = () => {
      const frame = generatePoseFrame(startedAt, tick)
      drawDemoFrame(context, frame, tick)
      tick += 1
      if (!stopped) {
        animationFrame = requestAnimationFrame(draw)
      }
    }

    draw()

    const stream = canvas.captureStream(30)
    for (const track of stream.getTracks()) {
      senderPeer.addTrack(track, stream)
    }

    const offer = await senderPeer.createOffer()
    await senderPeer.setLocalDescription(offer)
    await receiverPeer.setRemoteDescription(offer)
    const answer = await receiverPeer.createAnswer()
    await receiverPeer.setLocalDescription(answer)
    await senderPeer.setRemoteDescription(answer)

    const sendTimer = window.setInterval(() => {
      if (channel.readyState === "open") {
        channel.send(JSON.stringify(generatePoseFrame(startedAt, tick)))
      }
    }, 66)

    demoCleanupRef.current = () => {
      stopped = true
      cancelAnimationFrame(animationFrame)
      window.clearInterval(sendTimer)
      for (const track of stream.getTracks()) {
        track.stop()
      }
      senderPeer.close()
    }
  }, [attachDataChannel, cleanup, createReceiverPeer])

  useEffect(() => {
    let stopped = false

    const pollStats = async () => {
      const peerConnection = pcRef.current
      if (!peerConnection) {
        return
      }

      try {
        const stats = await peerConnection.getStats()
        if (stopped) {
          return
        }

        const snapshot = summarizeWebRtcReceiverStats(stats)
        if (snapshot.inboundVideo || snapshot.track) {
          // #region debug-point C:web-receiver-stats
          reportMotionDebugEvent(
            "C",
            "use-motion-webrtc.ts:web-receiver-stats",
            "Web receiver sampled inbound video stats",
            {
              inboundVideo: snapshot.inboundVideo,
              track: snapshot.track,
            }
          )
          // #endregion
        }
        if (snapshot.candidatePair) {
          // #region debug-point D:web-candidate-pair-stats
          reportMotionDebugEvent(
            "D",
            "use-motion-webrtc.ts:web-candidate-pair-stats",
            "Web receiver sampled selected candidate pair stats",
            snapshot.candidatePair
          )
          // #endregion
        }
      } catch {
        // Stats sampling is best-effort during debugging.
      }
    }

    const intervalId = window.setInterval(() => {
      void pollStats()
    }, 2000)
    void pollStats()

    return () => {
      stopped = true
      window.clearInterval(intervalId)
    }
  }, [status])

  useEffect(() => () => cleanup("idle"), [cleanup])

  return {
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
    stop: () => cleanup("idle"),
  }
}

export function MotionWebRtcProvider({ children }: { children: ReactNode }) {
  const controller = useMotionWebRtcController()

  return createElement(
    MotionWebRtcContext.Provider,
    { value: controller },
    children
  )
}

export function useMotionWebRtc() {
  const controller = useContext(MotionWebRtcContext)

  if (!controller) {
    throw new Error("useMotionWebRtc must be used within MotionWebRtcProvider")
  }

  return controller
}

import { createServer } from "node:http"
import { WebSocketServer } from "ws"

const PORT = Number(process.env.SIGNALING_PORT ?? 8787)
const HOST = process.env.SIGNALING_HOST ?? "0.0.0.0"
const PATHNAME = "/motion-signaling"
const rooms = new Map()
const DEBUG_SERVER_URL = process.env.DEBUG_SERVER_URL?.trim() || ""
const DEBUG_SESSION_ID = "motion-coach-stream"
const corsHeaders = {
  "access-control-allow-headers": "content-type",
  "access-control-allow-methods": "GET, OPTIONS",
  "access-control-allow-origin": "*",
}

function reportDebugEvent(hypothesisId, location, msg, data = {}) {
  if (!DEBUG_SERVER_URL) return
  fetch(DEBUG_SERVER_URL, {
    body: JSON.stringify({
      data,
      hypothesisId,
      location,
      msg: `[DEBUG] ${msg}`,
      runId: "pre-fix",
      sessionId: DEBUG_SESSION_ID,
      ts: Date.now(),
    }),
    method: "POST",
  }).catch(() => {})
}

function send(socket, payload) {
  if (socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(payload))
  }
}

function leaveRoom(socket) {
  const { roomId } = socket.motionPeer ?? {}
  if (!roomId) {
    return
  }

  const peers = rooms.get(roomId)
  if (!peers) {
    return
  }

  peers.delete(socket)
  for (const peer of peers) {
    send(peer, { type: "peer-left" })
  }

  if (peers.size === 0) {
    rooms.delete(roomId)
  }

  socket.motionPeer = undefined
}

function joinRoom(socket, message) {
  const roomId = String(message.roomId ?? "").trim()
  const role = message.role === "sender" ? "sender" : "receiver"
  // #region debug-point B:signal-join
  reportDebugEvent("B", "signaling-server.mjs:joinRoom", "Signaling server received join request", {
    existingPeers: rooms.get(roomId)?.size ?? 0,
    role,
    roomId,
  })
  // #endregion

  if (!roomId) {
    send(socket, { message: "roomId is required", type: "error" })
    return
  }

  let peers = rooms.get(roomId)
  if (!peers) {
    peers = new Set()
    rooms.set(roomId, peers)
  }

  if (!peers.has(socket) && peers.size >= 2) {
    send(socket, { message: "Room is full", type: "room-full" })
    return
  }

  leaveRoom(socket)
  peers.add(socket)
  socket.motionPeer = { role, roomId }

  send(socket, { role, roomId, type: "joined" })
  for (const peer of peers) {
    if (peer !== socket) {
      send(peer, { role, roomId, type: "peer-joined" })
      send(socket, {
        role: peer.motionPeer?.role ?? "receiver",
        roomId,
        type: "peer-joined",
      })
    }
  }
}

function forwardToRoom(socket, message) {
  const { roomId } = socket.motionPeer ?? {}
  if (!roomId) {
    send(socket, { message: "Join a room before sending signals", type: "error" })
    return
  }

  const peers = rooms.get(roomId)
  if (!peers) {
    return
  }

  for (const peer of peers) {
    if (peer !== socket) {
      // #region debug-point D:signal-forward
      reportDebugEvent(
        message.type === "offer" || message.type === "answer" ? "A" : "D",
        "signaling-server.mjs:forwardToRoom",
        "Signaling server forwarded room message",
        {
          candidate:
            message.type === "ice-candidate"
              ? String(message.candidate?.candidate ?? "").slice(0, 180)
              : null,
          roomId,
          toRole: peer.motionPeer?.role ?? "unknown",
          type: message.type,
        }
      )
      // #endregion
      send(peer, { ...message, roomId })
    }
  }
}

const server = createServer((request, response) => {
  if (request.method === "OPTIONS") {
    response.writeHead(204, corsHeaders)
    response.end()
    return
  }

  if (request.url === "/health") {
    response.writeHead(200, {
      ...corsHeaders,
      "content-type": "application/json",
    })
    response.end(JSON.stringify({ ok: true, rooms: rooms.size }))
    return
  }

  response.writeHead(404)
  response.end("Not found")
})

const wss = new WebSocketServer({
  path: PATHNAME,
  server,
})

async function canReuseExistingServer() {
  try {
    const response = await fetch(`http://127.0.0.1:${PORT}/health`)
    if (!response.ok) {
      return false
    }

    const payload = await response.json().catch(() => null)
    return payload?.ok === true && typeof payload.rooms === "number"
  } catch {
    return false
  }
}

let handledStartupError = false

async function handleStartupError(error) {
  if (handledStartupError) {
    return
  }

  handledStartupError = true

  if (error?.code === "EADDRINUSE" && (await canReuseExistingServer())) {
    console.log(
      `Motion signaling server already listening on ws://localhost:${PORT}${PATHNAME}`
    )
    process.exit(0)
  }

  console.error(error)
  process.exit(1)
}

wss.on("connection", (socket) => {
  socket.on("message", (data) => {
    let message
    try {
      message = JSON.parse(String(data))
    } catch {
      send(socket, { message: "Signal message must be JSON", type: "error" })
      return
    }

    if (message.type === "join") {
      joinRoom(socket, message)
      return
    }

    if (
      message.type === "offer" ||
      message.type === "answer" ||
      message.type === "ice-candidate"
    ) {
      forwardToRoom(socket, message)
      return
    }

    send(socket, { message: `Unsupported signal type: ${message.type}`, type: "error" })
  })

  socket.on("close", () => leaveRoom(socket))
  socket.on("error", () => leaveRoom(socket))
})

server.on("error", (error) => {
  void handleStartupError(error)
})

wss.on("error", (error) => {
  void handleStartupError(error)
})

server.listen(PORT, HOST, () => {
  console.log(
    `Motion signaling server listening on ws://${HOST}:${PORT}${PATHNAME}`
  )
})

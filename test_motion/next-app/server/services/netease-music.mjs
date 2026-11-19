import crypto from "node:crypto"

const NETEASE_OPEN_API_BASE_URL = "https://openapi.music.163.com"
const NETEASE_PUBLIC_API_BASE_URL = "https://music.163.com"
const NETEASE_EXAMPLE_API_BASE_URL = "https://apis.netstart.cn/music"
const DEFAULT_FALLBACK_TRACK_IDS = ["186016", "32785674", "534542079"]

function buildPublicTrackPlayUrl(trackId) {
  return `${NETEASE_PUBLIC_API_BASE_URL}/song/media/outer/url?id=${encodeURIComponent(String(trackId))}.mp3`
}

function createTimestamp() {
  return Date.now().toString()
}

function wrapPemBlock(type, value) {
  const normalized = String(value ?? "")
    .replace(/-----BEGIN [^-]+-----/g, "")
    .replace(/-----END [^-]+-----/g, "")
    .replace(/\s+/g, "")
    .trim()

  if (!normalized) {
    return ""
  }

  const lines = normalized.match(/.{1,64}/g) ?? []
  return `-----BEGIN ${type}-----\n${lines.join("\n")}\n-----END ${type}-----`
}

function getOpenApiConfig() {
  const appId = process.env.NETEASE_OPEN_API_APP_ID ?? ""
  const appSecret = process.env.NETEASE_OPEN_API_APP_SECRET ?? ""
  const publicKey = wrapPemBlock(
    "PUBLIC KEY",
    process.env.NETEASE_OPEN_API_PUBLIC_KEY
  )
  const privateKey = wrapPemBlock(
    "PRIVATE KEY",
    process.env.NETEASE_OPEN_API_PRIVATE_KEY
  )
  const accessToken = process.env.NETEASE_OPEN_API_ACCESS_TOKEN ?? ""

  return {
    accessToken,
    appId,
    appSecret,
    privateKey,
    publicKey,
  }
}

function buildSignatureContent(parameters) {
  return Object.entries(parameters)
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .sort(([leftKey], [rightKey]) => leftKey.localeCompare(rightKey))
    .map(([key, value]) => `${key}=${value}`)
    .join("&")
}

function signWithPrivateKey(content, privateKey) {
  const signer = crypto.createSign("RSA-SHA256")
  signer.update(content, "utf8")
  signer.end()
  return signer.sign(privateKey, "base64")
}

function getOpenApiDevicePayload() {
  return JSON.stringify({
    brand: process.env.NETEASE_OPEN_API_DEVICE_BRAND ?? "Trae",
    channel: process.env.NETEASE_OPEN_API_DEVICE_CHANNEL ?? "desktop",
    clientip: process.env.NETEASE_OPEN_API_DEVICE_CLIENT_IP ?? "127.0.0.1",
    model: process.env.NETEASE_OPEN_API_DEVICE_MODEL ?? "Desktop",
    os: process.env.NETEASE_OPEN_API_DEVICE_OS ?? "windows",
    osver: process.env.NETEASE_OPEN_API_DEVICE_OS_VERSION ?? "11",
  })
}

function mapArtistName(artist) {
  if (typeof artist === "string") {
    return artist
  }

  if (!artist || typeof artist !== "object") {
    return ""
  }

  return (
    artist.name ??
    artist.artistName ??
    artist.nickname ??
    artist.nickName ??
    ""
  )
}

function normalizeTrack(rawTrack, source) {
  if (!rawTrack || typeof rawTrack !== "object") {
    return null
  }

  const album = rawTrack.album ?? rawTrack.al ?? {}
  const artists =
    rawTrack.artists ??
    rawTrack.ar ??
    rawTrack.artistList ??
    rawTrack.singers ??
    []
  const coverUrl =
    album.picUrl ??
    album.coverUrl ??
    rawTrack.picUrl ??
    rawTrack.coverUrl ??
    rawTrack.albumPicUrl ??
    ""
  const playUrl =
    rawTrack.url ??
    rawTrack.playUrl ??
    rawTrack.songUrl ??
    rawTrack.audioUrl ??
    buildPublicTrackPlayUrl(rawTrack.id ?? rawTrack.songId ?? rawTrack.trackId ?? "")
  const durationMs = Number(rawTrack.duration ?? rawTrack.dt ?? 0)
  const id = String(rawTrack.id ?? rawTrack.songId ?? rawTrack.trackId ?? "")
  const title = String(rawTrack.name ?? rawTrack.title ?? rawTrack.songName ?? "")
  const artistNames = Array.isArray(artists)
    ? artists.map(mapArtistName).filter(Boolean).join(" / ")
    : ""

  if (!id || !title) {
    return null
  }

  return {
    albumArt: coverUrl,
    artists: artistNames || "网易云音乐",
    duration: Math.max(1, Math.round(durationMs / 1000)),
    id,
    playUrl,
    source,
    title,
  }
}

function collectTrackCandidates(responseBody) {
  const candidates = []

  const pushValue = (value) => {
    if (Array.isArray(value)) {
      candidates.push(...value)
      return
    }

    if (value && typeof value === "object") {
      candidates.push(value)
    }
  }

  pushValue(responseBody?.songs)
  pushValue(responseBody?.records)
  pushValue(responseBody?.data?.songs)
  pushValue(responseBody?.data?.records)
  pushValue(responseBody?.data)
  pushValue(responseBody?.result?.songs)
  pushValue(responseBody?.result)

  return candidates
    .map((track) => normalizeTrack(track, "openapi"))
    .filter(Boolean)
}

async function callOpenApiSongDetail(trackId, strategy, config) {
  const commonParameters = {
    appId: config.appId,
    bizContent: JSON.stringify({
      songid: String(trackId),
      trialscene: "daily_trial",
      withurl: true,
    }),
    device: getOpenApiDevicePayload(),
    signType: "rsa_sha256",
    timestamp: Date.now().toString(),
  }
  const signingParameters = { ...commonParameters }

  if (strategy.includeAccessToken && config.accessToken) {
    signingParameters.accessToken = config.accessToken
  }

  const signatureContent = buildSignatureContent(signingParameters)
  const signedParameters = new URLSearchParams(signingParameters)
  signedParameters.set("sign", signWithPrivateKey(signatureContent, config.privateKey))

  const response = await fetch(
    `${NETEASE_OPEN_API_BASE_URL}/openapi/music/basic/song/detail/get/v2?${signedParameters.toString()}`,
    {
      headers: {
        accept: "application/json",
        "user-agent": "neurafit-motion-desktop/1.0",
      },
    }
  )

  const responseBody = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      responseBody?.message ??
      responseBody?.msg ??
      `网易云开放平台请求失败: ${response.status}`
    const error = new Error(message)
    error.statusCode = response.status
    error.responseBody = responseBody
    throw error
  }

  const businessCode =
    typeof responseBody?.code === "number" || typeof responseBody?.code === "string"
      ? Number(responseBody.code)
      : 200
  if (!Number.isNaN(businessCode) && businessCode !== 200) {
    const message =
      responseBody?.message ??
      responseBody?.msg ??
      `网易云开放平台业务返回异常: ${businessCode}`
    const error = new Error(message)
    error.statusCode = businessCode
    error.responseBody = responseBody
    throw error
  }

  const tracks = collectTrackCandidates(responseBody)
  if (tracks.length === 0) {
    const error = new Error("网易云开放平台返回成功，但未解析到歌曲数据")
    error.responseBody = responseBody
    throw error
  }

  return tracks[0]
}

async function loadTracksFromOpenApi(trackIds) {
  const config = getOpenApiConfig()
  if (!config.appId || !config.privateKey) {
    throw new Error("网易云开放平台凭证未配置完整")
  }

  const strategies = [
    { includeAccessToken: Boolean(config.accessToken) },
    { includeAccessToken: false },
  ]

  const tracks = []
  let lastError = null

  for (const trackId of trackIds) {
    let track = null

    for (const strategy of strategies) {
      try {
        track = await callOpenApiSongDetail(trackId, strategy, config)
        break
      } catch (error) {
        lastError = error
      }
    }

    if (!track) {
      throw lastError ?? new Error("网易云开放平台请求失败")
    }

    tracks.push(track)
  }

  return tracks
}

async function loadTracksFromPublicApi(trackIds) {
  const requestUrl =
    `${NETEASE_EXAMPLE_API_BASE_URL}/song/detail?` +
    new URLSearchParams({
      ids: trackIds.join(","),
      timestamp: createTimestamp(),
    }).toString()
  const response = await fetch(
    requestUrl,
    {
      headers: {
        accept: "application/json",
        "user-agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
      },
    }
  )

  const responseBody = await response.json().catch(() => null)
  if (!response.ok || !Array.isArray(responseBody?.songs)) {
    const message =
      responseBody?.message ??
      responseBody?.msg ??
      `网易云公共歌曲接口请求失败: ${response.status}`
    throw new Error(message)
  }

  const tracks = responseBody.songs
    .map((track) => normalizeTrack(track, "public-example"))
    .filter(Boolean)

  if (tracks.length === 0) {
    throw new Error("网易云公共歌曲接口返回成功，但未解析到歌曲数据")
  }

  return tracks
}

export async function getFeaturedNeteaseTracks() {
  const configuredTrackIds = String(
    process.env.NETEASE_OPEN_API_FEATURED_TRACK_IDS ?? ""
  )
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean)
  const trackIds =
    configuredTrackIds.length > 0 ? configuredTrackIds : DEFAULT_FALLBACK_TRACK_IDS

  try {
    const exampleTracks = await loadTracksFromPublicApi(trackIds)
    return {
      songs: exampleTracks,
      source: "public-example",
    }
  } catch (publicError) {
    try {
      const tracks = await loadTracksFromOpenApi(trackIds)
      return {
        error: publicError instanceof Error ? publicError.message : String(publicError),
        songs: tracks,
        source: "openapi",
      }
    } catch (openApiError) {
      const fallbackTracks = await loadTracksFromPublicApi(trackIds).catch(() => [])
      return {
        error:
          publicError instanceof Error
            ? publicError.message
            : openApiError instanceof Error
              ? openApiError.message
              : String(openApiError),
        songs: fallbackTracks,
        source: fallbackTracks.length > 0 ? "public-example" : "public-fallback",
      }
    }
  }
}

export async function searchNeteaseTracks(queryText, limit = 8) {
  const normalizedQuery = String(queryText ?? "").trim()
  if (!normalizedQuery) {
    return {
      songs: [],
      source: "public-search",
    }
  }

  const response = await fetch(
    `${NETEASE_EXAMPLE_API_BASE_URL}/search?` +
      new URLSearchParams({
        keywords: normalizedQuery,
        limit: String(Math.max(1, Math.min(limit, 20))),
        offset: "0",
        timestamp: createTimestamp(),
        type: "1",
      }).toString(),
    {
      headers: {
        accept: "application/json",
        "user-agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
      },
    }
  )

  const responseBody = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      responseBody?.message ??
      responseBody?.msg ??
      `网易云搜索失败: ${response.status}`
    throw new Error(message)
  }

  const tracks = collectTrackCandidates(responseBody)
    .map((track) => ({ ...track, source: "public-search" }))
    .slice(0, Math.max(1, Math.min(limit, 20)))

  return {
    songs: tracks,
    source: "public-search",
  }
}

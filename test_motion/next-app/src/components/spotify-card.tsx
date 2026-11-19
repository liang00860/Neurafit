import {
  AlertCircle,
  Heart,
  LoaderCircle,
  Pause,
  Play,
  SkipBack,
  SkipForward,
  Volume2,
} from "lucide-react"
import { useEffect, useMemo, useRef, useState } from "react"

import { apiRequest } from "@/lib/api-client"

export type Song = {
  albumArt: string
  artists: string
  duration: number
  id?: string
  playUrl?: string | null
  source?: string
  title: string
}

type SpotifyCardProps = {
  className?: string
  command?: MusicSearchCommand | null
  onCommandHandled?: (requestId: string) => void
  songs?: Song[]
}

type FeaturedSongsResponse = {
  error?: string
  songs: Song[]
  source: string
}

export type MusicSearchCommand = {
  autoPlay?: boolean
  query: string
  requestId: string
}

const fallbackSongs: Song[] = [
  {
    albumArt:
      "https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&q=80&w=600",
    artists: "网易云音乐",
    duration: 214,
    source: "fallback",
    title: "正在连接你的歌单",
  },
  {
    albumArt:
      "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&q=80&w=600",
    artists: "网易云音乐",
    duration: 188,
    source: "fallback",
    title: "实时音乐卡片",
  },
  {
    albumArt:
      "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&q=80&w=600",
    artists: "网易云音乐",
    duration: 246,
    source: "fallback",
    title: "等待接口返回",
  },
]

function formatTime(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.floor(seconds % 60)
  return `${minutes}:${remainingSeconds.toString().padStart(2, "0")}`
}

function NeteaseCloudLogo() {
  return (
    <svg viewBox="0 0 64 64" className="size-7" aria-hidden="true">
      <circle cx="32" cy="32" r="26" fill="none" stroke="currentColor" strokeWidth="5" />
      <path
        d="M35 17.5a12.5 12.5 0 1 1-11.2 18"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="5"
      />
      <circle cx="24" cy="38.5" r="4.2" fill="currentColor" />
    </svg>
  )
}

function getSourceLabel(source: string) {
  switch (source) {
    case "public-example":
      return "示例接口"
    case "public-search":
      return "搜索结果"
    case "openapi":
      return "开放平台"
    case "public-fallback":
      return "真实曲库"
    default:
      return "加载中"
  }
}

export function NeteaseCloudCard({
  className = "",
  command = null,
  onCommandHandled,
  songs,
}: SpotifyCardProps) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const isPlayingRef = useRef(false)
  const pendingAutoPlayRef = useRef(false)
  const [remoteSongs, setRemoteSongs] = useState<Song[]>([])
  const [loading, setLoading] = useState(!songs?.length)
  const [loadError, setLoadError] = useState("")
  const [source, setSource] = useState(songs?.length ? "props" : "loading")
  const [isPlaying, setIsPlaying] = useState(false)
  const [isLiked, setIsLiked] = useState(false)
  const [volume, setVolume] = useState(72)
  const [currentSongIndex, setCurrentSongIndex] = useState(0)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [activeQuery, setActiveQuery] = useState("")

  useEffect(() => {
    if (songs?.length) return

    let cancelled = false

    async function loadFeaturedSongs() {
      setLoading(true)
      try {
        const response = await apiRequest<FeaturedSongsResponse>(
          "/music/netease/featured"
        )
        if (cancelled) {
          return
        }

        setRemoteSongs(response.songs)
        setSource(response.source)
        setLoadError(response.error ?? "")
      } catch (error) {
        if (cancelled) {
          return
        }

        setRemoteSongs([])
        setSource("fallback")
        setLoadError(error instanceof Error ? error.message : String(error))
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadFeaturedSongs()

    return () => {
      cancelled = true
    }
  }, [songs])

  useEffect(() => {
    if (!command?.requestId || songs?.length) {
      return
    }

    const activeCommand = command
    let cancelled = false

    async function searchTracks() {
      const query = activeCommand.query.trim()
      if (!query) {
        onCommandHandled?.(activeCommand.requestId)
        return
      }

      setLoading(true)
      setLoadError("")
      setActiveQuery(query)

      try {
        const response = await apiRequest<FeaturedSongsResponse>(
          `/music/netease/search?q=${encodeURIComponent(query)}&limit=8`
        )

        if (cancelled) {
          return
        }

        setRemoteSongs(response.songs)
        setSource(response.source)
        setCurrentSongIndex(0)
        setElapsedSeconds(0)
        setIsLiked(false)

        if (response.songs.length === 0) {
          setLoadError(`没有找到与“${query}”相关的歌曲`)
          pendingAutoPlayRef.current = false
          setIsPlaying(false)
        } else {
          pendingAutoPlayRef.current = Boolean(activeCommand.autoPlay)
          if (!activeCommand.autoPlay) {
            setIsPlaying(false)
          }
        }
      } catch (error) {
        if (cancelled) {
          return
        }

        pendingAutoPlayRef.current = false
        setIsPlaying(false)
        setLoadError(error instanceof Error ? error.message : String(error))
      } finally {
        if (!cancelled) {
          setLoading(false)
          onCommandHandled?.(activeCommand.requestId)
        }
      }
    }

    void searchTracks()

    return () => {
      cancelled = true
    }
  }, [command, onCommandHandled, songs])

  const playlist = useMemo(() => {
    if (songs?.length) {
      return songs
    }
    if (remoteSongs.length > 0) {
      return remoteSongs
    }
    return fallbackSongs
  }, [remoteSongs, songs])

  const normalizedSongIndex =
    playlist.length === 0
      ? 0
      : ((currentSongIndex % playlist.length) + playlist.length) % playlist.length
  const currentSong = playlist[normalizedSongIndex] ?? fallbackSongs[0]
  const duration = Math.max(1, currentSong.duration)
  const progress = Math.min(100, (elapsedSeconds / duration) * 100)

  useEffect(() => {
    isPlayingRef.current = isPlaying
  }, [isPlaying])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) {
      return
    }

    const handleTimeUpdate = () => {
      setElapsedSeconds(audio.currentTime)
    }

    const handlePlay = () => {
      isPlayingRef.current = true
      setIsPlaying(true)
    }

    const handlePause = () => {
      isPlayingRef.current = false
      setIsPlaying(false)
    }

    const handleEnded = () => {
      setCurrentSongIndex((index) => index + 1)
      setElapsedSeconds(0)
      setIsLiked(false)
    }

    const handleError = () => {
      setLoadError(`歌曲“${currentSong.title}”暂时无法播放`)
      setIsPlaying(false)
    }

    audio.addEventListener("timeupdate", handleTimeUpdate)
    audio.addEventListener("play", handlePlay)
    audio.addEventListener("pause", handlePause)
    audio.addEventListener("ended", handleEnded)
    audio.addEventListener("error", handleError)

    return () => {
      audio.removeEventListener("timeupdate", handleTimeUpdate)
      audio.removeEventListener("play", handlePlay)
      audio.removeEventListener("pause", handlePause)
      audio.removeEventListener("ended", handleEnded)
      audio.removeEventListener("error", handleError)
    }
  }, [currentSong.title])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) {
      return
    }

    audio.volume = volume / 100
  }, [volume])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) {
      return
    }

    audio.pause()
    audio.src = currentSong.playUrl ?? ""
    audio.load()

    const shouldAutoPlay = pendingAutoPlayRef.current || isPlayingRef.current
    pendingAutoPlayRef.current = false

    if (shouldAutoPlay && currentSong.playUrl) {
      void audio.play().catch((error) => {
        setLoadError(
          error instanceof Error ? error.message : "当前歌曲无法自动播放"
        )
        setIsPlaying(false)
      })
    }
  }, [currentSong.id, currentSong.playUrl])

  const selectSong = (nextIndex: number) => {
    setCurrentSongIndex(nextIndex)
    setElapsedSeconds(0)
    setIsLiked(false)
    setLoadError("")
  }

  const togglePlay = () => {
    const audio = audioRef.current
    if (!audio || !currentSong.playUrl) {
      setLoadError("当前歌曲暂无可用音源")
      return
    }

    setLoadError("")
    if (audio.paused) {
      void audio.play().catch((error) => {
        setLoadError(
          error instanceof Error ? error.message : "当前歌曲无法播放"
        )
        setIsPlaying(false)
      })
      return
    }

    audio.pause()
  }

  return (
    <section
      className={`group relative w-[320px] overflow-hidden rounded-xl text-white ${className}`}
      style={{
        background:
          "radial-gradient(25% 40% at 50% 30%, rgba(255,59,48,0.18), rgba(58, 10, 14, 0.98))",
        boxShadow:
          "0px 1px 0px 0px rgba(255,255,255,0.08) inset, 0px 0px 25px 5px rgba(255,59,48,0.08), 0px 0px 40px 20px rgba(255,59,48,0.04), 0 0 0 1px rgba(255,255,255,0.05) inset, 0 10px 30px -5px rgba(0,0,0,0.3)",
      }}
    >
      <audio ref={audioRef} preload="none" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(255,76,76,0.32),rgba(255,76,76,0.08)_34%,transparent_65%)]" />
      <div className="absolute inset-0 bg-[linear-gradient(180deg,#381015_0%,#17090d_38%,#0b0b10_100%)]" />

      <div className="relative z-10 p-5">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex size-14 items-center justify-center rounded-xl bg-black/85 text-[#ff4e45] shadow-[0_0_15px_rgba(255,59,48,0.25)] transition-transform duration-500 group-hover:scale-[1.04]">
              <NeteaseCloudLogo />
            </div>
            <div>
              <div className="bg-gradient-to-r from-white to-[#ffb3ad] bg-clip-text text-lg font-medium text-transparent">
                网易云
              </div>
              <div className="text-xs font-medium text-[#ff8a83]">
                {getSourceLabel(source)}
              </div>
            </div>
          </div>

          <button
            aria-label={isPlaying ? "Pause" : "Play"}
            className="flex size-9 items-center justify-center rounded-full bg-black/20 text-white/85 transition-all duration-300 group-hover:bg-[#ff4e45] group-hover:text-white"
            type="button"
            onClick={togglePlay}
          >
            {isPlaying ? <Pause size={16} /> : <Play size={16} className="ml-0.5" />}
          </button>
        </div>

        <p className="mb-4 text-sm font-light text-gray-200/90">
          {activeQuery
            ? `正在为你播放“${activeQuery}”的搜索结果`
            : "同步真实网易云歌曲数据，保留原 Spotify 卡片的紧凑布局。"}
        </p>

        {(loadError || loading) && (
          <div className="mb-4 flex items-center gap-2 rounded-lg border border-white/10 bg-black/20 px-3 py-2 text-[11px] text-white/75">
            {loadError ? (
              <AlertCircle size={13} className="shrink-0 text-[#ff8a83]" />
            ) : (
              <LoaderCircle size={13} className="shrink-0 animate-spin text-[#ff8a83]" />
            )}
            <span className="truncate">
              {loadError
                ? `开放平台暂不可用：${loadError}`
                : activeQuery
                  ? `正在搜索“${activeQuery}”`
                  : "正在读取网易云歌曲信息"}
            </span>
          </div>
        )}

        <div className="mx-auto mb-4 size-48 overflow-hidden rounded-xl shadow-[0_8px_24px_rgba(0,0,0,0.35)] transition-transform duration-500 group-hover:scale-[1.02]">
          <div className="relative size-full">
            <img
              src={currentSong.albumArt}
              alt={`${currentSong.title} cover`}
              className="size-full object-cover"
              draggable={false}
            />
            <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(0,0,0,0.05),rgba(0,0,0,0.5))]" />
            <button
              aria-label={isLiked ? "取消喜欢" : "标记喜欢"}
              className="absolute right-3 bottom-3 flex size-7 items-center justify-center rounded-full bg-black/45 text-white backdrop-blur-sm transition-colors hover:bg-black/60"
              type="button"
              onClick={() => setIsLiked((liked) => !liked)}
            >
              <Heart
                size={14}
                fill={isLiked ? "#ff3b30" : "none"}
                stroke={isLiked ? "#ff3b30" : "white"}
              />
            </button>
          </div>
        </div>

        <div className="overflow-hidden rounded-b-xl border-t border-white/5 bg-black/90 p-3 backdrop-blur-md">
          <div className="mb-3 h-1.5 w-full overflow-hidden rounded-full bg-white/10">
            <div
              className="h-full rounded-full bg-[linear-gradient(90deg,#ff7a72,#ff3b30)] transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>

          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="min-w-0 text-white">
              <div className="truncate text-xs font-medium text-white">
                {currentSong.title}
              </div>
              <div className="mt-0.5 truncate text-xs text-gray-400">
                {currentSong.artists}
              </div>
            </div>
            <div className="shrink-0 text-xs text-gray-400 tabular-nums">
              {formatTime(elapsedSeconds)} / {formatTime(duration)}
            </div>
          </div>

          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                aria-label="上一首"
                className="flex size-7 items-center justify-center rounded-full text-white transition-colors hover:bg-white/10"
                type="button"
                onClick={() =>
                  selectSong(
                    normalizedSongIndex === 0
                      ? playlist.length - 1
                      : normalizedSongIndex - 1
                  )
                }
              >
                <SkipBack size={14} />
              </button>
              <button
                aria-label={isPlaying ? "暂停" : "播放"}
                className="flex size-8 items-center justify-center rounded-full bg-white text-black transition-transform hover:scale-105"
                type="button"
                onClick={togglePlay}
              >
                {isPlaying ? <Pause size={15} /> : <Play size={15} className="ml-0.5" />}
              </button>
              <button
                aria-label="下一首"
                className="flex size-7 items-center justify-center rounded-full text-white transition-colors hover:bg-white/10"
                type="button"
                onClick={() => selectSong(normalizedSongIndex + 1)}
              >
                <SkipForward size={14} />
              </button>
            </div>

            <div className="flex items-center gap-2 text-gray-400">
              <Volume2 size={15} />
              <input
                aria-label="音量"
                className="w-16 accent-[#ff3b30]"
                max={100}
                min={0}
                type="range"
                value={volume}
                onChange={(event) => setVolume(Number(event.target.value))}
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

export const SpotifyCard = NeteaseCloudCard

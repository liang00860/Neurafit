const DEFAULT_SIGNALING_PORT = "8787"
const SIGNALING_PATH = "/motion-signaling"

export function getSignalingUrl() {
  const configuredUrl = import.meta.env.VITE_SIGNALING_URL
  if (configuredUrl?.trim()) {
    return configuredUrl.trim()
  }

  const port =
    import.meta.env.VITE_SIGNALING_PORT?.trim() || DEFAULT_SIGNALING_PORT
  const protocol =
    typeof window !== "undefined" && window.location.protocol === "https:"
      ? "wss"
      : "ws"
  const hostname =
    typeof window !== "undefined" && window.location.hostname
      ? window.location.hostname
      : "localhost"

  return `${protocol}://${hostname}:${port}${SIGNALING_PATH}`
}

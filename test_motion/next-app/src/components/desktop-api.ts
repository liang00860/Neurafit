import { apiRequest } from "@/lib/api-client"

export type RemoteWindowBounds = {
  height: number
  width: number
  x: number
  y: number
}

export type RemoteDesktopWindowState = RemoteWindowBounds & {
  appKey: string
  maximized: boolean
  minimized: boolean
  open: boolean
  restoreBounds: RemoteWindowBounds | null
  zIndex: number
}

export type RemoteDesktopDockItem = {
  appKey: string
  pinned: boolean
  showIndicator: boolean
  sortOrder: number
}

export type RemoteDesktopState = {
  dockItems: RemoteDesktopDockItem[]
  preferences: {
    appKey: string
    key: string
    value: unknown
  }[]
  windows: RemoteDesktopWindowState[]
}

export function getDesktopState() {
  return apiRequest<RemoteDesktopState>("/desktop/state")
}

export function saveDesktopWindowState(
  appKey: string,
  state: Omit<RemoteDesktopWindowState, "appKey">
) {
  return apiRequest<RemoteDesktopWindowState>(
    `/desktop/windows/${encodeURIComponent(appKey)}`,
    {
      body: JSON.stringify(state),
      method: "PUT",
    }
  )
}

import { query } from "../db/mysql.mjs"

const PROFILE_ID = 1

export async function listDesktopState() {
  const [windows, dockItems, preferences] = await Promise.all([
    query(
      `SELECT app_key, is_open, minimized, maximized, x, y, width, height, z_index, restore_bounds
       FROM desktop_windows
       WHERE profile_id = ?
       ORDER BY z_index`,
      [PROFILE_ID]
    ),
    query(
      `SELECT app_key, sort_order, pinned, show_indicator
       FROM desktop_dock_items
       WHERE profile_id = ?
       ORDER BY sort_order`,
      [PROFILE_ID]
    ),
    query(
      `SELECT app_key, preference_key, preference_value
       FROM app_preferences
       WHERE profile_id = ?`,
      [PROFILE_ID]
    ),
  ])

  return {
    dockItems: dockItems.map((item) => ({
      appKey: item.app_key,
      pinned: Boolean(item.pinned),
      showIndicator: Boolean(item.show_indicator),
      sortOrder: item.sort_order,
    })),
    preferences: preferences.map((preference) => ({
      appKey: preference.app_key,
      key: preference.preference_key,
      value: preference.preference_value,
    })),
    windows: windows.map((windowState) => ({
      appKey: windowState.app_key,
      height: windowState.height,
      maximized: Boolean(windowState.maximized),
      minimized: Boolean(windowState.minimized),
      open: Boolean(windowState.is_open),
      restoreBounds: windowState.restore_bounds,
      width: windowState.width,
      x: windowState.x,
      y: windowState.y,
      zIndex: windowState.z_index,
    })),
  }
}

export async function saveWindowState(appKey, state) {
  await query(
    `INSERT INTO desktop_windows
      (profile_id, app_key, is_open, minimized, maximized, x, y, width, height, z_index, restore_bounds)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
      is_open = VALUES(is_open), minimized = VALUES(minimized), maximized = VALUES(maximized),
      x = VALUES(x), y = VALUES(y), width = VALUES(width), height = VALUES(height),
      z_index = VALUES(z_index), restore_bounds = VALUES(restore_bounds)`,
    [
      PROFILE_ID,
      appKey,
      state.open ? 1 : 0,
      state.minimized ? 1 : 0,
      state.maximized ? 1 : 0,
      state.x,
      state.y,
      state.width,
      state.height,
      state.zIndex ?? 1,
      state.restoreBounds ? JSON.stringify(state.restoreBounds) : null,
    ]
  )

  return { appKey, ...state }
}

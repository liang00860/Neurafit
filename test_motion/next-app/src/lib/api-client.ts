const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api"
const AUTH_TOKEN_STORAGE_KEY = "neurafit.authToken"
const DEBUG_SERVER_URL = import.meta.env.VITE_DEBUG_SERVER_URL?.trim() ?? ""
const DEBUG_SESSION_ID = "calendar-delete-aborted"

function isCalendarDeleteRequest(path: string, options: RequestInit) {
  return (
    options.method === "DELETE" &&
    path.startsWith("/calendar/events/")
  )
}

function reportCalendarDeleteDebugEvent(
  location: string,
  msg: string,
  data: Record<string, unknown> = {}
) {
  if (!DEBUG_SERVER_URL) return
  void fetch(DEBUG_SERVER_URL, {
    body: JSON.stringify({
      source: "web",
      sessionId: DEBUG_SESSION_ID,
      runId: "pre-fix",
      hypothesisId: "H1",
      level: "info",
      location,
      msg: `[DEBUG] ${msg}`,
      data,
      ts: new Date().toISOString(),
    }),
    headers: {
      "content-type": "application/json",
    },
    keepalive: true,
    method: "POST",
  }).catch(() => undefined)
}

async function readErrorMessage(response: Response) {
  const contentType = response.headers.get("content-type") ?? ""

  if (contentType.includes("application/json")) {
    const errorBody = await response.json().catch(() => null)
    if (typeof errorBody?.message === "string" && errorBody.message.trim()) {
      return errorBody.message
    }
    if (typeof errorBody?.error === "string" && errorBody.error.trim()) {
      return errorBody.error
    }
  } else {
    const errorText = await response.text().catch(() => "")
    if (errorText.trim()) {
      return errorText.trim()
    }
  }

  if (response.status === 500) {
    return "API 服务异常或未启动，请确认后端服务正在运行"
  }

  return `API request failed: ${response.status}`
}

function readAuthToken() {
  if (typeof window === "undefined") {
    return ""
  }

  return window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)?.trim() ?? ""
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const isDeleteRequest = isCalendarDeleteRequest(path, options)
  const requestUrl = `${API_BASE_URL}${path}`
  const authToken = readAuthToken()

  if (isDeleteRequest) {
    // #region debug-point C:calendar-delete-request-start
    reportCalendarDeleteDebugEvent(
      "api-client.ts:apiRequest:start",
      "Calendar delete request started",
      {
        method: options.method ?? "GET",
        path,
        requestUrl,
      }
    )
    // #endregion
  }

  let response: Response
  try {
    response = await fetch(requestUrl, {
      headers: {
        ...(authToken ? { authorization: `Bearer ${authToken}` } : {}),
        "content-type": "application/json",
        ...options.headers,
      },
      ...options,
    })
  } catch (error) {
    if (isDeleteRequest) {
      // #region debug-point D:calendar-delete-request-failed
      reportCalendarDeleteDebugEvent(
        "api-client.ts:apiRequest:catch",
        "Calendar delete request threw before response",
        {
          errorName: error instanceof Error ? error.name : "unknown",
          errorMessage: error instanceof Error ? error.message : String(error),
          method: options.method ?? "GET",
          path,
          requestUrl,
        }
      )
      // #endregion
    }
    throw error
  }

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }

  if (response.status === 204) {
    if (isDeleteRequest) {
      // #region debug-point C:calendar-delete-request-success
      reportCalendarDeleteDebugEvent(
        "api-client.ts:apiRequest:success",
        "Calendar delete request completed",
        {
          method: options.method ?? "GET",
          path,
          requestUrl,
          status: response.status,
        }
      )
      // #endregion
    }
    return undefined as T
  }

  const contentType = response.headers.get("content-type") ?? ""
  if (contentType.includes("application/json")) {
    return response.json() as Promise<T>
  }

  return response.text() as Promise<T>
}

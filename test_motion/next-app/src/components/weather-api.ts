import { apiRequest } from "@/lib/api-client"

export type RemoteWeatherLocation = {
  id: string
  isDefault: boolean
  latitude: number | null
  longitude: number | null
  name: string
}

export function listWeatherLocations() {
  return apiRequest<RemoteWeatherLocation[]>("/weather/locations")
}

export function createWeatherLocation(input: {
  isDefault?: boolean
  latitude?: number | null
  longitude?: number | null
  name: string
}) {
  return apiRequest<RemoteWeatherLocation>("/weather/locations", {
    body: JSON.stringify(input),
    method: "POST",
  })
}

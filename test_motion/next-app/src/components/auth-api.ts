import { apiRequest } from "@/lib/api-client"

export type AuthUser = {
  avatarUrl: string | null
  createdAt: string
  email: string | null
  id: number
  lastLoginAt: string | null
  phone: string | null
  status: number
  username: string
}

export type AuthResponse = {
  token: string
  user: AuthUser
}

export function loginUser(payload: {
  identifier: string
  password: string
}) {
  return apiRequest<AuthResponse>("/auth/login", {
    body: JSON.stringify(payload),
    method: "POST",
  })
}

export function registerUser(payload: {
  email: string
  password: string
  username: string
}) {
  return apiRequest<AuthResponse>("/auth/register", {
    body: JSON.stringify(payload),
    method: "POST",
  })
}

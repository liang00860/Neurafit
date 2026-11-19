import { apiRequest } from "@/lib/api-client"

export type TrainingPlanActivityDate = {
  date: string
  day: string
  selected?: boolean
}

export type TrainingPlanActivityRow = {
  label: string
  value: string
}

export type TrainingPlanActivity = {
  dates: TrainingPlanActivityDate[]
  planTitle: string
  rows: TrainingPlanActivityRow[]
} | null

export function fetchTrainingPlanActivity() {
  return apiRequest<TrainingPlanActivity>("/training-plans/activity")
}

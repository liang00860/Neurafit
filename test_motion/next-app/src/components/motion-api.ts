import type { MotionPoseFrame } from "@/hooks/use-motion-webrtc"
import { apiRequest } from "@/lib/api-client"

export type SaveMotionSessionInput = {
  actionKey?: string
  calories: number
  durationSeconds: number
  endedAt: string
  poseFrames: MotionPoseFrame[]
  source: string
  startedAt: string
  totalScore: number
}

export async function saveMotionTrainingSession(input: SaveMotionSessionInput) {
  return apiRequest<{ id: string }>("/motion/sessions", {
    body: JSON.stringify({
      ...input,
      sets: [
        {
          averageScore: input.totalScore,
          completionRate: input.totalScore,
          durationSeconds: input.durationSeconds,
          reps: Math.max(1, Math.round(input.poseFrames.length / 12)),
          setIndex: 1,
        },
      ],
      summary: {
        frameCount: input.poseFrames.length,
        latestAdvice: input.poseFrames.at(-1)?.advice ?? "",
      },
    }),
    method: "POST",
  })
}

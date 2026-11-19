import { query, withTransaction } from "../db/mysql.mjs"

const PROFILE_ID = 1

function toMysqlDateTime(value) {
  if (!value) return null
  return new Date(value).toISOString().slice(0, 19).replace("T", " ")
}

async function getDefaultActionId(connection) {
  const [rows] = await connection.execute(
    "SELECT id FROM exercise_actions WHERE action_key = 'squat' AND deleted_at IS NULL LIMIT 1"
  )
  return rows[0]?.id ?? null
}

export async function createTrainingSession(input) {
  return withTransaction(async (connection) => {
    const actionId = input.actionKey
      ? (
          await connection.execute(
            "SELECT id FROM exercise_actions WHERE action_key = ? AND deleted_at IS NULL LIMIT 1",
            [input.actionKey]
          )
        )[0][0]?.id
      : await getDefaultActionId(connection)
    const startedAt = input.startedAt ?? new Date().toISOString()
    const endedAt = input.endedAt ?? null
    const [sessionResult] = await connection.execute(
      `INSERT INTO training_sessions
        (profile_id, action_id, source, started_at, ended_at, duration_seconds,
         calories, total_score, summary)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        PROFILE_ID,
        actionId,
        input.source ?? "web-demo",
        toMysqlDateTime(startedAt),
        toMysqlDateTime(endedAt),
        input.durationSeconds ?? 0,
        input.calories ?? 0,
        input.totalScore ?? null,
        JSON.stringify(input.summary ?? {}),
      ]
    )
    const sessionId = sessionResult.insertId

    if (Array.isArray(input.sets)) {
      for (const set of input.sets) {
        await connection.execute(
          `INSERT INTO training_sets
            (session_id, set_index, reps, duration_seconds, completion_rate, average_score)
           VALUES (?, ?, ?, ?, ?, ?)`,
          [
            sessionId,
            set.setIndex,
            set.reps ?? 0,
            set.durationSeconds ?? 0,
            set.completionRate ?? null,
            set.averageScore ?? null,
          ]
        )
      }
    }

    if (Array.isArray(input.poseFrames)) {
      for (const frame of input.poseFrames.slice(0, 30)) {
        const [frameResult] = await connection.execute(
          `INSERT INTO pose_frames
            (session_id, frame_timestamp, landmarks, connections, angles, confidence)
           VALUES (?, ?, ?, ?, ?, ?)`,
          [
            sessionId,
            toMysqlDateTime(frame.timestamp ?? new Date().toISOString()),
            JSON.stringify(frame.landmarks ?? []),
            JSON.stringify(frame.connections ?? []),
            JSON.stringify(frame.angles ?? {}),
            frame.confidence ?? 0,
          ]
        )

        for (const correction of frame.corrections ?? []) {
          await connection.execute(
            `INSERT INTO motion_corrections
              (session_id, pose_frame_id, correction_type, severity, message, occurred_at)
             VALUES (?, ?, ?, ?, ?, ?)`,
            [
              sessionId,
              frameResult.insertId,
              correction.type ?? "form",
              correction.severity ?? "info",
              correction.message,
              toMysqlDateTime(frame.timestamp ?? new Date().toISOString()),
            ]
          )
        }
      }
    }

    return { id: String(sessionId), startedAt, endedAt }
  })
}

export async function listTrainingSessions() {
  const rows = await query(
    `SELECT ts.id, ts.source, ts.started_at, ts.ended_at, ts.duration_seconds,
            ts.calories, ts.total_score, ts.summary, ea.name AS action_name
     FROM training_sessions ts
     LEFT JOIN exercise_actions ea ON ea.id = ts.action_id
     WHERE ts.profile_id = ? AND ts.deleted_at IS NULL
     ORDER BY ts.started_at DESC
     LIMIT 50`,
    [PROFILE_ID]
  )

  return rows.map((row) => ({
    actionName: row.action_name ?? "Session",
    calories: Number(row.calories ?? 0),
    durationSeconds: row.duration_seconds,
    endedAt: row.ended_at,
    id: String(row.id),
    source: row.source,
    startedAt: row.started_at,
    summary: row.summary,
    totalScore: row.total_score == null ? null : Number(row.total_score),
  }))
}

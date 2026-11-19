import "../env.mjs"
import { readFile } from "node:fs/promises"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import mysql from "mysql2/promise"

import { closePool, getConnectionConfig, getDatabaseName } from "./mysql.mjs"

const __dirname = dirname(fileURLToPath(import.meta.url))
const schemaPath = join(__dirname, "schema.sql")

async function migrate() {
  const sql = await readFile(schemaPath, "utf8")
  const connection = await mysql.createConnection({
    ...getConnectionConfig(),
    multipleStatements: true,
  })

  try {
    await connection.query(sql)
    console.log(`Database migration completed for ${getDatabaseName()}`)
  } finally {
    await connection.end()
    await closePool()
  }
}

migrate().catch((error) => {
  console.error(error)
  process.exitCode = 1
})

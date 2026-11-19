import fs from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const serverDir = path.dirname(fileURLToPath(import.meta.url))
const appRoot = path.resolve(serverDir, "..")

function parseEnvValue(rawValue) {
  const value = rawValue.trim()
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1)
  }

  return value
}

function loadEnvFile(filePath, values) {
  if (!fs.existsSync(filePath)) {
    return
  }

  const content = fs.readFileSync(filePath, "utf8")
  for (const line of content.split(/\r?\n/)) {
    const trimmedLine = line.trim()
    if (!trimmedLine || trimmedLine.startsWith("#")) {
      continue
    }

    const separatorIndex = trimmedLine.indexOf("=")
    if (separatorIndex === -1) {
      continue
    }

    const key = trimmedLine.slice(0, separatorIndex).trim()
    const value = trimmedLine.slice(separatorIndex + 1)
    if (key) {
      values.set(key, parseEnvValue(value))
    }
  }
}

const loadedValues = new Map()
loadEnvFile(path.join(appRoot, ".env"), loadedValues)
loadEnvFile(path.join(appRoot, ".env.local"), loadedValues)

for (const [key, value] of loadedValues) {
  if (process.env[key] === undefined) {
    process.env[key] = value
  }
}

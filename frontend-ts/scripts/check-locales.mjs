/**
 * Prüft die Sprachkataloge in src/branding/locales: Alle Dateien müssen genau dieselben Schlüssel haben wie de.json,
 * die Quelle, und kein Text darf leer sein. Läuft mit npm run lint. Ein fehlender Schlüssel fiele sonst erst im
 * Browser auf, wo dann der Schlüssel selbst oder der deutsche Text stünde.
 */
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const localesDir = fileURLToPath(new URL('../src/branding/locales/', import.meta.url))
const SOURCE = 'de.json'

/** Alle Schlüssel eines Katalogs flach, z. B. solrmanager.cores.title, Listen als key[0] */
function keysOf(value, prefix = '') {
  if (Array.isArray(value)) {
    return value.flatMap((item, index) => keysOf(item, `${prefix}[${index}]`))
  }
  if (typeof value === 'object' && value !== null) {
    return Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix === '' ? key : `${prefix}.${key}`))
  }
  if (typeof value !== 'string' || value.trim() === '') {
    return [`${prefix} (LEER)`]
  }
  return [prefix]
}

const files = readdirSync(localesDir).filter((name) => name.endsWith('.json'))
const catalogs = Object.fromEntries(
  files.map((name) => [name, keysOf(JSON.parse(readFileSync(join(localesDir, name), 'utf8')))]),
)
const sourceKeys = new Set(catalogs[SOURCE])
let problems = 0

for (const [name, keys] of Object.entries(catalogs)) {
  const own = new Set(keys)
  for (const key of keys) {
    if (key.endsWith(' (LEER)')) {
      console.error(`${name}: ${key}`)
      problems++
    }
  }
  if (name === SOURCE) {
    continue
  }
  for (const key of sourceKeys) {
    if (!own.has(key)) {
      console.error(`${name}: fehlt ${key}`)
      problems++
    }
  }
  for (const key of own) {
    if (!sourceKeys.has(key)) {
      console.error(`${name}: überzählig ${key}`)
      problems++
    }
  }
}

if (problems > 0) {
  console.error(`${problems} Problem(e) in den Sprachkatalogen`)
  process.exit(1)
}
console.log(`Sprachkataloge vollständig: ${files.join(', ')}, ${sourceKeys.size} Schlüssel`)

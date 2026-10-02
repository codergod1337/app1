import type { Users } from '../users/Users.ts'

/** Zufall aus einem Text: derselbe Text ergibt immer dieselbe Folge. So sieht jedes Profil anders, aber immer gleich aus. */
export function seededRandom(seed: string): () => number {
  let hash = 1779033703 ^ seed.length
  for (let i = 0; i < seed.length; i++) {
    hash = Math.imul(hash ^ seed.charCodeAt(i), 3432918353)
    hash = (hash << 13) | (hash >>> 19)
  }
  return () => {
    hash = Math.imul(hash ^ (hash >>> 16), 2246822507)
    hash = Math.imul(hash ^ (hash >>> 13), 3266489909)
    hash ^= hash >>> 16
    return (hash >>> 0) / 4294967296
  }
}

/** Vor- und Nachname, sonst der username, sonst die E-Mail */
export function usersDisplayName(users: Users): string {
  const fullName = [users.vorname, users.nachname].filter(Boolean).join(' ')
  return fullName || users.username || users.email
}

/** Zwei Buchstaben fürs Profilbild, solange es kein hochgeladenes gibt */
export function usersInitials(users: Users): string {
  if (users.vorname && users.nachname) {
    return (users.vorname[0] + users.nachname[0]).toUpperCase()
  }
  return (users.vorname || users.username || users.email).slice(0, 2).toUpperCase()
}

/** Verlauf fürs Profilbild, die Farbe kommt aus der guid und bleibt deshalb immer gleich */
export function usersAvatarBackground(guid: string): string {
  const hue = Math.floor(seededRandom(guid)() * 360)
  return `linear-gradient(135deg, hsl(${hue} 70% 58%), hsl(${(hue + 40) % 360} 65% 36%))`
}

/** Ganze Jahre und restliche Monate seit dem Zeitpunkt */
export function membershipLength(since: Date, now: Date): { years: number; months: number } {
  let years = now.getFullYear() - since.getFullYear()
  let months = now.getMonth() - since.getMonth()
  if (now.getDate() < since.getDate()) {
    months--
  }
  if (months < 0) {
    years--
    months += 12
  }
  return { years, months }
}

/** Link zu einer Eingabe, die eine Webadresse sein könnte. Nur http und https, sonst null. */
export function toWebUrl(text: string | null): string | null {
  if (text === null) {
    return null
  }
  if (/^https?:\/\/\S+$/i.test(text)) {
    return text
  }
  // ohne Schema, aber wie eine Adresse: example.de/pfad
  if (/^[\w-]+(\.[\w-]+)+(\/\S*)?$/.test(text)) {
    return `https://${text}`
  }
  return null
}

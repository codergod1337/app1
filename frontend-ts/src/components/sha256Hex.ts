/**
 * SHA-256 als Hex-String (64 Zeichen 0–9, a–f). Passwörter gehen nur so ans Backend, der Klartext verlässt den
 * Browser nie. Das Backend legt BCrypt darüber.
 *
 * crypto.subtle gibt es nur im Secure Context: https oder localhost. Über http://<ip> gibt es hier einen harten Fehler.
 */
export async function sha256Hex(text: string): Promise<string> {
  return toHex(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)))
}

/** Oberhalb davon rechnet der Browser nicht mehr: crypto.subtle nimmt die ganze Datei in den Speicher */
export const MAX_FILE_BYTES_FOR_HASH = 2000 * 1024 * 1024

/**
 * SHA-256 einer Datei als Hex, die id ihres Dokuments im DMS. null, wenn der Browser es nicht kann: Datei zu groß oder
 * kein Secure Context. Dann rechnet nur das Backend, und die Vorprüfung auf Dubletten entfällt für diese Datei.
 */
export async function fileSha256Hex(file: File): Promise<string | null> {
  if (file.size > MAX_FILE_BYTES_FOR_HASH || typeof crypto === 'undefined' || crypto.subtle === undefined) {
    return null
  }
  return toHex(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()))
}

function toHex(digest: ArrayBuffer): string {
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

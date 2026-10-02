/**
 * SHA-256 als Hex-String (64 Zeichen 0–9, a–f). Passwörter gehen nur so ans Backend, der Klartext verlässt den
 * Browser nie. Das Backend legt BCrypt darüber.
 *
 * crypto.subtle gibt es nur im Secure Context: https oder localhost. Über http://<ip> gibt es hier einen harten Fehler.
 */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

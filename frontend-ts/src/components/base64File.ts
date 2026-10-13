/**
 * Dateien als Base64, damit sie im JSON über den einen Request-Client gehen können. Für kleine Dateien gedacht, z. B.
 * den Export der Stammdaten: Das Backend (Jackson) liest und schreibt byte[] als Base64. Dazu der eine Download-Weg
 * des Frontends: Base64-Inhalt oder Text als Datei, wie ein Klick auf einen Download-Link (User 2026-10-13, es
 * werden viele Texte zum Herunterladen angeboten).
 */

/** Den Inhalt einer Datei als Base64 */
export async function fileToBase64(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  // in Stücken: String.fromCharCode mit allen Bytes auf einmal sprengt bei großen Dateien den Stack
  for (let start = 0; start < bytes.length; start += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(start, start + 0x8000))
  }
  return btoa(binary)
}

/**
 * Base64-Inhalt als Blob mit diesem Typ, die eine Stelle fürs Dekodieren: für den Download hier und fürs Anzeigen im
 * Browser, z. B. ein PDF-Anhang einer Mail, dessen Bytes schon als Base64 im Browser stehen (PdfFrame nimmt den Blob).
 */
export function blobOfBase64(base64: string, type: string): Blob {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index)
  }
  return new Blob([bytes], { type })
}

/** Lädt Base64-Inhalt als Datei herunter, wie ein Klick auf einen Download-Link */
export function downloadBase64File(fileName: string, base64: string, type: string) {
  downloadBlob(fileName, blobOfBase64(base64, type))
}

/** Lädt einen Text als Datei herunter, z. B. ein JSON als name.json mit type application/json */
export function downloadTextFile(fileName: string, text: string, type: string) {
  downloadBlob(fileName, new Blob([text], { type }))
}

/** Der eine Download-Weg: ein Blob unter seinem Dateinamen, wie ein Klick auf einen Download-Link */
function downloadBlob(fileName: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  // erst später freigeben: Manche Browser lesen die URL erst nach dem Klick
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

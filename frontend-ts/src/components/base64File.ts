/**
 * Dateien als Base64, damit sie im JSON über den einen Request-Client gehen können. Für kleine Dateien gedacht, z. B.
 * den Export der Stammdaten: Das Backend (Jackson) liest und schreibt byte[] als Base64.
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

/** Lädt Base64-Inhalt als Datei herunter, wie ein Klick auf einen Download-Link */
export function downloadBase64File(fileName: string, base64: string, type: string) {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index)
  }
  const url = URL.createObjectURL(new Blob([bytes], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  // erst später freigeben: Manche Browser lesen die URL erst nach dem Klick
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

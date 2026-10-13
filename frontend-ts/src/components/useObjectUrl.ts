import { useEffect, useState } from 'react'

/**
 * Ein Blob als Object-URL, damit der Browser ihn selbst anzeigen kann (iframe, img, video). Die URL gilt, solange der
 * Blob derselbe ist; beim Wechsel und beim Abbau wird sie freigegeben, sonst hielte der Browser die Bytes bis zum
 * Schließen des Tabs. null, solange es keinen Blob gibt.
 */
export function useObjectUrl(blob: Blob | null): string | null {
  const [objectUrl, setObjectUrl] = useState<string | null>(null)

  useEffect(() => {
    if (blob === null) {
      setObjectUrl(null)
      return undefined
    }
    const url = URL.createObjectURL(blob)
    setObjectUrl(url)
    return () => {
      URL.revokeObjectURL(url)
    }
  }, [blob])

  return objectUrl
}

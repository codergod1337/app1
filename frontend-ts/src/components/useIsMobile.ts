import { useSyncExternalStore } from 'react'

/** Unter dieser Breite gilt die Ansicht als mobil (Bootstraps Grenze md = 768 px). */
const MOBILE_QUERY = '(max-width: 767.98px)'

function subscribe(onChange: () => void): () => void {
  const query = window.matchMedia(MOBILE_QUERY)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

/** true in der mobilen Auflösung. Die Komponente rendert neu, sobald sich das ändert (z. B. Handy gedreht). */
export function useIsMobile(): boolean {
  return useSyncExternalStore(subscribe, () => window.matchMedia(MOBILE_QUERY).matches)
}

import { isValidKey } from './keyRule.ts'

/** Vor jedem key einer gemerkten Scrollhöhe in den UsersSettings */
export const SCROLL_SETTING_PREFIX = 'SCROLL_'

/**
 * Das Routenmuster ohne Parameterwerte: /solrmanager/DMS mit {coreKey: 'DMS'} wird /solrmanager/:coreKey. Jedes
 * Segment, dessen Wert ein Parameter ist, steht als :name. So gilt eine Höhe je Tab eines Moduls, nicht je Kern oder
 * je Artikelnummer.
 */
export function routePattern(pathname: string, params: Readonly<Record<string, string | undefined>>): string {
  const nameByValue = new Map<string, string>()
  for (const [name, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      nameByValue.set(value, name)
    }
  }
  const segments = pathname
    .split('/')
    .filter((segment) => segment !== '')
    .map((segment) => {
      const name = nameByValue.get(decode(segment))
      return name === undefined ? segment : `:${name}`
    })
  return `/${segments.join('/')}`
}

/**
 * Der key der UsersSetting zu einem Muster, nach der normalen Key-Regel: /admin/users wird SCROLL_ADMIN_USERS,
 * /solrmanager/:coreKey wird SCROLL_SOLRMANAGER_COREKEY, die Startseite SCROLL_HOME. null, wenn aus dem Muster kein
 * gültiger key wird, dann wird nichts gemerkt.
 */
export function scrollSettingKey(pattern: string): string | null {
  const body =
    pattern === '/'
      ? 'HOME'
      : pattern
          .toUpperCase()
          .replace(/[^A-Z0-9]+/g, '_')
          .replace(/^_+|_+$/g, '')
  const key = SCROLL_SETTING_PREFIX + body
  return isValidKey(key) ? key : null
}

function decode(segment: string): string {
  try {
    return decodeURIComponent(segment)
  } catch {
    return segment
  }
}

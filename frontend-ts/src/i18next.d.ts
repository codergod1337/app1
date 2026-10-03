import 'i18next'
import type de from './branding/locales/de.json'

/** Die Schlüssel der festen Texte sind typisiert nach der deutschen Quelle: t('solrmanager.cores.title') mit Autovervollständigung */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation'
    resources: { translation: typeof de }
  }
}

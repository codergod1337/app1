import { useTranslation } from 'react-i18next'
import { useLanguage } from '../../components/languageContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { AbasInfosys } from '../infosys/AbasInfosys.ts'
import { ArtikelMappedResponseBox } from './ArtikelMappedResponseBox.tsx'

interface ArtikelInfosysTabProps {
  artikelNummer: string
  /** der hinterlegte Aufruf mit dem Anzeigeort ARTIKELMAPPE, der dieser Tab ist */
  abasInfosys: AbasInfosys
}

/**
 * Ein Tab der Mappe, der ein hinterlegter Infosystem-Aufruf ist (User 2026-10-11, nach dem Tab „Fertigung“ der
 * Vorlage): beim Öffnen wird der Aufruf für den Artikel ausgeführt, die Platzhalter füllt das Backend aus dem Dokument,
 * die Antwort kommt zerlegt über das Mapping ihres Typs. Welche Aufrufe Tabs sind, sagt ihr Anzeigeort, kein Code.
 */
export function ArtikelInfosysTab({ artikelNummer, abasInfosys }: ArtikelInfosysTabProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  return (
    <ArtikelMappedResponseBox
      artikelNummer={artikelNummer}
      request={{ kind: 'infosys', abasInfosysKey: abasInfosys.key }}
      title={translate(abasInfosys.displayName, language) || abasInfosys.key}
      emptyText={t('artikel.nachladen.empty')}
    />
  )
}

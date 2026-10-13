import { useTranslation } from 'react-i18next'
import { ContentBox } from '../../components/ContentBox.tsx'
import { isValidSternzeit } from '../abasmanager/AbasSternzeit.ts'
import { ARTIKEL_INSPECTION_ORDERS_ABAS_INFOSYS_KEY, ARTIKEL_INSPECTION_PROCEDURE_FIELD_NAME, artikelValueOf } from './Artikel.ts'
import { ArtikelMappedResponseBox } from './ArtikelMappedResponseBox.tsx'

interface ArtikelPruefungenTabProps {
  artikelNummer: string
  document: Record<string, unknown>
}

/**
 * Der Tab „Prüfungen“ nach der Vorlage (User 2026-10-11), beides live bei ABAS: Soll, die Prüfpläne aus der
 * Prüfeinstellung, deren Sternzeit im Feld yqmappqmpruefv des Artikels steht; Ist, die durchgeführten Prüfungen aus dem
 * hinterlegten Aufruf QMPRUEFAUFTRAEGE. Die Merkmale je Prüfplan (71:3) folgen, sobald das Mapping einen Subfetch je
 * Tabellenzeile kennt.
 */
export function ArtikelPruefungenTab({ artikelNummer, document }: ArtikelPruefungenTabProps) {
  const { t } = useTranslation()
  const procedureSternzeit = artikelValueOf(document, ARTIKEL_INSPECTION_PROCEDURE_FIELD_NAME).trim()

  return (
    <>
      {isValidSternzeit(procedureSternzeit) ? (
        <ArtikelMappedResponseBox
          artikelNummer={artikelNummer}
          request={{ kind: 'sternzeit', fieldName: ARTIKEL_INSPECTION_PROCEDURE_FIELD_NAME }}
          title={t('artikel.inspections.soll')}
          hint={t('artikel.inspections.sollHint')}
          emptyText={t('artikel.inspections.noPlans')}
        />
      ) : (
        <ContentBox title={t('artikel.inspections.soll')}>
          <p className="mb-0 text-muted">{t('artikel.inspections.noProcedure')}</p>
        </ContentBox>
      )}
      <ArtikelMappedResponseBox
        artikelNummer={artikelNummer}
        request={{ kind: 'infosys', abasInfosysKey: ARTIKEL_INSPECTION_ORDERS_ABAS_INFOSYS_KEY }}
        title={t('artikel.inspections.ist')}
        emptyText={t('artikel.inspections.noOrders')}
      />
    </>
  )
}

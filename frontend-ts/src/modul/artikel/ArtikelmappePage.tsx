import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'
import { type AbasInfosys, abasInfosysPlaceholderNamesOf } from '../infosys/AbasInfosys.ts'
import {
  ARTIKEL_INSPECTION_ORDERS_ABAS_INFOSYS_KEY,
  ARTIKEL_REFERENCE_FLAG_BY_TAB,
  ARTIKEL_TABS,
  type ArtikelDocumentForClient,
  type ArtikelTab,
  artikelFieldHasValue,
  artikelInfosysTabKey,
  artikelMasterNumberOf,
  artikelPath,
  artikelTabPath,
  fetchArtikelIntoCore,
  getArtikelDocument,
  getArtikelmappeInfosysList,
  isArtikelTab,
} from './Artikel.ts'
import { ArtikelDetailsTab } from './ArtikelDetailsTab.tsx'
import { ArtikelDocumentsTab } from './ArtikelDocumentsTab.tsx'
import { ARTIKEL_PAIR_LIST_FIELD_NAMES, ARTIKEL_TAB_FIELD_NAMES, ARTIKEL_TABLE_FIELDS, type ArtikelTableField } from './artikelFieldSections.ts'
import { ArtikelInfosysTab } from './ArtikelInfosysTab.tsx'
import { ArtikelOverviewTab } from './ArtikelOverviewTab.tsx'
import { ArtikelPairListTable } from './ArtikelPairListTable.tsx'
import { ArtikelPruefungenTab } from './ArtikelPruefungenTab.tsx'
import { ArtikelReferencesTab } from './ArtikelReferencesTab.tsx'
import { ArtikelTableFieldBox } from './ArtikelTableFieldBox.tsx'

/**
 * Was die Seite zu einer Nummer geladen hat: die Mappe, ein Fehler, oder der Kern kennt die Nummer nicht und ABAS wird
 * gerade gefragt (User 2026-10-13); eine andere Nummer in der Adresse heißt „lädt“
 */
type Loaded = { artikelNummer: string } & (
  | { kind: 'error'; message: string }
  | { kind: 'fetchingFromAbas' }
  | { kind: 'ok'; artikelmappe: ArtikelDocumentForClient }
)

/** Die Tabs mit einem Tabellenfeld (je Zeile ein JSON-Objekt), sie erscheinen nur mit Zeilen */
const TABLE_FIELD_TABS: Partial<Record<ArtikelTab, ArtikelTableField>> = {
  bom: ARTIKEL_TABLE_FIELDS.bom,
  operations: ARTIKEL_TABLE_FIELDS.operations,
}

/**
 * Die Artikelmappe, nach ArtikelmappeSeite der Vorlage: Route /artikel/:artikelNummer/:tab?, der Subheader trägt die
 * Nummer und die Tabs, der aktive steht in der Adresse. Die Mappe wird einmal geladen (Dokument plus Familie), jeder
 * feste Tab zeigt daraus seinen Teil; die Dokumente kommen aus dem DMS. Dazu die Tabs, die live bei ABAS nachladen
 * (User 2026-10-11): „Prüfungen“ und je ein Tab für jeden hinterlegten Aufruf mit dem Anzeigeort ARTIKELMAPPE, vor den
 * Details. Ein Tab erscheint nur mit Recht und Daten (User 2026-10-13): wenn eines seiner Felder aus
 * ARTIKEL_TAB_FIELD_NAMES im Dokument steht, das nur lesbare Felder trägt; ein Infosystem-Tab nur, wenn alle Felder
 * seiner Platzhalter da sind. Das Backend prüft dieselben Felder vor jedem Nachladen. Die Herkunft für jeden Weg aus der
 * Mappe ist der aktive Tab, damit Zurück wieder hier landet.
 */
export function ArtikelmappePage() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { artikelNummer = '', tab } = useParams()
  const [lastLoaded, setLastLoaded] = useState<Loaded | null>(null)
  /** die Aufrufe mit dem Anzeigeort ARTIKELMAPPE, einmal je Seite; null solange sie laden, leer wenn es keine gibt */
  const [infosysList, setInfosysList] = useState<AbasInfosys[] | null>(null)

  // Die Mappe laden. Kennt der Kern die Nummer nicht (404), wird ABAS über die Belegnummer gefragt und der Artikel in
  // den Kern geholt (User 2026-10-13); danach noch einmal laden, bei abweichender Nummer im Kern dorthin springen.
  useEffect(() => {
    let cancelled = false
    function messageOf(error: unknown): string {
      return error instanceof RequestError ? error.message : t('common.unknownError')
    }
    function loadFromCore(afterAbas: boolean) {
      getArtikelDocument(artikelNummer)
        .then((result) => {
          if (!cancelled) {
            setLastLoaded({ artikelNummer, kind: 'ok', artikelmappe: result.data })
          }
        })
        .catch((error: unknown) => {
          if (cancelled) {
            return
          }
          if (!afterAbas && error instanceof RequestError && error.status === 404) {
            fetchFromAbas()
            return
          }
          setLastLoaded({ artikelNummer, kind: 'error', message: messageOf(error) })
        })
    }
    function fetchFromAbas() {
      setLastLoaded({ artikelNummer, kind: 'fetchingFromAbas' })
      fetchArtikelIntoCore(artikelNummer)
        .then((result) => {
          if (cancelled) {
            return
          }
          const idInCore = result.data.primaryId
          if (idInCore !== null && idInCore !== '' && idInCore !== artikelNummer) {
            navigate(artikelPath(idInCore), { replace: true })
            return
          }
          loadFromCore(true)
        })
        .catch((error: unknown) => {
          if (cancelled) {
            return
          }
          const unknownAtAbas = error instanceof RequestError && error.status === 404
          setLastLoaded({ artikelNummer, kind: 'error', message: unknownAtAbas ? t('artikel.fetchFromAbas.unknown', { artikelNummer }) : messageOf(error) })
        })
    }
    loadFromCore(false)
    return () => {
      cancelled = true
    }
  }, [artikelNummer, navigate, t])

  useEffect(() => {
    let cancelled = false
    getArtikelmappeInfosysList()
      .then((result) => {
        if (!cancelled) {
          setInfosysList(result.data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setInfosysList([])
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  // geladen ist nur, was zur Nummer in der Adresse gehört; sonst lädt die Seite gerade
  const loaded: Loaded | null = lastLoaded !== null && lastLoaded.artikelNummer === artikelNummer ? lastLoaded : null
  const document = loaded?.kind === 'ok' ? loaded.artikelmappe.document : null

  /** Ein fester Tab nur mit Recht und Daten: eines seiner Felder steht im Dokument; ohne Eintrag immer */
  function tabHasData(tabKey: ArtikelTab): boolean {
    if (document === null) {
      return true
    }
    const fieldNames = ARTIKEL_TAB_FIELD_NAMES[tabKey]
    return fieldNames === undefined || fieldNames.some((fieldName) => artikelFieldHasValue(document, fieldName))
  }

  /** Ein Infosystem-Tab nur, wenn jedes Feld seiner Platzhalter im Dokument steht; sonst könnte ABAS gar nicht gefragt werden */
  function infosysHasData(abasInfosys: AbasInfosys): boolean {
    if (document === null) {
      return true
    }
    return abasInfosysPlaceholderNamesOf(abasInfosys.requestBodyTemplate).every((name) => artikelFieldHasValue(document, name))
  }

  // die Aufrufe als Tabs, ohne den der Ist-Seite der Prüfungen, der dort schon steht
  const infosysTabs = (infosysList ?? []).filter((abasInfosys) => abasInfosys.key !== ARTIKEL_INSPECTION_ORDERS_ABAS_INFOSYS_KEY && infosysHasData(abasInfosys))
  const activeInfosys = infosysTabs.find((abasInfosys) => artikelInfosysTabKey(abasInfosys.key) === tab) ?? null
  const activeTab: string = activeInfosys !== null ? tab ?? 'overview' : isArtikelTab(tab) && tabHasData(tab) ? tab : 'overview'

  const tabs: SubheaderTab[] = []
  if (document !== null) {
    for (const tabKey of ARTIKEL_TABS.filter(tabHasData)) {
      if (tabKey === 'details') {
        for (const abasInfosys of infosysTabs) {
          tabs.push({ label: translate(abasInfosys.displayName, language) || abasInfosys.key, to: artikelTabPath(artikelNummer, artikelInfosysTabKey(abasInfosys.key)) })
        }
      }
      tabs.push({ label: t(`artikel.tabs.${tabKey}`), to: artikelTabPath(artikelNummer, tabKey) })
    }
  }

  const origin: OriginState = { origin: { path: artikelTabPath(artikelNummer, activeTab), label: t('artikel.originLabel', { artikelNummer }) } }

  let content
  if (loaded === null) {
    content = (
      <ContentBox title={t('artikel.title')}>
        <p className="mb-0">{t('common.loading')}</p>
      </ContentBox>
    )
  } else if (loaded.kind === 'error') {
    content = (
      <ContentBox title={t('artikel.title')}>
        <p className="mb-0 text-danger">{loaded.message}</p>
      </ContentBox>
    )
  } else if (loaded.kind === 'fetchingFromAbas') {
    content = (
      <ContentBox title={t('artikel.title')}>
        <p className="mb-0">
          <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
          {t('artikel.fetchFromAbas.fetching', { artikelNummer })}
        </p>
      </ContentBox>
    )
  } else if (activeInfosys !== null) {
    content = <ArtikelInfosysTab artikelNummer={artikelNummer} abasInfosys={activeInfosys} />
  } else {
    const artikelmappe = loaded.artikelmappe
    const fixedTab: ArtikelTab = isArtikelTab(activeTab) ? activeTab : 'overview'
    const referenceFlag = ARTIKEL_REFERENCE_FLAG_BY_TAB[fixedTab]
    const tableField = TABLE_FIELD_TABS[fixedTab]
    if (tableField !== undefined) {
      content = <ArtikelTableFieldBox document={artikelmappe.document} tableField={tableField} emptyText={t('artikel.pairList.empty')} origin={origin} />
    } else if (fixedTab === 'overview') {
      content = <ArtikelOverviewTab artikelNummer={artikelNummer} artikelmappe={artikelmappe} origin={origin} />
    } else if (fixedTab === 'documents') {
      content = <ArtikelDocumentsTab artikelNummer={artikelNummer} masterNumber={artikelMasterNumberOf(artikelmappe.document)} origin={origin} />
    } else if (fixedTab === 'details') {
      content = <ArtikelDetailsTab document={artikelmappe.document} />
    } else if (fixedTab === 'inspections') {
      content = <ArtikelPruefungenTab artikelNummer={artikelNummer} document={artikelmappe.document} />
    } else if (fixedTab === 'customer') {
      content = (
        <ArtikelPairListTable
          title={t('artikel.tabs.customer')}
          document={artikelmappe.document}
          fieldNames={ARTIKEL_PAIR_LIST_FIELD_NAMES.customer}
          emptyText={t('artikel.pairList.emptyCustomer')}
          origin={origin}
        />
      )
    } else if (fixedTab === 'supplier') {
      content = (
        <>
          <ArtikelPairListTable
            title={t('artikel.tabs.supplier')}
            document={artikelmappe.document}
            fieldNames={ARTIKEL_PAIR_LIST_FIELD_NAMES.supplier}
            emptyText={t('artikel.pairList.emptySupplier')}
            origin={origin}
          />
          <ArtikelPairListTable
            title={t('artikel.pairList.manufacturer')}
            document={artikelmappe.document}
            fieldNames={ARTIKEL_PAIR_LIST_FIELD_NAMES.manufacturer}
            emptyText={t('artikel.pairList.emptyManufacturer')}
            origin={origin}
          />
        </>
      )
    } else if (referenceFlag !== undefined) {
      content = <ArtikelReferencesTab artikelNummer={artikelNummer} flag={referenceFlag} title={t(`artikel.tabs.${fixedTab}`)} origin={origin} />
    }
  }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="artikel" /> {t('artikel.status', { artikelNummer })}
          </>
        }
        tabs={tabs}
        sticky
      />
      <div className="main-content">{content}</div>
    </>
  )
}

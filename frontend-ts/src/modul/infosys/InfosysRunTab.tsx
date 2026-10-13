import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { translate } from '../../components/multilingual.ts'
import { MappingJsonTreeBox } from '../mapping/MappingJsonTreeBox.tsx'
import { abasInfosysActionsOf, abasInfosysPlaceholderNamesOf, abasInfosysRequestTextOf, runAbasInfosys, useAbasInfosysList } from './AbasInfosys.ts'

/** Die geholte Antwort mit der Anfrage, die sie geliefert hat */
interface FetchedResponse {
  requestText: string
  value: unknown
}

/**
 * Der Tab „Ausführen“: einen hinterlegten Aufruf wählen, je Platzhalter einen Wert eintippen, meist eine Nummer, und
 * die Antwort als Baum sehen, wie die Bezugsnummer der Vorlage (ow1/BELEG2ID, ybeleg) nur ohne Auswertung. So prüft man
 * einen Aufruf, bevor ein Subfetch oder die Artikelmappe ihn nutzt. Geschickt wird der Key mit den Werten, die Aktionen
 * baut das Backend aus der Vorlage; die Vorschau hier rechnet sie zum Hinsehen nach.
 */
export function InfosysRunTab() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { abasInfosysList, errorMessage: listErrorMessage } = useAbasInfosysList()
  const [selectedKey, setSelectedKey] = useState('')
  const [valueByName, setValueByName] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  const [fetched, setFetched] = useState<FetchedResponse | null>(null)
  const [fetchCount, setFetchCount] = useState(0)

  const abasInfosys = abasInfosysList?.find((candidate) => candidate.key === selectedKey) ?? null
  const placeholders = abasInfosys === null ? [] : abasInfosysPlaceholderNamesOf(abasInfosys.requestBodyTemplate)
  const valueByPlaceholderName = Object.fromEntries(placeholders.map((name) => [name, (valueByName[name] ?? '').trim()]))
  const complete = abasInfosys !== null && placeholders.every((name) => valueByPlaceholderName[name] !== '')
  const actions = abasInfosys === null || !complete ? null : abasInfosysActionsOf(abasInfosys, valueByPlaceholderName)
  const requestText = abasInfosys === null ? '' : abasInfosysRequestTextOf(abasInfosys, null)

  function run(event: FormEvent) {
    event.preventDefault()
    if (abasInfosys === null || actions === null || busy) {
      return
    }
    setBusy(true)
    setFailed(null)
    runAbasInfosys(abasInfosys.key, valueByPlaceholderName)
      .then((result) => {
        setFetched({ requestText, value: result.data })
        setFetchCount((count) => count + 1)
      })
      .catch((error: unknown) => setFailed(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => setBusy(false))
  }

  let footer
  if (listErrorMessage !== null) {
    footer = <span className="text-danger">{listErrorMessage}</span>
  } else if (busy) {
    footer = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {t('mapping.abasFetch.fetching')}
      </span>
    )
  } else if (failed !== null) {
    footer = <span className="text-danger">{t('mapping.abasFetch.failed', { error: failed })}</span>
  } else {
    footer = <span>{t('infosys.run.hint')}</span>
  }

  return (
    <>
      <ContentBox title={t('infosys.run.title')} footer={footer}>
        <form className="d-flex flex-column gap-3" onSubmit={run}>
          <div className="row g-2">
            <div className="col-12 col-md-6">
              <label className="form-label mb-1" htmlFor="infosys-run-call">
                {t('infosys.run.call')}
              </label>
              <select
                id="infosys-run-call"
                className="form-select form-select-sm"
                value={selectedKey}
                disabled={abasInfosysList === null}
                onChange={(event) => {
                  setSelectedKey(event.target.value)
                  setValueByName({})
                  setFailed(null)
                }}
              >
                <option value="">{abasInfosysList !== null && abasInfosysList.length === 0 ? t('infosys.run.noCalls') : t('infosys.run.chooseCall')}</option>
                {(abasInfosysList ?? []).map((candidate) => (
                  <option key={candidate.key} value={candidate.key}>
                    {candidate.key}: {translate(candidate.displayName, language) || candidate.key} ({candidate.infosysGroup}/{candidate.infosysName})
                  </option>
                ))}
              </select>
            </div>
          </div>
          {abasInfosys !== null && (
            <div className="row g-2">
              {placeholders.length === 0 && <div className="col-12 text-body-secondary small">{t('infosys.calls.form.noPlaceholders')}</div>}
              {placeholders.map((name) => (
                <div key={name} className="col-12 col-md-4">
                  <label className="form-label mb-1" htmlFor={`infosys-run-${name}`}>
                    <code>{name}</code>
                  </label>
                  <input
                    id={`infosys-run-${name}`}
                    className="form-control form-control-sm font-monospace"
                    value={valueByName[name] ?? ''}
                    placeholder={t('infosys.run.inputPlaceholder', { name })}
                    onChange={(event) => setValueByName((current) => ({ ...current, [name]: event.target.value }))}
                  />
                </div>
              ))}
            </div>
          )}
          {abasInfosys !== null && (
            <div>
              <div className="small">{t('mapping.abasFetch.previewLabel')}</div>
              <code className="text-break">{requestText}</code>
              <pre className="mb-0 mt-1 small">{JSON.stringify({ actions: actions ?? JSON.parse(abasInfosys.requestBodyTemplate) }, null, 2)}</pre>
            </div>
          )}
          <div>
            <button type="submit" className="button-other" disabled={abasInfosys === null || actions === null || busy}>
              <Icon name="abasManager" /> {t('mapping.abasFetch.fetch')}
            </button>
          </div>
        </form>
      </ContentBox>

      {fetched !== null && (
        <MappingJsonTreeBox
          key={fetchCount}
          value={fetched.value}
          title={
            <>
              {t('mapping.abasFetch.responseTitle')} <code className="small text-break">{fetched.requestText}</code>
            </>
          }
        />
      )}
    </>
  )
}

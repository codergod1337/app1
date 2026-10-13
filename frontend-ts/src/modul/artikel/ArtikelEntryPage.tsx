import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Subheader } from '../../components/Subheader.tsx'
import { SEARCH_PATH } from '../search/Search.ts'
import { artikelPath } from './Artikel.ts'

/**
 * Der Einstieg des Artikel-Moduls ohne Nummer: eine Artikelnummer eintippen und in ihre Mappe springen. Der andere
 * Weg ist die Suche, wie in der Vorlage, dort über den Kern ARTIKEL. Keine Historie, die hat die Vorlage eingefroren.
 */
export function ArtikelEntryPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [artikelNummer, setArtikelNummer] = useState('')

  function open(event: FormEvent) {
    event.preventDefault()
    const trimmed = artikelNummer.trim()
    if (trimmed !== '') {
      navigate(artikelPath(trimmed))
    }
  }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="artikel" /> {t('artikel.title')}
          </>
        }
        sticky
      />
      <div className="main-content">
        <ContentBox
          title={t('artikel.entry.title')}
          footer={
            <span>
              {t('artikel.entry.hint')}{' '}
              <button type="button" className="btn btn-link btn-sm p-0 border-0 align-baseline" onClick={() => navigate(SEARCH_PATH)}>
                {t('artikel.entry.toSearch')}
              </button>
            </span>
          }
        >
          <form className="d-flex gap-2 align-items-center flex-wrap" onSubmit={open}>
            <label className="form-label mb-0" htmlFor="artikel-entry-number">
              {t('artikel.entry.number')}
            </label>
            <input
              id="artikel-entry-number"
              className="form-control font-monospace"
              style={{ maxWidth: '16rem' }}
              value={artikelNummer}
              placeholder={t('artikel.entry.placeholder')}
              onChange={(event) => setArtikelNummer(event.target.value)}
            />
            <button type="submit" className="button-other" disabled={artikelNummer.trim() === ''}>
              {t('artikel.entry.open')}
            </button>
          </form>
        </ContentBox>
      </div>
    </>
  )
}

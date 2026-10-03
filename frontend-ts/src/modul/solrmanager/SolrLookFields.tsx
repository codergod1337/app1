import { useTranslation } from 'react-i18next'
import { ColorField } from '../../components/ColorField.tsx'
import { SymbolPicker } from '../../components/SymbolPicker.tsx'
import { BADGE_GRADIENTS, type BadgeGradient } from '../accessrole/AccessRole.ts'
import type { SolrLook } from './SolrLook.ts'

/** Die Farbfelder, ihre Beschriftung steht im Katalog unter solrmanager.look.<field> */
const LOOK_COLOR_FIELDS = [
  'mainColor',
  'gradientStart',
  'gradientEnd',
  'textColor',
  'textShadowColor',
  'shadowColor',
] as const

interface SolrLookFieldsProps {
  /** Präfix der Element-ids, z. B. new-solr-core-form */
  idPrefix: string
  look: SolrLook
  disabled: boolean
  /** nur die geänderten Felder, z. B. { gradient: 'BEVEL' } */
  onChange: (changes: Partial<SolrLook>) => void
}

/**
 * Die Eingabefelder für das Aussehen eines Kerns: Farben, Verlaufsart, Symbol. Die Live-Vorschau (Emblem) zeigt der
 * Aufrufer. Gehört in ein Bootstrap-row.
 */
export function SolrLookFields({ idPrefix, look, disabled, onChange }: SolrLookFieldsProps) {
  const { t } = useTranslation()

  return (
    <>
      {LOOK_COLOR_FIELDS.map((field) => (
        <div key={field} className="col-md-6">
          <ColorField
            id={`${idPrefix}-${field}`}
            label={t(`solrmanager.look.${field}`)}
            value={look[field]}
            disabled={disabled}
            onChange={(color) => onChange({ [field]: color })}
          />
        </div>
      ))}
      <div className="col-md-6">
        <label className="form-label" htmlFor={`${idPrefix}-gradient`}>
          {t('solrmanager.look.gradient')}
        </label>
        <select
          id={`${idPrefix}-gradient`}
          className="form-select"
          value={look.gradient ?? ''}
          onChange={(event) => onChange({ gradient: (event.target.value || null) as BadgeGradient | null })}
        >
          <option value="">{t('common.default')}</option>
          {BADGE_GRADIENTS.map(({ gradient }) => (
            <option key={gradient} value={gradient}>
              {t(`common.gradient.${gradient}`)}
            </option>
          ))}
        </select>
      </div>
      <div className="col-12">
        <span className="form-label d-block">{t('common.symbol')}</span>
        <SymbolPicker value={look.symbol} disabled={disabled} onChange={(symbol) => onChange({ symbol })} />
      </div>
    </>
  )
}

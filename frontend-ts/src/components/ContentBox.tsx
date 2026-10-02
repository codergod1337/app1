import type { ReactNode } from 'react'
import type { LanguageCode } from '../branding/languages.ts'
import { LanguageFlags } from './LanguageFlags.tsx'

interface ContentBoxProps {
  title: ReactNode
  /** Knöpfe rechts im contentbox-header */
  actions?: ReactNode
  /** Inhalt des contentbox-footer. Der Footer selbst ist immer da, nur sein Inhalt ist optional. */
  footer?: ReactNode
  /** Für Formulare mit mehrsprachigen Feldern: die Flaggen oben rechts in contentbox-main wählen die Sprache. */
  language?: { value: LanguageCode; onChange: (language: LanguageCode) => void }
  children: ReactNode
}

export function ContentBox({ title, actions, footer, language, children }: ContentBoxProps) {
  return (
    <section className="contentbox">
      <div className="contentbox-header">
        <h2 className="contentbox-title">{title}</h2>
        {actions}
      </div>
      <div className="contentbox-main">
        {language && <LanguageFlags value={language.value} onChange={language.onChange} />}
        {children}
      </div>
      <div className="contentbox-footer">{footer}</div>
    </section>
  )
}

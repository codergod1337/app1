import { useLoad } from '../../api/useLoad.ts'
import { getAllGraphMailTemplates, getAllGraphMailTemplateTypes } from './GraphMailTemplate.ts'

/** Alle Mailvorlagen, geladen beim ersten Anzeigen. Die Vorlagen liegen nicht in den Stammdaten: Ihre Texte sind nicht öffentlich. */
export function useAllGraphMailTemplates() {
  const { data, errorMessage, reload } = useLoad(getAllGraphMailTemplates)
  return { graphMailTemplates: data, errorMessage, reload }
}

/** Die Vorlagentypen mit ihren Platzhaltergruppen, für Auswahl und Hinweise im Editor */
export function useAllGraphMailTemplateTypes() {
  const { data, errorMessage } = useLoad(getAllGraphMailTemplateTypes)
  return { graphMailTemplateTypes: data, errorMessage }
}

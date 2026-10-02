import { icons, type IconName } from '../branding/icons.ts'
import { PackIcon } from './PackIcon.tsx'

/** Ein Symbol der Oberfläche über seinen Namen, die Zuordnung steht in branding/icons.ts. */
export function Icon({ name }: { name: IconName }) {
  return <PackIcon symbol={icons[name]} />
}

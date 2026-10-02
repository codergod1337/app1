import { branding } from '../branding/branding.ts'

const currentYear = new Date().getFullYear()

/** Immer da, unabhängig vom Modul. */
export function Footer() {
  return (
    <footer className="footer">
      <span>
        © {currentYear} {branding.companyName}
      </span>
    </footer>
  )
}

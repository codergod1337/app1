import { Outlet } from 'react-router'
import { MasterDataProvider } from '../components/MasterDataProvider.tsx'
import { Toasts } from '../components/Toasts.tsx'
import { UsersSettingsProvider } from '../components/UsersSettingsProvider.tsx'
import { SessionProvider } from '../modul/login/SessionProvider.tsx'
import { Footer } from './Footer.tsx'
import { Header } from './Header.tsx'

/**
 * Grundgerüst jeder Seite: header, main, footer.
 * In main rendert das Modul selbst: optional seinen subheader, dann main-content.
 * Stammdaten und Sitzung laden beim Start, unabhängig voneinander, und stehen allen Modulen bereit. Darauf die eigenen
 * Einstellungen des Angemeldeten und die Sprache der Oberfläche (UsersSettingsProvider).
 * Darüber schweben unten rechts die Toasts (showToast).
 */
export function Layout() {
  return (
    <MasterDataProvider>
      <SessionProvider>
        <UsersSettingsProvider>
          <Header />
          <main className="main">
            <Outlet />
          </main>
          <Footer />
          <Toasts />
        </UsersSettingsProvider>
      </SessionProvider>
    </MasterDataProvider>
  )
}

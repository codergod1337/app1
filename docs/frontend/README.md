# Frontend

React mit TypeScript, gebaut mit Vite. Liegt in `frontend-ts/`.

- Routing: React Router
- CSS: Bootstrap (nur CSS, kein JS) als Werkzeugkasten, darüber `basic.css` und ein Theme
- Drag and Drop: dnd-kit
- Symbole: Bootstrap Icons, Tabler Icons, Lucide, Flaggen aus flag-icons
- Lint: oxlint

## Ordner

```
frontend-ts/
  public/themes/   ein CSS pro Theme
  src/
    api/           der zentrale Client für alle Backend-Requests, misst jeden Request
    branding/      alles Firmenspezifische (Name, Logo, Sprachen)
    components/    alles, was mehr als ein Modul nutzt (Table, ContentBox, Popup, Toast, Hoverlay …)
    layout/        Header, Footer, Grundgerüst
    modul/         ein Ordner pro Modul (login, users, admin, profile …)
    styles/        basic.css: Maße und alles, was in jedem Theme gleich ist
```

## Grundsätze

- Keine Komponente ruft selbst `fetch` auf. Jeder Request läuft über den Client in `api/`. Die Funktionen zu den Endpunkten stehen in derselben Datei wie ihr Typ, z. B. `modul/users/Users.ts`.
- Eine Seite besteht aus `header`, `main` und `footer`. Das Modul rendert in `main` optional seinen Subheader und darunter genau eine Darstellung: ContentBox, Matrix oder A4-Ansicht.
- Die Sitzung (wer ist angemeldet, welche Rollen) lesen alle Module über `useSession()`, die Stammdaten über `useMasterData()`.
- CSS-Priorität: Bootstrap < `basic.css` < Theme. Ein Theme ist genau eine Datei in `public/themes/` und setzt die Variablen, die `basic.css` anbietet. Ausnahme: Knöpfe, Hoverlay und Toast gestaltet das Theme komplett selbst.
- Mehrsprachige Felder sind JSON mit dem Sprachkürzel als Schlüssel. Die aktivierten Sprachen stehen in `branding/languages.ts`.
- Alles Firmenspezifische steht in `branding/`, nie fest in einer Komponente.

## Starten

Voraussetzung: Node.js, dazu ein laufendes Backend auf Port 6969.

```
cd frontend-ts
npm install
npm run dev
```

Der Dev-Server läuft auf Port 3000 und leitet alle Requests an `/api` an das Backend weiter (`vite.config.ts`). Im Frontend gibt es deshalb keine Backend-URL zu konfigurieren.

Weitere Befehle: `npm run build` (nach `dist/`), `npm run lint`.

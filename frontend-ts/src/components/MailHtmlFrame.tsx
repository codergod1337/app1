import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

/** Wie der neue Teil in der ganzen Mail lag: Rest dahinter, kein Rest, oder die Stelle war nicht zu finden */
export type MailNewPartState = 'REST' | 'NO_REST' | 'NOT_FOUND'

/** Wie der Rest hinter dem neuen Teil dasteht: warn = Grenze mit Box, Rest schraffiert; show = wie der Rest der Mail */
export type MailRestMode = 'warn' | 'show'

interface MailHtmlFrameProps {
  /** das HTML der Mail; Bilder nur als data: oder blob:, alles andere wird nicht geladen */
  html: string
  /** Titel des iframes für Screenreader, z. B. der Betreff */
  title: string
  /** der neue Teil der Mail (uniqueBody); ohne: nichts wird abgetrennt */
  newPartHtml?: string | null
  /** nur mit newPartHtml: wie der Rest dasteht */
  restMode?: MailRestMode
  /** nur mit newPartHtml: bekommt nach dem Laden, wie der neue Teil lag */
  onNewPartFound?: (state: MailNewPartState) => void
}

/**
 * NICHTS VON AUSSEN NACHLADEN (Vorlage, User 2026-09-20): Die sandbox sperrt Skripte, Formulare und Popups, das Laden
 * von Bildern, Schriften und Stylesheets fremder Server sperrt sie nicht. Genau darüber laufen Zählpixel: Der Absender
 * sähe, dass und wann seine Mail geöffnet wurde. Diese Regel steht als Allererstes im Dokument und lässt nur zu, was in
 * der Mail selbst steckt: Bilder als data:, Formatierung als eingebettetes CSS. Dazu blob: für die Bilder einer
 * archivierten Nachricht, die die Seite selbst aus dem DMS holt (dms:<id> im Text); eine Mail kann ohne Skript keine
 * blob:-Adresse erzeugen.
 */
const NOTHING_FROM_OUTSIDE =
  '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src data: blob:; style-src \'unsafe-inline\'; font-src data:">'

/** Die Grundformatierung vor dem HTML der Mail, deren eigene Angaben gewinnen. Bilder und Tabellen sprengen nie die Seite. */
const BASE_STYLE =
  '<style>' +
  'html,body{margin:0;padding:0}' +
  'body{padding:4px 2px;font-family:system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif;' +
  'font-size:15px;line-height:1.55;overflow-wrap:anywhere}' +
  'img{max-width:100% !important;height:auto !important}' +
  'table{max-width:100% !important}' +
  'pre{white-space:pre-wrap}' +
  'blockquote{margin:0 0 0 .5em;padding-left:1em;border-left:3px solid #999}' +
  '</style>'

/**
 * Die Mail im Look des Themes (Vorlage): Eine Mail ist für weißes Papier geschrieben, Outlook setzt an fast jedes Wort
 * color:black. Deshalb werden Schrift und Flächen der Mail hier übersteuert, alles in der Schrift der ContentBox, Links
 * in der Linkfarbe, Flächen durchsichtig. Bilder bleiben, wie sie sind. Die Farben kommen als Werte hinein, denn
 * CSS-Variablen der Seite reichen nicht in ein iframe.
 */
function themeStyle(): string {
  const style = getComputedStyle(document.body)
  const text = style.getPropertyValue('--contentbox-text').trim() || '#111827'
  const link = style.getPropertyValue('--bs-link-color').trim() || '#2563eb'
  const scheme = getComputedStyle(document.documentElement).colorScheme || 'normal'
  return (
    '<style>' +
    `:root{color-scheme:${scheme}}` +
    'html,body{background:transparent !important}' +
    `body,body *{color:${text} !important;background-color:transparent !important;background-image:none !important;` +
    `border-color:color-mix(in srgb,${text} 35%,transparent) !important}` +
    `a,a *{color:${link} !important}` +
    '</style>'
  )
}

/** Die Warnfarbe des Themes für die Grenze */
function warnColor(): string {
  return getComputedStyle(document.body).getPropertyValue('--bs-danger').trim() || '#dc2626'
}

/** Wie viele Zeichen vom Ende des neuen Teils als Anker dienen, wenn er nicht Zeichen für Zeichen vorn steht */
const ANCHOR_CHARACTERS = 40

function withoutWhitespace(text: string | null | undefined): string {
  return (text ?? '').replace(/\s+/g, '')
}

/**
 * Den Rest hinter dem neuen Teil abtrennen (Vorlage, User 2026-09-27): archiviert wird standardmäßig nur der neue Teil,
 * bei einer Weiterleitung steckt der Inhalt aber im Zitat darunter. Bei Antworten und Weiterleitungen steht der neue Teil
 * oben. Verglichen wird der reine Text ohne Leerraum, denn Exchange formatiert den neuen Teil anders als die Mail. Steht
 * er nicht Zeichen für Zeichen vorn, dient sein Ende als Anker (die erste Fundstelle, eine Signatur wiederholt sich im
 * Zitat). Findet sich beides nicht, bleibt alles, wie es ist: lieber nichts ausgrauen als das Falsche.
 */
function splitRest(mailDocument: Document, newPartHtml: string): MailNewPartState {
  if (!mailDocument.body) {
    return 'NOT_FOUND'
  }
  if (mailDocument.body.querySelector('[data-mail-rest]')) {
    return 'REST'
  }
  const newPart = new DOMParser().parseFromString(newPartHtml, 'text/html')
  newPart.querySelectorAll('script, style').forEach((element) => element.remove())
  const newText = withoutWhitespace(newPart.body?.textContent)

  // der Text der ganzen Mail, Knoten für Knoten, ohne Skript- und Stilblöcke
  const walker = mailDocument.createTreeWalker(mailDocument.body, NodeFilter.SHOW_TEXT)
  const textNodes: Text[] = []
  let wholeText = ''
  while (walker.nextNode()) {
    const node = walker.currentNode as Text
    const parentTag = node.parentElement?.tagName
    if (parentTag === 'SCRIPT' || parentTag === 'STYLE') {
      continue
    }
    textNodes.push(node)
    wholeText += withoutWhitespace(node.nodeValue)
  }
  if (!wholeText) {
    return 'NO_REST'
  }

  // wo endet der neue Teil in der ganzen Mail? Ist er leer (Weiterleitung ohne eigenes Wort), ist alles Rest
  let end = -1
  if (!newText) {
    end = 0
  } else if (wholeText.startsWith(newText)) {
    end = newText.length
  } else {
    const anchor = newText.slice(-ANCHOR_CHARACTERS)
    const found = wholeText.indexOf(anchor)
    if (found >= 0) {
      end = found + anchor.length
    }
  }
  if (end < 0) {
    return 'NOT_FOUND'
  }
  if (end >= wholeText.length) {
    return 'NO_REST'
  }
  if (end === 0) {
    wrapRest(mailDocument, mailDocument.body, 0)
    return 'REST'
  }
  // die Stelle im Baum: hinter dem end-ten Zeichen, Leerraum zählt nicht mit
  let counted = 0
  for (const node of textNodes) {
    const value = node.nodeValue ?? ''
    for (let position = 0; position < value.length; position += 1) {
      if (/\s/.test(value[position])) {
        continue
      }
      counted += 1
      if (counted < end) {
        continue
      }
      wrapRest(mailDocument, node, position + 1)
      return 'REST'
    }
  }
  return 'NOT_FOUND'
}

/** Alles ab dieser Stelle bis zum Ende in die Hülle data-mail-rest samt Schraffur, davor die Grenze */
function wrapRest(mailDocument: Document, startNode: Node, startOffset: number) {
  const range = mailDocument.createRange()
  range.setStart(startNode, startOffset)
  range.setEnd(mailDocument.body, mailDocument.body.childNodes.length)
  const rest = mailDocument.createElement('div')
  rest.setAttribute('data-mail-rest', '')
  // eigener Stapelkontext: Die Schraffur liegt über allem, was die Mail selbst positioniert
  rest.style.cssText = 'position:relative;isolation:isolate'
  rest.appendChild(range.extractContents())
  const hatch = mailDocument.createElement('div')
  hatch.setAttribute('data-mail-rest-hatch', '')
  hatch.style.cssText = 'position:absolute;inset:0;z-index:999;pointer-events:none;display:none'
  // mit important: Der Theme-Look nimmt sonst jedes Hintergrundbild weg
  hatch.style.setProperty(
    'background-image',
    'repeating-linear-gradient(-45deg,rgba(110,110,110,.35) 0 7px,rgba(255,255,255,.35) 7px 16px)',
    'important',
  )
  rest.appendChild(hatch)
  const boundary = mailDocument.createElement('div')
  boundary.setAttribute('data-mail-rest-boundary', '')
  mailDocument.body.append(boundary, rest)
}

/** Grenze und Rest nach dem Modus; die Farben mit important, sonst überstimmt sie der Theme-Look */
function styleRest(mailDocument: Document, mode: MailRestMode, title: string, text: string, color: string) {
  const boundary = mailDocument.querySelector<HTMLElement>('[data-mail-rest-boundary]')
  const hatch = mailDocument.querySelector<HTMLElement>('[data-mail-rest-hatch]')
  if (!boundary || !hatch) {
    return
  }
  boundary.replaceChildren()
  boundary.removeAttribute('style')
  hatch.style.display = mode === 'warn' ? 'block' : 'none'
  if (mode === 'show') {
    boundary.style.display = 'none'
    return
  }
  const titleElement = mailDocument.createElement('div')
  titleElement.textContent = title
  titleElement.style.cssText = 'font-weight:700;font-size:17px;letter-spacing:.02em'
  const textElement = mailDocument.createElement('div')
  textElement.textContent = text
  textElement.style.cssText = 'font-size:13px;margin-top:2px'
  boundary.append(titleElement, textElement)
  boundary.style.cssText = 'margin:18px 0 12px;padding:10px 14px;border:3px solid;border-radius:6px;font-family:system-ui,sans-serif'
  for (const element of [boundary, titleElement, textElement]) {
    element.style.setProperty('color', color, 'important')
    element.style.setProperty('border-color', color, 'important')
  }
  boundary.style.setProperty('background-color', `color-mix(in srgb,${color} 12%,transparent)`, 'important')
}

/**
 * Eine Mail als abgeschottetes iframe (nach der MailNachricht der Vorlage): nichts wird von außen nachgeladen, kein
 * Skript läuft (die sandbox erlaubt nur allow-same-origin, damit wir hineingreifen können: messen und den Rest
 * abtrennen; Skripte der Mail bräuchten allow-scripts, das steht nie dabei). Das iframe wird so hoch wie sein Text,
 * gemessen nach dem Laden, nach jedem Bild und bei jeder Änderung der Fensterbreite.
 *
 * Mit newPartHtml wird der Rest hinter dem neuen Teil abgetrennt und nach restMode gezeigt: warn = Grenze „bis hier
 * wird archiviert“ und der Rest schraffiert, show = alles wie die Mail.
 */
export function MailHtmlFrame({ html, title, newPartHtml = null, restMode = 'warn', onNewPartFound }: MailHtmlFrameProps) {
  const { t } = useTranslation()
  const frameRef = useRef<HTMLIFrameElement>(null)
  const [height, setHeight] = useState<number | null>(null)
  const [loaded, setLoaded] = useState(false)

  const srcDoc = NOTHING_FROM_OUTSIDE + BASE_STYLE + html + themeStyle()

  function measureHeight() {
    const mailDocument = frameRef.current?.contentDocument
    if (!mailDocument?.body) {
      return
    }
    // am Inhalt messen, nicht am Sichtfenster: Das ist nie kleiner als das iframe selbst
    setHeight(Math.ceil(Math.max(mailDocument.body.scrollHeight, mailDocument.body.offsetHeight, mailDocument.documentElement.offsetHeight)))
  }

  function afterLoad() {
    const mailDocument = frameRef.current?.contentDocument
    if (!mailDocument) {
      return
    }
    if (newPartHtml !== null) {
      onNewPartFound?.(splitRest(mailDocument, newPartHtml))
      styleRest(mailDocument, restMode, t('mailHtmlFrame.restWarnTitle'), t('mailHtmlFrame.restWarnText'), warnColor())
    }
    setLoaded(true)
    measureHeight()
    for (const image of Array.from(mailDocument.images)) {
      if (!image.complete) {
        image.addEventListener('load', measureHeight, { once: true })
      }
    }
    // Nachzügler (Schriften, spätes Layout): zweimal nachmessen kostet nichts
    window.setTimeout(measureHeight, 300)
    window.setTimeout(measureHeight, 1500)
  }

  // der Modus wechselt (Schalter „ganze Mail“): Grenze und Schraffur neu, die Box kostet Höhe
  useEffect(() => {
    const mailDocument = frameRef.current?.contentDocument
    if (!loaded || newPartHtml === null || !mailDocument) {
      return
    }
    styleRest(mailDocument, restMode, t('mailHtmlFrame.restWarnTitle'), t('mailHtmlFrame.restWarnText'), warnColor())
    measureHeight()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restMode, loaded])

  // neue Breite, neuer Umbruch, neue Höhe
  useEffect(() => {
    window.addEventListener('resize', measureHeight)
    return () => window.removeEventListener('resize', measureHeight)
  }, [])

  return (
    <iframe
      ref={frameRef}
      className="mail-html-frame"
      title={title}
      sandbox="allow-same-origin"
      srcDoc={srcDoc}
      onLoad={afterLoad}
      style={{ height: height !== null ? `${height}px` : '20rem' }}
    />
  )
}

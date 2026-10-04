import { useEffect, useRef, useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Icon } from './Icon.tsx'

interface FileDropzoneProps {
  /** bekommt die abgelegten oder gewählten Dateien. Ohne multiple höchstens eine, von einem Mehrfach-Drop die erste */
  onFiles: (files: File[]) => void
  /** Filter der Dateiwahl, z. B. ".pdf,application/pdf" */
  accept?: string
  multiple?: boolean
  /** Beschriftung der Zone, ohne Angabe „Datei hierher ziehen oder klicken“ */
  text?: string
  /** zusätzlich ein Knopf „Foto aufnehmen“: auf dem Telefon öffnet er die Kamera, am Rechner die Dateiwahl */
  camera?: boolean
  disabled?: boolean
}

/**
 * Die Zone für Datei-Uploads nach der FileDropzone der Vorlage: Datei hineinziehen oder klicken, dann öffnet sich die
 * Dateiwahl. Bewusst dumm: Sie liefert die rohen File-Objekte, was damit passiert, entscheidet der Aufrufer.
 *
 * Der Kamera-Knopf ist ein zweiter Eingang, kein Umschalter: Das Attribut capture erzwingt die Kamera, ein Feld, das es
 * trägt, öffnet auf dem Telefon nur noch die Aufnahme, die Galerie wäre nicht mehr erreichbar. Deshalb zwei getrennte
 * Eingänge. Jede Aufnahme fällt einzeln in onFiles, der Wert des Feldes wird danach geleert, sonst meldete es beim
 * zweiten Foto mit demselben Dateinamen keine Änderung.
 *
 * Solange die Zone montiert ist, ist der Browser-Standard fürs Datei-Ablegen fensterweit aus: Sonst öffnet der Browser
 * die gezogene Datei als Seite, sobald jemand neben der Zone loslässt.
 */
export function FileDropzone({
  onFiles,
  accept,
  multiple = false,
  text,
  camera = false,
  disabled = false,
}: FileDropzoneProps) {
  const { t } = useTranslation()
  const fileInput = useRef<HTMLInputElement>(null)
  const cameraInput = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)

  useEffect(() => {
    function preventBrowserDrop(event: Event) {
      event.preventDefault()
    }
    window.addEventListener('dragover', preventBrowserDrop)
    window.addEventListener('drop', preventBrowserDrop)
    return () => {
      window.removeEventListener('dragover', preventBrowserDrop)
      window.removeEventListener('drop', preventBrowserDrop)
    }
  }, [])

  function handOver(fileList: FileList | null) {
    const files = Array.from(fileList ?? [])
    if (files.length === 0 || disabled) {
      return
    }
    onFiles(multiple ? files : files.slice(0, 1))
  }

  function dragEnter(event: DragEvent) {
    event.preventDefault()
    if (!disabled) {
      setDragOver(true)
    }
  }

  function dragOverZone(event: DragEvent) {
    event.preventDefault()
    event.dataTransfer.dropEffect = disabled ? 'none' : 'copy'
    if (!disabled) {
      setDragOver(true)
    }
  }

  function drop(event: DragEvent) {
    event.preventDefault()
    setDragOver(false)
    handOver(event.dataTransfer.files)
  }

  return (
    <div className="file-dropzone-block">
      <button
        type="button"
        className={`file-dropzone${dragOver ? ' file-dropzone-dragover' : ''}`}
        disabled={disabled}
        onClick={() => fileInput.current?.click()}
        onDragEnter={dragEnter}
        onDragOver={dragOverZone}
        onDragLeave={() => setDragOver(false)}
        onDrop={drop}
      >
        <Icon name="upload" /> {text ?? (multiple ? t('common.dropzoneMultiple') : t('common.dropzone'))}
      </button>

      {camera && (
        <button
          type="button"
          className="file-dropzone-camera"
          disabled={disabled}
          onClick={() => cameraInput.current?.click()}
        >
          <Icon name="camera" /> {t('common.takePhoto')}
        </button>
      )}

      <input
        ref={fileInput}
        type="file"
        accept={accept}
        multiple={multiple}
        className="d-none"
        onChange={(event) => {
          handOver(event.target.files)
          // leeren, damit dieselbe Datei noch einmal wählbar ist
          event.target.value = ''
        }}
      />

      {/* capture="environment": die rückseitige Kamera. Ohne den Wert nimmt manches Gerät die Selfie-Kamera. */}
      {camera && (
        <input
          ref={cameraInput}
          type="file"
          accept="image/*"
          capture="environment"
          className="d-none"
          onChange={(event) => {
            handOver(event.target.files)
            event.target.value = ''
          }}
        />
      )}
    </div>
  )
}

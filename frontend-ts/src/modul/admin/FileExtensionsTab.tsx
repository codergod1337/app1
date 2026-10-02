import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import {
  changeFileExtensionPositions,
  deleteFileExtension,
  type FileExtension,
} from '../fileextension/FileExtension.ts'
import { fileExtensionBlocks } from '../fileextension/fileExtensionBlocks.ts'
import {
  changeFileExtensionCollectionPositions,
  deleteFileExtensionCollection,
  type FileExtensionCollection,
} from '../fileextension/FileExtensionCollection.ts'
import { FileShape } from '../fileextension/FileShape.tsx'
import type { EditFileExtensionCollectionState } from './EditFileExtensionCollectionTab.tsx'
import type { EditFileExtensionState } from './EditFileExtensionTab.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Endungen“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/fileextensions', label: 'Endungen' } }

/** Haken für ja, leer für nein */
function yes(value: boolean) {
  return (
    value && (
      <>
        <Icon name="check" />
        <span className="visually-hidden">ja</span>
      </>
    )
  )
}

/** Nach Position, bei gleicher Position nach der Endung, wie im Backend */
function byPosition(a: FileExtension, b: FileExtension) {
  return a.listingPosition - b.listingPosition || a.extension.localeCompare(b.extension)
}

/**
 * Tab „Endungen“ der Administration: oben die Gruppen, darunter der Endungskatalog nach Gruppen. Beides lässt sich
 * per Drag and Drop sortieren, die Endungen innerhalb ihrer Gruppe. Welche Dateiart welche Endung annimmt, pflegt
 * später die Freischalt-Matrix.
 */
export function FileExtensionsTab() {
  const navigate = useNavigate()
  const { fileExtensions, fileExtensionCollections, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedFileExtensions, setReorderedFileExtensions] = useState<FileExtension[] | null>(null)
  const [reorderedFileExtensionCollections, setReorderedFileExtensionCollections] = useState<
    FileExtensionCollection[] | null
  >(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfragen vor dem Löschen: null, solange keine offen ist
  const [fileExtensionToDelete, setFileExtensionToDelete] = useState<FileExtension | null>(null)
  const [fileExtensionCollectionToDelete, setFileExtensionCollectionToDelete] =
    useState<FileExtensionCollection | null>(null)

  const shownFileExtensions = reorderedFileExtensions ?? fileExtensions
  const shownFileExtensionCollections = reorderedFileExtensionCollections ?? fileExtensionCollections
  const blocks =
    shownFileExtensions !== null && shownFileExtensionCollections !== null
      ? fileExtensionBlocks(shownFileExtensionCollections, shownFileExtensions)
      : null

  function reportPositionError(error: unknown) {
    setPositionErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
  }

  /** Gruppen neu durchzählen und alle auf einmal speichern, danach die Stammdaten neu laden */
  function reorderFileExtensionCollections(newOrder: FileExtensionCollection[]) {
    const positions = positionsByKey(newOrder, (fileExtensionCollection) => fileExtensionCollection.key)
    setReorderedFileExtensionCollections(
      newOrder.map((fileExtensionCollection) => ({
        ...fileExtensionCollection,
        listingPosition: positions[fileExtensionCollection.key],
      })),
    )
    setPositionErrorMessage(null)
    changeFileExtensionCollectionPositions(positions)
      .then((result) => setReorderedFileExtensionCollections(result.data))
      .catch((error: unknown) => {
        reportPositionError(error)
        setReorderedFileExtensionCollections(null)
      })
      .finally(reloadMasterData)
  }

  /** Die Endungen einer Gruppe neu durchzählen und auf einmal speichern, danach die Stammdaten neu laden */
  function reorderFileExtensions(newOrderOfBlock: FileExtension[]) {
    const positions = positionsByKey(newOrderOfBlock, (fileExtension) => fileExtension.extension)
    setReorderedFileExtensions(
      (shownFileExtensions ?? [])
        .map((fileExtension) =>
          fileExtension.extension in positions
            ? { ...fileExtension, listingPosition: positions[fileExtension.extension] }
            : fileExtension,
        )
        .sort(byPosition),
    )
    setPositionErrorMessage(null)
    changeFileExtensionPositions(positions)
      .then((result) => setReorderedFileExtensions(result.data))
      .catch((error: unknown) => {
        reportPositionError(error)
        setReorderedFileExtensions(null)
      })
      .finally(reloadMasterData)
  }

  function editFileExtension(fileExtension: FileExtension) {
    const state: EditFileExtensionState = { ...ORIGIN_STATE, fileExtension: fileExtension.extension }
    navigate('/admin/fileextensions/edit', { state })
  }

  function editFileExtensionCollection(fileExtensionCollection: FileExtensionCollection) {
    const state: EditFileExtensionCollectionState = {
      ...ORIGIN_STATE,
      fileExtensionCollectionKey: fileExtensionCollection.key,
    }
    navigate('/admin/fileextensions/collections/edit', { state })
  }

  const collectionColumns: TableColumn<FileExtensionCollection>[] = [
    {
      header: 'Form',
      cell: (fileExtensionCollection) => (
        <span className="fs-5 d-inline-flex">
          <FileShape shape={fileExtensionCollection.symbolShape} />
        </span>
      ),
    },
    { header: 'Key', cell: (fileExtensionCollection) => <code>{fileExtensionCollection.key}</code> },
    { header: 'Name', cell: (fileExtensionCollection) => translate(fileExtensionCollection.displayName) },
    {
      header: 'Endungen',
      cell: (fileExtensionCollection) =>
        (shownFileExtensions ?? []).filter(
          (fileExtension) => fileExtension.fileExtensionCollectionKey === fileExtensionCollection.key,
        ).length,
      unimportant: true,
    },
  ]

  // Feste Breiten: Die Tabellen der Gruppen stehen untereinander, ihre Spalten genau übereinander
  const extensionColumns: TableColumn<FileExtension>[] = [
    {
      header: 'Endung',
      width: '10rem',
      cell: (fileExtension) => (
        <span className="d-inline-flex align-items-center gap-2">
          <code>.{fileExtension.extension}</code>
          {fileExtension.dangerous && (
            <Hoverlay text="Achtung: heikles Format, beim Freischalten wird gewarnt">
              <span className="text-danger">
                <Icon name="warning" />
              </span>
            </Hoverlay>
          )}
        </span>
      ),
    },
    // ohne Breite: bekommt den Rest
    { header: 'Beschreibung', cell: (fileExtension) => translate(fileExtension.description), unimportant: true },
    { header: 'OCR', width: '5rem', cell: (fileExtension) => yes(fileExtension.ocr), unimportant: true },
    { header: 'KI', width: '5rem', cell: (fileExtension) => yes(fileExtension.ki), unimportant: true },
    { header: 'Vorschau', width: '6rem', cell: (fileExtension) => yes(fileExtension.imagePreview), unimportant: true },
  ]

  const loadingSpinner = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
  const anyErrorMessage = errorMessage ?? positionErrorMessage

  return (
    <>
      <ContentBox
        title="Gruppen"
        actions={
          <NewEntityButton
            entity="FileExtensionCollection"
            onClick={() => navigate('/admin/fileextensions/collections/new', { state: ORIGIN_STATE })}
          />
        }
        footer={
          anyErrorMessage !== null ? (
            <span className="text-danger">{anyErrorMessage}</span>
          ) : shownFileExtensionCollections === null ? (
            loadingSpinner
          ) : (
            <span>{shownFileExtensionCollections.length} Gruppen. Jede legt die Form fest, in der ihre Dateien gezeichnet werden.</span>
          )
        }
      >
        {shownFileExtensionCollections !== null && (
          <Table
            columns={collectionColumns}
            rows={shownFileExtensionCollections}
            onReorder={reorderFileExtensionCollections}
            rowKey={(fileExtensionCollection) => fileExtensionCollection.key}
            detailTitle={(fileExtensionCollection) => translate(fileExtensionCollection.displayName)}
            emptyText="keine Gruppen"
            actions={(fileExtensionCollection) => (
              <>
                <EditButton label="bearbeiten" onClick={() => editFileExtensionCollection(fileExtensionCollection)} />
                <DeleteButton label="löschen" onClick={() => setFileExtensionCollectionToDelete(fileExtensionCollection)} />
              </>
            )}
          />
        )}
      </ContentBox>

      <ContentBox
        title="Endungen"
        actions={
          <NewEntityButton
            entity="FileExtension"
            onClick={() => navigate('/admin/fileextensions/new', { state: ORIGIN_STATE })}
          />
        }
        footer={
          anyErrorMessage !== null ? (
            <span className="text-danger">{anyErrorMessage}</span>
          ) : shownFileExtensions === null ? (
            loadingSpinner
          ) : (
            <span>{shownFileExtensions.length} Endungen. Tika bekommt jede Datei, die Schalter sagen, was das Format darüber hinaus kann.</span>
          )
        }
      >
        {blocks !== null &&
          blocks.map((block, index) => (
            <div key={block.fileExtensionCollection?.key ?? 'ohne-gruppe'} className={index > 0 ? 'mt-4' : undefined}>
              <h3 className="h6 d-flex align-items-center gap-2 mb-2">
                <FileShape shape={block.fileExtensionCollection?.symbolShape ?? null} />
                {block.fileExtensionCollection ? translate(block.fileExtensionCollection.displayName) : 'ohne Gruppe'}
                <span className="text-body-secondary fw-normal">({block.fileExtensions.length})</span>
              </h3>
              <Table
                columns={extensionColumns}
                rows={block.fileExtensions}
                onReorder={reorderFileExtensions}
                rowKey={(fileExtension) => fileExtension.extension}
                detailTitle={(fileExtension) => `.${fileExtension.extension}`}
                emptyText="keine Endungen in dieser Gruppe"
                actions={(fileExtension) => (
                  <>
                    <EditButton label="bearbeiten" onClick={() => editFileExtension(fileExtension)} />
                    <DeleteButton label="löschen" onClick={() => setFileExtensionToDelete(fileExtension)} />
                  </>
                )}
              />
            </div>
          ))}
      </ContentBox>

      <DeletePopup
        open={fileExtensionToDelete !== null}
        title="Endung löschen"
        onConfirm={() => deleteFileExtension(fileExtensionToDelete?.extension ?? '')}
        onDeleted={() => {
          setReorderedFileExtensions(null)
          reloadMasterData()
        }}
        onClose={() => setFileExtensionToDelete(null)}
      >
        Soll die Endung <strong>.{fileExtensionToDelete?.extension}</strong> wirklich gelöscht werden? Keine Dateiart
        nimmt sie danach noch an.
      </DeletePopup>
      <DeletePopup
        open={fileExtensionCollectionToDelete !== null}
        title="Gruppe löschen"
        onConfirm={() => deleteFileExtensionCollection(fileExtensionCollectionToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedFileExtensionCollections(null)
          setReorderedFileExtensions(null)
          reloadMasterData()
        }}
        onClose={() => setFileExtensionCollectionToDelete(null)}
      >
        Soll die Gruppe <strong>{translate(fileExtensionCollectionToDelete?.displayName ?? null)}</strong> wirklich
        gelöscht werden? Ihre Endungen stehen danach ohne Gruppe.
      </DeletePopup>
    </>
  )
}

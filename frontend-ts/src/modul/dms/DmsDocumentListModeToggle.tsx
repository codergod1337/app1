import { useTranslation } from 'react-i18next'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { showToast } from '../../components/toastStore.ts'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import { DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY, DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY, dmsDocumentListModeOf, nextDmsDocumentListMode } from './Dms.ts'

interface DmsDocumentListModeToggleProps {
  /** die UsersSetting des Orts, z. B. ARTIKELMAPPE_DATENTYPEN: jeder Ort merkt sich seine Ansicht selbst */
  settingKey: string
}

/**
 * Der Umschalter über einer Dokumentenliste je Dateiart, nach dem DatentypenSchalter der Vorlage: der Knopf nennt die
 * jetzige Ansicht, das Hoverlay die nächste, der Klick geht im Kreis ALL, WITH_FILES, FAVORITES. In den Favoriten steht
 * daneben der Mülleimer, sobald eine eigene Reihenfolge oder Zugeklapptes gespeichert ist: Er löscht beides, danach gilt
 * die Katalogreihenfolge, alles aufgeklappt.
 */
export function DmsDocumentListModeToggle({ settingKey }: DmsDocumentListModeToggleProps) {
  const { t } = useTranslation()
  const { settingValue, changeSetting, deleteSetting } = useUsersSettings()
  const mode = dmsDocumentListModeOf(settingValue(settingKey))
  const next = nextDmsDocumentListMode(mode)
  const hasFavorites = settingValue(DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY) !== null || settingValue(DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY) !== null

  function switchMode() {
    changeSetting(settingKey, next).catch((error: unknown) =>
      showToast({ kind: 'error', title: t('dms.fileSubClassDocuments.modeNotSaved'), text: error instanceof Error ? error.message : undefined }),
    )
  }

  async function resetFavorites() {
    try {
      if (settingValue(DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY) !== null) {
        await deleteSetting(DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY)
      }
      if (settingValue(DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY) !== null) {
        await deleteSetting(DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY)
      }
      showToast({ kind: 'success', title: t('dms.fileSubClassDocuments.favoritesReset') })
    } catch (error: unknown) {
      showToast({ kind: 'error', title: t('dms.fileSubClassDocuments.favoritesNotReset'), text: error instanceof Error ? error.message : undefined })
    }
  }

  return (
    <span className="d-inline-flex align-items-center gap-2">
      <Hoverlay text={t(`dms.fileSubClassDocuments.nextMode.${next}`)}>
        <button type="button" className="button-other" onClick={switchMode}>
          {mode === 'FAVORITES' && <Icon name="favorite" />} {t(`dms.fileSubClassDocuments.modes.${mode}`)}
        </button>
      </Hoverlay>
      {mode === 'FAVORITES' && hasFavorites && (
        <Hoverlay text={t('dms.fileSubClassDocuments.resetFavorites')}>
          <button type="button" className="button-other" aria-label={t('dms.fileSubClassDocuments.resetFavorites')} onClick={resetFavorites}>
            <Icon name="delete" />
          </button>
        </Hoverlay>
      )}
    </span>
  )
}

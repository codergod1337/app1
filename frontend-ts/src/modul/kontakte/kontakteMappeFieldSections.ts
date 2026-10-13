import {
  KONTAKTE_CONTACT_KIND_FIELD_NAME,
  KONTAKTE_ID_FIELD_NAME,
  KONTAKTE_NAME_FIELD_NAME,
  KONTAKTE_SYNC_DATE_FIELD_NAME,
  KONTAKTE_SYNC_ERROR_FIELD_NAME,
  KONTAKTE_SYNC_STATUS_FIELD_NAME,
  type KontaktePartnerRole,
} from './Kontakte.ts'

/** Die Schlüssel der Abschnittsnamen im Sprachkatalog, kontakte.mappe.sections.<key> */
export type KontakteMappeFieldSectionKey = 'address' | 'communication' | 'business' | 'characteristics' | 'notes' | 'audit' | 'remaining'

/** Ein Abschnitt der Übersicht: seine Felder des Kerns KONTAKTE in Anzeigereihenfolge, nach den ABAS-Namen */
export interface KontakteMappeFieldSection {
  key: KontakteMappeFieldSectionKey
  fieldNames: readonly string[]
}

/**
 * Die Geschäftsdaten je Rolle, nach den GeschaeftsBoxen der Kundenmappe der Vorlage: die Felder der ABAS-Kopie (Kunde
 * 0:1, Lieferant 1:1) mit Vorsilbe, die lesbaren Namen vor den Codes. Die Codes ohne Namen und alles Übrige der Rolle
 * zeigt „Übrige Felder“.
 */
const BUSINESS_FIELD_NAMES: Record<KontaktePartnerRole, readonly string[]> = {
  KUNDE: [
    'kundeZbedname',
    'kundeLbedname',
    'kundePbedname',
    'kundeMbed',
    'kundeVart',
    'kundeSelbukreisname',
    'kundeBelartkunde',
    'kundeLimit',
    'kundeWaehr',
    'kundeSprai',
    'kundeBranche',
    'kundeBetreuername',
    'kundeVertretname',
    'kundeErechok',
    'kundeErechmail',
    'kundeFrbez',
    'kundeGart',
    'kundeLaarta',
    'kundeEgkenn',
  ],
  LIEFERANT: [
    'lieferantZbedname',
    'lieferantLbedname',
    'lieferantPbedname',
    'lieferantMbed',
    'lieferantSelbukreisname',
    'lieferantBelartlief',
    'lieferantWaehr',
    'lieferantSprai',
    'lieferantBranche',
    'lieferantLaarta',
    'lieferantEgkenn',
  ],
}

/**
 * Die Abschnitte der Übersicht je Rolle, nach den FeldGruppen der Kundenmappe der Vorlage (Anschrift, Kommunikation,
 * Geschäftsdaten, Sync), dazu die eigenen Felder des 19ers (Bedingungen, Branche, Merkmale) und die Texte. Was keinen
 * Abschnitt hat und kein anderer Teil der Mappe zeigt, kommt in „Übrige Felder“, damit nichts verborgen bleibt.
 */
export function kontakteMappeFieldSectionsOf(partnerRole: KontaktePartnerRole): readonly KontakteMappeFieldSection[] {
  return [
    { key: 'address', fieldNames: ['ans', 'str', 'plz', 'nort', 'staatName', 'regionName', 'ustid'] },
    { key: 'communication', fieldNames: ['tele', 'mtele', 'fax', 'email', 'urlt', 'kontakt'] },
    { key: 'business', fieldNames: BUSINESS_FIELD_NAMES[partnerRole] },
    {
      key: 'characteristics',
      fieldNames: ['lbedName', 'zbedName', 'bverbName', 'frbez', 'brancheText', 'kenn', 'merkmal1Text', 'merkmal2Text', 'merkmal3Text', 'merkmal4Text', 'merkmal5Text', 'merkmal6Text'],
    },
    { key: 'notes', fieldNames: ['kontaktSuch', 'kontaktBem', 'kontaktFtext', 'ftext2'] },
    { key: 'audit', fieldNames: ['nummer', 'kontaktStand', 'kontaktZeich', KONTAKTE_SYNC_DATE_FIELD_NAME, KONTAKTE_SYNC_STATUS_FIELD_NAME, KONTAKTE_SYNC_ERROR_FIELD_NAME] },
  ]
}

/** Felder, die der Kopf der Mappe zeigt oder die nur Technik sind: Sie fehlen in „Übrige Felder“ */
export const KONTAKTE_MAPPE_FIELD_NAMES_SHOWN_ELSEWHERE: readonly string[] = [
  KONTAKTE_ID_FIELD_NAME,
  KONTAKTE_NAME_FIELD_NAME,
  KONTAKTE_CONTACT_KIND_FIELD_NAME,
  'cursorDate',
  'userGrantedAccess',
  '_version_',
]

/** Die Vorsilbe der Geschäftsfelder der jeweils anderen Rolle: In der Kundenmappe fehlen die des Lieferanten und umgekehrt */
export const KONTAKTE_OTHER_ROLE_FIELD_PREFIX: Record<KontaktePartnerRole, string> = {
  KUNDE: 'lieferant',
  LIEFERANT: 'kunde',
}

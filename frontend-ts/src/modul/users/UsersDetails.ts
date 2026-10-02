import { request } from '../../api/client.ts'

/** Info, Profiltext und Kontaktdaten eines Users (UsersDetails.java). Ohne gespeicherte Zeile ist alles leer. */
export interface UsersDetails {
  usersGuid: string
  /** mehrsprachiges JSON: ein Satz unter dem Namen */
  info: string | null
  /** mehrsprachiges JSON: der lange Text unter „Über mich“ */
  profilText: string | null
  webseite: string | null
  mobile: string | null
  steam: string | null
  discord: string | null
  insta: string | null
  /** setzt nur der Admin */
  kundenNummer: string | null
  /** setzt nur der Admin */
  lieferantenNummer: string | null
  /** gescheiterte Passwortprüfungen, zählt bisher niemand */
  pwUnsuccessfull: number
}

/** Was der User selbst an seinen Details ändert. Was fehlt oder leer ist, wird geleert. */
export type UsersDetailsData = Pick<
  UsersDetails,
  'info' | 'profilText' | 'webseite' | 'mobile' | 'steam' | 'discord' | 'insta'
>

// ===== Endpunkte des UsersDetailsController (jeder bei sich selbst) =====

const USERS_DETAILS_PATH = '/api/rest/v1/usersdetails'

export function getUsersDetailsByUsersGuid(usersGuid: string) {
  return request<UsersDetails>('GET', `${USERS_DETAILS_PATH}/${usersGuid}`)
}

/** kundenNummer, lieferantenNummer und pwUnsuccessfull bleiben unverändert. */
export function updateUsersDetails(usersGuid: string, changedUsersDetailsData: UsersDetailsData) {
  return request<UsersDetails>('PUT', USERS_DETAILS_PATH, { usersGuid, ...changedUsersDetailsData })
}

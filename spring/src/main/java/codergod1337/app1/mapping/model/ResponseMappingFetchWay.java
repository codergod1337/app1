package codergod1337.app1.mapping.model;

import java.util.Map;

/**
 * Der Fetchweg eines {@link ResponseMapping} (User 2026-10-10: jedes Mapping hat einen hinterlegten Fetchweg), das JSON in
 * {@link ResponseMapping#getFetchWayJson()}. Bei STERNZEIT reicht die Beispiel-Sternzeit, bei INFOSYS der hinterlegte
 * Aufruf mit Beispielwerten für seine Platzhalter (User 2026-10-11: jede Infosystem-Anfrage über einen hinterlegten
 * Aufruf; das Mapping kennt ihn nur über seinen Key, ein loser Fremdschlüssel wie am Subfetch-Eintrag).
 *
 * @param kind                   STERNZEIT, INFOSYS oder JSON
 * @param sternzeit              bei STERNZEIT: die Beispiel-Sternzeit, z. B. (87448,19,0)
 * @param abasInfosysKey         bei INFOSYS: der Key des hinterlegten Aufrufs, z. B. KUNDEN_ARTIKELNUMMERN
 * @param valueByPlaceholderName bei INFOSYS: je Platzhalter der Vorlage ein Beispielwert, z. B. {"kartikel": "10502057"}
 */
public record ResponseMappingFetchWay(ResponseMappingFetchWayKind kind, String sternzeit, String abasInfosysKey,
		Map<String, String> valueByPlaceholderName) {
}

package codergod1337.app1.mapping.model;

/**
 * Welche Rolle der Wert eines Eintrags als id des Dokuments spielt (User 2026-10-10): Die eindeutige id ist oft die
 * Sternzeit, aber nicht immer, beim Kunden ist es die Kundennummer; manche Entitäten haben keine eigene id und brauchen
 * eine Erweiterung; und durch die ABAS-Struktur kann es mehrere eindeutige Werte geben. Welcher Wert welche Alternative
 * ist, sagt der Key des Eintrags selbst, z. B. FIRMA_NUMMER.
 */
public enum ResponseMappingLineIdRole {

	/** Die eindeutige id des Dokuments, höchstens einmal je Mapping */
	PRIMARY,

	/** Erweitert den PRIMARY zur Kennung, z. B. die Positionsnummer zur Sternzeit der Chance */
	COMPOSITE,

	/** Auch eindeutig, aber im Zweifel gilt der PRIMARY; kennzeichnet, dass ein Suchen über diesen Wert Sinn ergibt */
	ALTERNATIVE

}

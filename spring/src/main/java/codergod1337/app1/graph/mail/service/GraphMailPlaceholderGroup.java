package codergod1337.app1.graph.mail.service;

import java.util.List;
import java.util.Map;

/**
 * Eine Gruppe von Platzhaltern, die eine Vorlage als {gruppe.name} einsetzen kann, z. B. die Felder des Users. Jede
 * Gruppe ist ein eigener Bean: Der Editor zeigt ihre Namen, das Muster nimmt ihre Beispielwerte, und wer eine Mail
 * schickt, übergibt die echten Werte der Gruppen, die der Typ der Vorlage verlangt. Eine neue Gruppe (z. B. ein
 * Dokument) ist eine neue Klasse plus ihr Eintrag am Vorlagentyp, sonst nichts.
 */
public interface GraphMailPlaceholderGroup {

	/** Der Teil vor dem Punkt, z. B. users. Nur Kleinbuchstaben. */
	String groupKey();

	/** Die Namen hinter dem Punkt, in Anzeigereihenfolge, z. B. vorname. */
	List<String> placeholderNames();

	/** Beispielwerte für das Muster im Editor, je Name einer. */
	Map<String, String> sampleValues();

}

package codergod1337.app1.message.archive.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Beteiligter einer Mail auf der Aufbereiten-Seite (User 2026-10-14) mit den 19er-Kontakten zu seiner Adresse.
 * Eine Adresse kann an mehreren Ansprechpartnern hängen, deshalb eine Liste.
 *
 * @param role     Von, Gesendet von, An, Cc oder Bcc
 * @param name     der Name, wie ihn die Mail trägt; null ohne
 * @param address  die Adresse, klein geschrieben
 * @param contacts die Kontakte aus dem Kern KONTAKTE mit dieser Adresse, so weit der User ihre Felder lesen darf; leer bei
 *                 unseren eigenen Adressen (Domäne der Postfächer) und ohne die AR KONTAKTE
 */
public record MessageArchiveParticipant(Role role, String name, String address, List<Map<String, Object>> contacts) {

	/** Die Rolle in der Mail */
	public enum Role {
		FROM, SENDER, TO, CC, BCC
	}

}

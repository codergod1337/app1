package codergod1337.app1.dms.model;

import java.util.List;
import java.util.Locale;

/**
 * Die Sprachen, die ein DMS-Dokument haben kann (Vorgabe 2026-10-03): die 18 gängigsten als ISO-639-1-Code, dieselbe
 * Liste wie im Frontend (DmsDocumentLanguage.ts), das die Flaggen und die Reihenfolge kennt. Das Backend prüft nur, ob
 * ein Code dazugehört. null heißt keine Sprache, erlaubt in jeder Gruppe, in Gruppen ohne Sprache der einzige Wert.
 */
public final class DmsDocumentLanguages {

	public static final List<String> CODES = List.of("de", "en", "bg", "ar", "cs", "el", "es", "fr", "hu", "it", "ja",
			"nl", "pl", "pt", "ro", "ru", "tr", "zh");

	private DmsDocumentLanguages() {
	}

	public static boolean isKnownCode(String code) {
		return code != null && CODES.contains(code);
	}

	/** Der Code klein und ohne Region (zh-CN wird zh), null bleibt null. So landen Erkennung und Eingabe auf der Liste. */
	public static String normalizeCode(String code) {
		if (code == null) {
			return null;
		}
		String trimmed = code.strip().toLowerCase(Locale.ROOT);
		int dash = trimmed.indexOf('-');
		return dash > 0 ? trimmed.substring(0, dash) : trimmed;
	}

}

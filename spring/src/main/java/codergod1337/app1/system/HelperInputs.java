package codergod1337.app1.system;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Statische Prüfmethoden für Eingaben. */
public final class HelperInputs {

	/** Nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben, nie zwei _ hintereinander. */
	private static final Pattern VALID_KEY = Pattern.compile("(?!.*__)[A-Z]([A-Z0-9_]*[A-Z])?");

	/** SHA-256 als Hex-String, wie ihn der Browser bildet: genau 64 Zeichen 0–9 und a–f. */
	private static final Pattern VALID_SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

	private HelperInputs() {
	}

	/**
	 * true, wenn der Text ein gültiger Key ist: nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben,
	 * nie zwei _ hintereinander, höchstens 200 Zeichen. Beispiele: ADMIN, FILE_READ, LEVEL_2_USER.
	 * {@code null} ist kein gültiger Key.
	 */
	public static boolean isValidKey(String keyText) {
		return keyText != null && keyText.length() <= 200 && VALID_KEY.matcher(keyText).matches();
	}

	/**
	 * true, wenn der Text eine gültige guid im Format 8-4-4-4-12 ist, z. B. 3f2b8c1e-9a4d-4e7b-8c2f-1d5e6a7b8c9d.
	 * {@code null} ist keine gültige guid.
	 */
	public static boolean isValidGuid(String guidText) {
		if (guidText == null) {
			return false;
		}
		try {
			// UUID.fromString nimmt auch Kurzformen wie 1-2-3-4-5 an, erst der Vergleich sichert das volle Format
			return UUID.fromString(guidText).toString().equals(guidText.toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * true, wenn der Text ein SHA-256 als Hex-String ist: genau 64 Zeichen 0–9 und a–f. So kommen Passwörter aus dem
	 * Browser, nie im Klartext. {@code null} ist kein gültiger Hash.
	 */
	public static boolean isValidSha256Hex(String sha256Hex) {
		return sha256Hex != null && VALID_SHA256_HEX.matcher(sha256Hex).matches();
	}

}

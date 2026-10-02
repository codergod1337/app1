package codergod1337.app1.system.security.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Erzeugt und hasht die Tokens des Logins: Abholschein, Bestätigung, Refresh. Alle funktionieren gleich: Der Klartext
 * geht einmal nach draußen (Antwort, Link, Cookie), gespeichert wird nur sein SHA-256. Kommt er zurück, wird er wieder
 * gehasht und nachgeschlagen. Ein gestohlener DB-Auszug enthält so nichts Benutzbares.
 */
public final class LoginToken {

	/** 256 Bit Zufall: Daran hängt, dass niemand einen Token errät. Nie kleiner machen. */
	private static final int TOKEN_BYTES = 32;

	/** Kryptographisch sicherer Zufall, nicht Random: dessen Werte lassen sich vorhersagen. */
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private LoginToken() {
	}

	/**
	 * Ein neuer Zufallswert: 32 Byte als base64url ohne Padding, etwa 43 Zeichen. Passt so ohne Umkodierung in Link
	 * und Cookie. Geht einmal nach draußen, gespeichert wird nur sein Hash.
	 */
	public static String createLoginToken() {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}

	/**
	 * SHA-256 als Hex (64 Zeichen), zum Speichern und Nachschlagen. SHA-256 statt BCrypt: BCrypt salzt zufällig, danach
	 * ließe sich nicht suchen. Und einen Zufallswert aus 32 Byte rät niemand, er braucht keine künstliche Langsamkeit.
	 */
	public static String hashLoginToken(String loginToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(loginToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 nicht verfügbar", e);
		}
	}

}

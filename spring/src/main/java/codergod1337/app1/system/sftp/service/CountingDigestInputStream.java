package codergod1337.app1.system.sftp.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Zählt die Bytes, die durchlaufen, und rechnet dabei auf Wunsch den SHA-256 mit. So kennt der SftpService nach dem
 * Schreiben Größe und Hash, ohne die Datei ein zweites Mal zu lesen. Der umhüllte Strom gehört dem Aufrufer, close()
 * schließt ihn deshalb nicht.
 */
final class CountingDigestInputStream extends FilterInputStream {

	/** null: nur zählen */
	private final MessageDigest digest;
	private long count;

	CountingDigestInputStream(InputStream content, MessageDigest digest) {
		super(content);
		this.digest = digest;
	}

	@Override
	public int read() throws IOException {
		int value = in.read();
		if (value >= 0) {
			count++;
			if (digest != null) {
				digest.update((byte) value);
			}
		}
		return value;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		int read = in.read(buffer, offset, length);
		if (read > 0) {
			count += read;
			if (digest != null) {
				digest.update(buffer, offset, read);
			}
		}
		return read;
	}

	/** Überspringen liefe am Hash vorbei: stattdessen lesen und verwerfen */
	@Override
	public long skip(long bytesToSkip) throws IOException {
		byte[] buffer = new byte[8192];
		long skipped = 0;
		while (skipped < bytesToSkip) {
			int read = read(buffer, 0, (int) Math.min(buffer.length, bytesToSkip - skipped));
			if (read < 0) {
				break;
			}
			skipped += read;
		}
		return skipped;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void close() {
		// der Strom gehört dem Aufrufer, der schließt ihn auch
	}

	/** so viele Bytes sind bisher durchgelaufen */
	long count() {
		return count;
	}

	/** der SHA-256 der durchgelaufenen Bytes als Hex, nur mit digest. Danach beginnt der Digest von vorn. */
	String sha256Hex() {
		return HexFormat.of().formatHex(digest.digest());
	}

}

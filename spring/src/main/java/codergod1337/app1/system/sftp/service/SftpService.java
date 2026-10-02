package codergod1337.app1.system.sftp.service;

import codergod1337.app1.system.sftp.SftpUnavailableException;
import codergod1337.app1.system.sftp.model.SftpProperties;
import codergod1337.app1.system.sftp.model.SftpStoredFile;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die einzige Stelle, die mit dem Dateispeicher redet. Kennt keine Rechte und keine Dateiarten: Wer eine Datei sehen
 * darf, entscheidet der Aufrufer, hier kommen fertige Pfade und Ströme an.
 *
 * Alle Pfade sind relativ zum root-dir und werden geprüft: kein absoluter Pfad, kein „..“, kein Backslash. Kein
 * Aufrufer kann die Wurzel verlassen. Alles strömt, auch eine Datei von vielen Gigabyte liegt nie im Arbeitsspeicher.
 * Die Ströme gehören dem Aufrufer, er schließt sie.
 *
 * Schreiben geht immer über eine .part-Datei: schreiben, Größe prüfen, umbenennen. Bricht eine Übertragung ab, bleibt
 * keine halbe Datei unter dem endgültigen Namen liegen. Das ist wichtig, weil der Name der Hash des Inhalts ist: Eine
 * halbe Datei gälte sonst als „haben wir schon“.
 *
 * Ob die Grenze je User gilt, entscheidet der Pool über den angemeldeten User, nicht diese Klasse.
 */
@Service
public class SftpService {

	private static final Logger log = LoggerFactory.getLogger(SftpService.class);

	private static final String PART_SUFFIX = ".part";
	/** Ordner, die es auf dem Server schon gibt: spart je Datei die Nachfrage. Wird bei dieser Größe geleert. */
	private static final int MAX_KNOWN_DIRECTORIES = 10_000;
	private static final int MAX_PATH_LENGTH = 500;
	private static final Pattern VALID_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

	private final SftpConnectionPool sftpConnectionPool;
	private final SftpPathService sftpPathService;
	private final String rootDir;
	private final Set<String> knownDirectories = ConcurrentHashMap.newKeySet();

	public SftpService(SftpConnectionPool sftpConnectionPool, SftpPathService sftpPathService,
			SftpProperties properties) {
		this.sftpConnectionPool = sftpConnectionPool;
		this.sftpPathService = sftpPathService;
		// ohne Schrägstrich am Ende, der kommt beim Zusammensetzen
		this.rootDir = properties.rootDir().replaceAll("/+$", "");
	}

	/**
	 * Legt den Inhalt unter diesem Pfad ab, z. B. SftpPathService.relativePath(sha256). Fehlende Ordner werden
	 * angelegt. Gibt es die Datei schon, bleibt sie: Der Name ist der Hash, der Inhalt also derselbe. Wer hier andere
	 * Namen vergibt, muss selbst für eindeutige sorgen.
	 */
	public void writeFileData(InputStream content, String relativePath) {
		String absolutePath = absolutePath(relativePath);
		try (PooledSftpChannel lent = sftpConnectionPool.borrowSftpChannel()) {
			CountingDigestInputStream counting = new CountingDigestInputStream(content, null);
			writePart(lent, counting, absolutePath + PART_SUFFIX);
			finishPart(lent, absolutePath + PART_SUFFIX, absolutePath);
		}
	}

	/**
	 * Legt den Inhalt ab und rechnet dabei den SHA-256 mit: für Uploads, die direkt durchgereicht werden, ohne dass
	 * jemand den Hash vorher kennt. Geschrieben wird in den Tagesordner unter einem Zufallsnamen, am Ende steht der
	 * Hash fest und die Datei bekommt ihren Namen <sha256>.bin. Gibt es sie in diesem Ordner schon, bleibt die alte.
	 * Ob dieselben Bytes schon an einem anderen Tag liegen, weiß nur der Aufrufer (Solr).
	 */
	public SftpStoredFile writeFileData(InputStream content) {
		String directory = sftpPathService.nextRelativeDirectory();
		String partAbsolutePath = absolutePath(directory + "/" + UUID.randomUUID() + PART_SUFFIX);
		try (PooledSftpChannel lent = sftpConnectionPool.borrowSftpChannel()) {
			CountingDigestInputStream counting = new CountingDigestInputStream(content, sha256());
			writePart(lent, counting, partAbsolutePath);
			String sha256Hex = counting.sha256Hex();
			String relativePath = directory + "/" + sha256Hex + SftpPathService.FILE_SUFFIX;
			finishPart(lent, partAbsolutePath, absolutePath(relativePath));
			return new SftpStoredFile(relativePath, sha256Hex, counting.count());
		}
	}

	/**
	 * Schreibt die Datei in den Zielstrom, meist den der HTTP-Antwort. 404, wenn es sie nicht gibt. Bricht der
	 * Empfänger ab (Browser schließt den Tab), ist das kein Fehler des Dateispeichers.
	 */
	public void readFileData(String relativePath, OutputStream target) {
		String absolutePath = absolutePath(relativePath);
		try (PooledSftpChannel lent = sftpConnectionPool.borrowSftpChannel()) {
			InputStream fromServer = openForRead(lent, absolutePath);
			// der Strom vom Server gehört uns, der Zielstrom dem Aufrufer
			try (fromServer) {
				fromServer.transferTo(target);
			} catch (IOException e) {
				// mitten im Lesen abgebrochen: der Kanal steckt in der Übertragung fest und wird verworfen
				lent.markBroken();
				throw new UncheckedIOException("Übertragung von " + absolutePath + " abgebrochen", e);
			}
		}
	}

	/** Gibt es die Datei? „Nein“ ist eine Antwort, kein Fehler. */
	public boolean fileDataExists(String relativePath) {
		String absolutePath = absolutePath(relativePath);
		try (PooledSftpChannel lent = sftpConnectionPool.borrowSftpChannel()) {
			try {
				return exists(lent.channel(), absolutePath);
			} catch (SftpException e) {
				throw failed(lent, "Nachsehen von " + absolutePath, e);
			}
		}
	}

	/** Löscht die Datei. War sie schon weg, gilt das als Erfolg: Der gewünschte Zustand ist hergestellt. */
	public void deleteFileData(String relativePath) {
		String absolutePath = absolutePath(relativePath);
		try (PooledSftpChannel lent = sftpConnectionPool.borrowSftpChannel()) {
			try {
				lent.channel().rm(absolutePath);
			} catch (SftpException e) {
				if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
					log.debug("SFTP: {} war schon weg", absolutePath);
					return;
				}
				throw failed(lent, "Löschen von " + absolutePath, e);
			}
		}
	}

	// ===== Schreiben über .part =====

	/** Schreibt nach partAbsolutePath und prüft die Größe. Scheitert etwas, ist die .part danach weg. */
	private void writePart(PooledSftpChannel lent, CountingDigestInputStream content, String partAbsolutePath) {
		ChannelSftp channel = lent.channel();
		try {
			ensureDirectory(channel, parentOf(partAbsolutePath));
			channel.put(content, partAbsolutePath, ChannelSftp.OVERWRITE);
			long storedSize = channel.stat(partAbsolutePath).getSize();
			if (storedSize != content.count()) {
				removeQuietly(channel, partAbsolutePath);
				log.error("SFTP: {} unvollständig, {} von {} Bytes angekommen", partAbsolutePath, storedSize, content.count());
				throw new SftpUnavailableException("die Übertragung zum Dateispeicher war unvollständig");
			}
		} catch (SftpException e) {
			removeQuietly(channel, partAbsolutePath);
			throw failed(lent, "Schreiben nach " + partAbsolutePath, e);
		}
	}

	/** Die fertige .part bekommt ihren Namen. Gibt es das Ziel schon, bleibt es, die .part fällt weg. */
	private void finishPart(PooledSftpChannel lent, String partAbsolutePath, String absolutePath) {
		ChannelSftp channel = lent.channel();
		try {
			if (exists(channel, absolutePath)) {
				log.debug("SFTP: {} gab es schon, die neue Übertragung fällt weg", absolutePath);
				channel.rm(partAbsolutePath);
				return;
			}
			channel.rename(partAbsolutePath, absolutePath);
		} catch (SftpException e) {
			removeQuietly(channel, partAbsolutePath);
			throw failed(lent, "Umbenennen nach " + absolutePath, e);
		}
	}

	/**
	 * Legt den Ordner samt allen fehlenden darüber an. SFTP kennt kein „leg den ganzen Pfad an“, nur eine Ebene auf
	 * einmal. Legen zwei Threads denselben Ordner gleichzeitig an, scheitert einer: Dann wird nachgesehen, ob er
	 * inzwischen da ist. Bekannte Ordner werden gemerkt, damit nicht jede Datei die Nachfrage kostet.
	 */
	private void ensureDirectory(ChannelSftp channel, String directory) throws SftpException {
		if (directory == null || knownDirectories.contains(directory)) {
			return;
		}
		if (!exists(channel, directory)) {
			ensureDirectory(channel, parentOf(directory));
			try {
				channel.mkdir(directory);
			} catch (SftpException e) {
				if (!exists(channel, directory)) {
					throw e;
				}
				// ein anderer Thread war schneller, in Ordnung
			}
		}
		if (knownDirectories.size() >= MAX_KNOWN_DIRECTORIES) {
			knownDirectories.clear();
		}
		knownDirectories.add(directory);
	}

	// ===== Helfer =====

	private InputStream openForRead(PooledSftpChannel lent, String absolutePath) {
		try {
			return lent.channel().get(absolutePath);
		} catch (SftpException e) {
			if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "die Datei liegt nicht auf dem Dateispeicher");
			}
			throw failed(lent, "Lesen von " + absolutePath, e);
		}
	}

	/** stat, „gibt es nicht“ als Antwort statt als Fehler */
	private static boolean exists(ChannelSftp channel, String absolutePath) throws SftpException {
		try {
			channel.stat(absolutePath);
			return true;
		} catch (SftpException e) {
			if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				return false;
			}
			throw e;
		}
	}

	/** Aufräumen nach einem Fehler darf den eigentlichen Fehler nicht verdecken */
	private static void removeQuietly(ChannelSftp channel, String absolutePath) {
		try {
			channel.rm(absolutePath);
		} catch (SftpException e) {
			if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				log.debug("SFTP: Aufräumen von {} fehlgeschlagen: {}", absolutePath, e.getMessage());
			}
		}
	}

	/**
	 * Der Fehler nach außen bleibt allgemein, Pfad und Ursache stehen im Log. Ist die Verbindung selbst das Problem,
	 * wird sie verworfen.
	 */
	private static SftpUnavailableException failed(PooledSftpChannel lent, String what, SftpException e) {
		if (e.id == ChannelSftp.SSH_FX_NO_CONNECTION || e.id == ChannelSftp.SSH_FX_CONNECTION_LOST
				|| e.getCause() instanceof IOException) {
			lent.markBroken();
		}
		log.error("SFTP: {} fehlgeschlagen (Code {}): {}", what, e.id, e.getMessage());
		return new SftpUnavailableException("der Dateispeicher ist gerade nicht erreichbar", e);
	}

	/** Der geprüfte relative Pfad mit der Wurzel davor. Programmierfehler der Aufrufer fallen hier auf. */
	private String absolutePath(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			throw new IllegalArgumentException("relativePath fehlt");
		}
		if (relativePath.length() > MAX_PATH_LENGTH || relativePath.startsWith("/") || relativePath.contains("\\")) {
			throw new IllegalArgumentException("relativePath ist ungültig: " + relativePath);
		}
		for (String segment : relativePath.split("/")) {
			if (!VALID_SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) {
				throw new IllegalArgumentException("relativePath ist ungültig: " + relativePath);
			}
		}
		return rootDir + "/" + relativePath;
	}

	/** Der Ordner über einem Pfad, null oberhalb der Wurzel */
	private String parentOf(String absolutePath) {
		int lastSlash = absolutePath.lastIndexOf('/');
		if (lastSlash <= 0 || absolutePath.length() <= rootDir.length()) {
			return null;
		}
		return absolutePath.substring(0, lastSlash);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 fehlt in dieser JVM", e);
		}
	}

}

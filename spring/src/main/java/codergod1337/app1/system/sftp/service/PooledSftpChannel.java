package codergod1337.app1.system.sftp.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eine ausgeliehene SFTP-Verbindung mit Rückgabepflicht: Sie wird nur so benutzt:
 *
 * <pre>
 * try (PooledSftpChannel lent = pool.borrowSftpChannel()) {
 *     lent.channel().put(…);
 * } // hier geht sie an den Pool zurück, auch bei einer Ausnahme
 * </pre>
 *
 * Eine zweite Rückgabe wird ignoriert, sonst gäbe der Pool mehr Plätze frei, als er hat. Nach einem Fehler meldet der
 * Aufrufer markBroken(): Dann wird die Verbindung nicht wieder verliehen, sondern geschlossen.
 *
 * Zwei Prüfungen mit sehr unterschiedlichen Kosten: isLocallyConnected() sieht nur, was der Client selbst weiß, und
 * kostet nichts. isAlive() fragt beim Server nach und erkennt auch Verbindungen, die der Server längst aufgegeben hat.
 */
public final class PooledSftpChannel implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(PooledSftpChannel.class);

	private final Session session;
	private final ChannelSftp channel;
	private final SftpConnectionPool pool;

	/** wem sie gerade gehört. null: dem System, dann gibt es keinen Platz je User freizugeben. */
	private volatile UUID usersGuid;
	private final AtomicBoolean lent = new AtomicBoolean(false);
	private volatile boolean broken;
	private volatile long lastUsedNanos = System.nanoTime();

	PooledSftpChannel(Session session, ChannelSftp channel, SftpConnectionPool pool) {
		this.session = session;
		this.channel = channel;
		this.pool = pool;
	}

	/** Die rohe Verbindung. Was man damit tun kann, weiß die Bibliothek besser als jede Hülle. */
	public ChannelSftp channel() {
		return channel;
	}

	/** Nach einem Fehler: nicht wieder verleihen, beim Zurückgeben schließen. */
	public void markBroken() {
		broken = true;
	}

	boolean isBroken() {
		return broken;
	}

	UUID usersGuid() {
		return usersGuid;
	}

	/** Beim Ausleihen: wem sie ab jetzt gehört */
	PooledSftpChannel lendTo(UUID newUsersGuid) {
		usersGuid = newUsersGuid;
		broken = false;
		lent.set(true);
		return this;
	}

	void touch() {
		lastUsedNanos = System.nanoTime();
	}

	/** so lange liegt sie unbenutzt im Pool */
	Duration idleTime() {
		return Duration.ofNanos(System.nanoTime() - lastUsedNanos);
	}

	/** Sieht von hier aus offen aus, ohne Kontakt zum Server */
	boolean isLocallyConnected() {
		return channel.isConnected() && session.isConnected();
	}

	/** Lebt wirklich noch: ein stat auf das Arbeitsverzeichnis, die kleinstmögliche Anfrage an den Server */
	boolean isAlive() {
		if (!isLocallyConnected()) {
			return false;
		}
		try {
			channel.stat(".");
			return true;
		} catch (SftpException e) {
			log.debug("SFTP-Verbindung ist tot: {}", e.getMessage());
			return false;
		}
	}

	/** Schließt Kanal und Sitzung und wirft nie: Aufräumen darf den eigentlichen Fehler nicht verdecken. */
	void closeQuietly() {
		try {
			channel.disconnect();
		} catch (RuntimeException e) {
			log.debug("SFTP-Kanal schließen fehlgeschlagen: {}", e.getMessage());
		}
		try {
			session.disconnect();
		} catch (RuntimeException e) {
			log.debug("SFTP-Sitzung schließen fehlgeschlagen: {}", e.getMessage());
		}
	}

	/** Das Ende des try-Blocks: die Rückgabe an den Pool. Nur die erste zählt. */
	@Override
	public void close() {
		if (lent.compareAndSet(true, false)) {
			pool.returnSftpChannel(this);
		}
	}

}

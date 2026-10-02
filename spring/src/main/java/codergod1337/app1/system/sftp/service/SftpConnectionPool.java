package codergod1337.app1.system.sftp.service;

import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.sftp.SftpPerUserLimitException;
import codergod1337.app1.system.sftp.SftpUnavailableException;
import codergod1337.app1.system.sftp.model.SftpProperties;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hält SFTP-Verbindungen offen und verleiht sie. Eine Verbindung aufzubauen kostet Schlüsseltausch und Anmeldung,
 * leicht ein paar hundert Millisekunden: Das soll nicht bei jeder Datei fällig sein.
 *
 * Zwei Grenzen, beide als faire Semaphore (wer zuerst wartet, kommt zuerst dran): eine für alle Verbindungen zusammen
 * und eine je angemeldetem User. Ob die Grenze je User gilt, entscheidet nicht der Aufrufer, sondern der
 * CurrentUsersProvider: Ist ein Mensch angemeldet, zählt sein Platz, läuft das Backend aus sich heraus (Jobs, Import),
 * gilt nur die globale Grenze. Ein Controller kann die Grenze je User damit nicht umgehen.
 *
 * Sicherheit: Nur der Server mit dem konfigurierten Host-Key wird akzeptiert. Anmeldung mit Passwort oder Schlüssel.
 *
 * Verbindungen werden beim Ausleihen nur dann beim Server geprüft, wenn sie länger unbenutzt waren. Ein Keepalive hält
 * sie offen, ein Aufräumer schließt, was lange unbenutzt liegt, und hält min-idle-connections vor. Der Start wartet
 * nicht auf den Server: Ist er nicht da, versucht es der Aufräumer wieder.
 */
@Component
public class SftpConnectionPool {

	private static final Logger log = LoggerFactory.getLogger(SftpConnectionPool.class);

	/** War eine Verbindung länger unbenutzt, fragt das Ausleihen erst beim Server nach, ob sie noch lebt */
	private static final Duration CHECK_ALIVE_AFTER = Duration.ofSeconds(5);
	/** Keepalive an den Server, damit Firewalls und der Server untätige Verbindungen nicht wegräumen */
	private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(60);
	private static final Duration HOUSEKEEPING_INTERVAL = Duration.ofSeconds(30);

	private final SftpProperties properties;
	private final CurrentUsersProvider currentUsersProvider;
	private final JSch jsch;
	private final Semaphore globalPermits;
	private final ConcurrentMap<UUID, Semaphore> permitsByUsersGuid = new ConcurrentHashMap<>();
	/** zuletzt zurückgegebene zuerst: so bleiben die untätigen untätig und fallen dem Aufräumer anheim */
	private final LinkedBlockingDeque<PooledSftpChannel> idleChannels = new LinkedBlockingDeque<>();
	private final ScheduledExecutorService housekeeping;
	private volatile boolean shuttingDown;
	/** damit ein fehlender Server nicht alle 30 Sekunden eine Warnung erzeugt */
	private volatile boolean unreachableLogged;
	/** die erste gelungene Verbindung steht einmal im Log, danach nur noch nach einem Ausfall */
	private volatile boolean everConnected;

	public SftpConnectionPool(SftpProperties properties, CurrentUsersProvider currentUsersProvider) {
		requireText(properties.host(), "app1.sftp.host");
		requireText(properties.user(), "app1.sftp.user");
		requireText(properties.hostKey(), "app1.sftp.host-key");
		requireText(properties.rootDir(), "app1.sftp.root-dir");
		if (!properties.usesPrivateKey()) {
			requireText(properties.pw(), "app1.sftp.pw");
		}
		if (properties.maxConnections() < 1 || properties.maxConnectionsPerUser() < 1) {
			throw new IllegalStateException("app1.sftp.max-connections und max-connections-per-user müssen mindestens 1 sein");
		}
		this.properties = properties;
		this.currentUsersProvider = currentUsersProvider;
		this.jsch = createJsch();
		this.globalPermits = new Semaphore(properties.maxConnections(), true);
		this.housekeeping = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "sftp-housekeeping");
			// darf das Beenden der Anwendung nicht aufhalten, wenn er gerade auf einen langsamen Server wartet
			thread.setDaemon(true);
			return thread;
		});
	}

	@PostConstruct
	void start() {
		log.info("SFTP-Pool: {}:{} als {} ({}), Wurzel {}, höchstens {} Verbindungen, {} je User", properties.host(),
				properties.port(), properties.user(), properties.usesPrivateKey() ? "Schlüssel" : "Passwort",
				properties.rootDir(), properties.maxConnections(), properties.maxConnectionsPerUser());
		housekeeping.scheduleWithFixedDelay(this::housekeep, 0, HOUSEKEEPING_INTERVAL.toSeconds(), TimeUnit.SECONDS);
	}

	/**
	 * Schließt die freien Verbindungen. Die ausgeliehenen schließen sich bei ihrer Rückgabe selbst: Sie mitten in
	 * einer Übertragung wegzureißen, zerstörte die Datei.
	 */
	@PreDestroy
	void shutdown() {
		shuttingDown = true;
		housekeeping.shutdownNow();
		PooledSftpChannel idle;
		while ((idle = idleChannels.poll()) != null) {
			idle.closeQuietly();
		}
	}

	/**
	 * Holt eine Verbindung. Ist ein User angemeldet, braucht er erst seinen eigenen Platz, dann einen globalen. In
	 * dieser Reihenfolge, sonst belegte jemand, der seine eigene Grenze erreicht hat, einen globalen Platz und
	 * blockierte andere. Läuft die Wartezeit ab: 429 am eigenen Platz, 503 am globalen.
	 */
	public PooledSftpChannel borrowSftpChannel() {
		if (shuttingDown) {
			throw new SftpUnavailableException("der Dateispeicher wird gerade heruntergefahren");
		}
		UUID usersGuid = currentUsersProvider.isUsersLoggedIn() ? currentUsersProvider.getCurrentUsersGuid() : null;
		Semaphore usersPermits = null;
		if (usersGuid != null) {
			usersPermits = permitsByUsersGuid.computeIfAbsent(usersGuid,
					guid -> new Semaphore(properties.maxConnectionsPerUser(), true));
			if (!tryAcquire(usersPermits)) {
				throw new SftpPerUserLimitException(properties.maxConnectionsPerUser());
			}
		}
		if (!tryAcquire(globalPermits)) {
			release(usersPermits);
			log.warn("SFTP-Pool: alle {} Verbindungen belegt, Wartezeit abgelaufen", properties.maxConnections());
			throw new SftpUnavailableException("der Dateispeicher ist gerade ausgelastet");
		}
		try {
			return idleOrNew().lendTo(usersGuid);
		} catch (RuntimeException e) {
			// die eben reservierten Plätze wieder frei, sonst wären sie belegt, ohne dass jemand eine Verbindung hat
			globalPermits.release();
			release(usersPermits);
			throw e;
		}
	}

	/**
	 * Die Rückgabe, gerufen von PooledSftpChannel.close(). Brauchbare Verbindungen kommen zurück in den Bestand,
	 * kaputte werden geschlossen. Die Plätze werden im finally frei: Ginge beim Schließen etwas schief, blieben sie
	 * sonst für immer belegt.
	 */
	void returnSftpChannel(PooledSftpChannel channel) {
		try {
			if (!channel.isBroken() && !shuttingDown && channel.isLocallyConnected()) {
				channel.touch();
				idleChannels.offerFirst(channel);
			} else {
				channel.closeQuietly();
			}
		} finally {
			globalPermits.release();
			UUID usersGuid = channel.usersGuid();
			if (usersGuid != null) {
				release(permitsByUsersGuid.get(usersGuid));
			}
		}
	}

	/** Erst der Bestand, geprüft nur nach längerer Pause, sonst eine neue Verbindung */
	private PooledSftpChannel idleOrNew() {
		PooledSftpChannel idle;
		while ((idle = idleChannels.pollFirst()) != null) {
			boolean usable = idle.idleTime().compareTo(CHECK_ALIVE_AFTER) < 0 ? idle.isLocallyConnected() : idle.isAlive();
			if (usable) {
				return idle;
			}
			idle.closeQuietly();
		}
		return openChannel();
	}

	/**
	 * Hält den Bestand in Ordnung: schließt, was länger als idle-timeout unbenutzt liegt (min-idle-connections
	 * bleiben), und füllt auf min-idle-connections auf, solange die globale Grenze es zulässt.
	 */
	private void housekeep() {
		if (shuttingDown) {
			return;
		}
		int minIdle = Math.min(properties.minIdleConnections(), properties.maxConnections());
		for (Iterator<PooledSftpChannel> idle = idleChannels.descendingIterator(); idle.hasNext();) {
			PooledSftpChannel channel = idle.next();
			if (idleChannels.size() > minIdle && channel.idleTime().compareTo(properties.idleTimeout()) > 0) {
				idle.remove();
				channel.closeQuietly();
			}
		}
		while (idleChannels.size() < minIdle && lentCount() + idleChannels.size() < properties.maxConnections()) {
			try {
				idleChannels.offerFirst(openChannel());
			} catch (SftpUnavailableException e) {
				if (!unreachableLogged) {
					log.warn("SFTP-Vorwärmen fehlgeschlagen, nächster Versuch in {} s: {}",
							HOUSEKEEPING_INTERVAL.toSeconds(), e.getCause() != null ? e.getCause().getMessage() : e.getReason());
					unreachableLogged = true;
				}
				return;
			}
		}
	}

	/** Wie viele gerade ausgeliehen sind, abgeleitet aus den vergebenen Plätzen: kein zweiter Zähler daneben */
	private int lentCount() {
		return properties.maxConnections() - globalPermits.availablePermits();
	}

	private PooledSftpChannel openChannel() {
		try {
			Session session = jsch.getSession(properties.user(), properties.host(), properties.port());
			if (!properties.usesPrivateKey()) {
				session.setPassword(properties.pw().getBytes(StandardCharsets.UTF_8));
			}
			session.setConfig("StrictHostKeyChecking", "yes");
			session.setServerAliveInterval((int) KEEPALIVE_INTERVAL.toMillis());
			session.setServerAliveCountMax(3);
			session.setTimeout((int) properties.socketTimeout().toMillis());
			session.connect((int) properties.connectTimeout().toMillis());
			ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
			channel.connect((int) properties.connectTimeout().toMillis());
			if (unreachableLogged || !everConnected) {
				log.info("SFTP-Server {}:{} erreichbar, Host-Key geprüft, Anmeldung als {} ok", properties.host(),
						properties.port(), properties.user());
				unreachableLogged = false;
				everConnected = true;
			}
			return new PooledSftpChannel(session, channel, this);
		} catch (JSchException e) {
			log.debug("SFTP-Verbindung zu {}:{} fehlgeschlagen: {}", properties.host(), properties.port(), e.getMessage());
			throw new SftpUnavailableException("der Dateispeicher ist gerade nicht erreichbar", e);
		}
	}

	/** Einmal für alle Verbindungen: der erlaubte Host-Key und, falls gesetzt, der eigene Schlüssel */
	private JSch createJsch() {
		try {
			JSch newJsch = new JSch();
			newJsch.setKnownHosts(new ByteArrayInputStream(knownHostsLine().getBytes(StandardCharsets.UTF_8)));
			if (properties.usesPrivateKey()) {
				newJsch.addIdentity(properties.privateKeyPath());
			}
			return newJsch;
		} catch (JSchException e) {
			throw new IllegalStateException("app1.sftp: Host-Key oder privater Schlüssel sind unbrauchbar: " + e.getMessage(), e);
		}
	}

	/** Eine known_hosts-Zeile: der Host so, wie JSch ihn beim Verbinden nennt, bei anderem Port als 22 mit Klammern */
	private String knownHostsLine() {
		String host = properties.port() == 22 ? properties.host() : "[" + properties.host() + "]:" + properties.port();
		return host + " " + properties.hostKey().trim() + "\n";
	}

	/** Wartet auf einen Platz. false, wenn die Zeit ablief. Eine Unterbrechung wird weitergereicht. */
	private boolean tryAcquire(Semaphore permits) {
		try {
			return permits.tryAcquire(properties.borrowTimeout().toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SftpUnavailableException("das Warten auf den Dateispeicher wurde unterbrochen", e);
		}
	}

	private static void release(Semaphore permits) {
		if (permits != null) {
			permits.release();
		}
	}

	private static void requireText(String value, String property) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(property + " fehlt");
		}
	}

}

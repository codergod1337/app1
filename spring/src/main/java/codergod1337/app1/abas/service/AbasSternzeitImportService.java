package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasSternzeit;
import codergod1337.app1.abas.model.AbasSternzeitImportCheck;
import codergod1337.app1.abas.model.AbasSternzeitImportGroup;
import codergod1337.app1.abas.model.AbasSternzeitImportResult;
import codergod1337.app1.abas.model.AbasSternzeitQueueSource;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

/**
 * Der Import von Sternzeiten aus einer Datei (User 2026-10-13), in zwei Schritten: Prüfen liest die Datei und zählt je
 * Objekttyp, ohne etwas zu speichern; Importieren liest sie noch einmal und reiht nur die gewählten Gruppen zum Holen
 * ein. Gebaut für große Dateien: Die Datei wird als Strom gelesen, nie ganz in den Speicher, eingereiht wird in Paketen
 * zu {@value #BATCH_SIZE}, und alle zwei Sekunden geht ein Zwischenstand an den Aufrufer.
 *
 * Welche Dateien gehen, entscheidet das erste Zeichen:
 * <ul>
 * <li>{ oder [: JSON, eine Liste, ein Objekt oder ein Objekt je Zeile. Jedes Objekt mit dem Key sternzeit zählt, dazu
 * sein objectType, wenn er dabeisteht; so liest es das Inventar der Vorlage und den eigenen Export. Jeder andere Text,
 * der genau eine Sternzeit ist, zählt ohne Typ, so geht auch eine Antwort von ABAS.</li>
 * <li>alles andere: Text, jede Folge (Zahl,Zahl,Zahl) zählt ohne Typ, so geht das Queue-Futter der Vorlage, eine
 * Sternzeit je Zeile, oder ein Log.</li>
 * </ul>
 * Ohne Typ bleibt nur die Datenbank, die mittlere Zahl: Am Typ lässt sich die Gruppe nicht ablesen.
 */
@Service
public class AbasSternzeitImportService {

	private static final Logger log = LoggerFactory.getLogger(AbasSternzeitImportService.class);

	/** So viele Kennungen gehen je Paket in die Queue, eine Anweisung je Paket */
	static final int BATCH_SIZE = 1000;

	/** So oft meldet ein Lauf höchstens einen Zwischenstand */
	private static final long PROGRESS_INTERVAL_MS = 2000;

	/** Das Spaltenmaß des Objekttyps in abas_object_type; länger kann kein echter Typ sein */
	private static final int MAX_OBJECT_TYPE_LENGTH = 50;

	/** So weit schaut das Lesen höchstens nach dem ersten Zeichen, das kein Leerraum ist */
	private static final int FORMAT_PEEK_BYTES = 4096;

	/** Eine Sternzeit irgendwo in einer Textzeile, die mittlere Gruppe ist die Datenbank */
	private static final Pattern STERNZEIT_IN_TEXT = Pattern.compile("\\((\\d+),(\\d+),(\\d+)\\)");

	private static final String STERNZEIT_KEY = "sternzeit";
	private static final String OBJECT_TYPE_KEY = "objectType";

	private final AbasSternzeitQueueService abasSternzeitQueueService;
	private final AbasIgnoredSternzeitService abasIgnoredSternzeitService;
	private final ObjectMapper objectMapper;

	public AbasSternzeitImportService(AbasSternzeitQueueService abasSternzeitQueueService,
			AbasIgnoredSternzeitService abasIgnoredSternzeitService, ObjectMapper objectMapper) {
		this.abasSternzeitQueueService = abasSternzeitQueueService;
		this.abasIgnoredSternzeitService = abasIgnoredSternzeitService;
		this.objectMapper = objectMapper;
	}

	// ===== Schritt 1: Prüfen =====

	/**
	 * Was in der Datei steckt: je Objekttyp eine Gruppe, ohne Typ je Datenbank eine, die größten zuerst. Speichert
	 * nichts. progress bekommt alle zwei Sekunden den Stand bis hierher.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitImportCheck checkAbasSternzeitImportFile(InputStream file,
			Consumer<AbasSternzeitImportCheck> progress) throws IOException {
		Map<String, GroupCount> groupsByKey = new LinkedHashMap<>();
		long[] sternzeitAndInvalidCount = new long[2];
		long[] lastProgressAt = { System.currentTimeMillis() };
		readSternzeiten(file, (sternzeit, objectType) -> {
			sternzeitAndInvalidCount[0]++;
			Integer abasDatabaseNumber = objectType == null ? abasDatabaseNumberOf(sternzeit) : null;
			String groupKey = objectType != null ? "T" + objectType : "D" + abasDatabaseNumber;
			groupsByKey.computeIfAbsent(groupKey, key -> new GroupCount(objectType, abasDatabaseNumber, sternzeit)).count++;
			long now = System.currentTimeMillis();
			if (now - lastProgressAt[0] >= PROGRESS_INTERVAL_MS) {
				lastProgressAt[0] = now;
				progress.accept(checkOf(sternzeitAndInvalidCount, groupsByKey));
			}
		}, () -> sternzeitAndInvalidCount[1]++);
		AbasSternzeitImportCheck check = checkOf(sternzeitAndInvalidCount, groupsByKey);
		log.info("Sternzeit-Import geprüft: {} Sternzeiten, {} ungültig, {} Gruppen", check.sternzeitCount(),
				check.invalidCount(), check.groups().size());
		return check;
	}

	// ===== Schritt 2: Importieren =====

	/**
	 * Reiht die gewählten Sternzeiten zum Holen ein: mit Objekttyp, wenn er in objectTypes steht, ohne Typ, wenn ihre
	 * Datenbank in abasDatabaseNumbers steht. Gesperrte bleiben draußen, Doppelte und schon wartende zählen als
	 * übersprungen. Gefetcht wird hier nichts, die Queue füllt sich, der Pool holt, wenn er läuft. 400, wenn nichts
	 * gewählt ist.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitImportResult importAbasSternzeitenFromFile(InputStream file, Set<String> objectTypes,
			Set<Integer> abasDatabaseNumbers, Consumer<AbasSternzeitImportResult> progress) throws IOException {
		if (objectTypes.isEmpty() && abasDatabaseNumbers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Objekttyp und keine Datenbank gewählt");
		}
		// found, filtered, queued, skipped, ignored
		long[] counts = new long[5];
		List<String> batch = new ArrayList<>(BATCH_SIZE);
		long[] lastProgressAt = { System.currentTimeMillis() };
		readSternzeiten(file, (sternzeit, objectType) -> {
			counts[0]++;
			boolean chosen = objectType != null ? objectTypes.contains(objectType)
					: abasDatabaseNumbers.contains(abasDatabaseNumberOf(sternzeit));
			if (!chosen) {
				counts[1]++;
				return;
			}
			batch.add(sternzeit);
			if (batch.size() >= BATCH_SIZE) {
				enqueueBatch(batch, counts);
				long now = System.currentTimeMillis();
				if (now - lastProgressAt[0] >= PROGRESS_INTERVAL_MS) {
					lastProgressAt[0] = now;
					progress.accept(resultOf(counts));
				}
			}
		}, () -> {
			// ein ungültiger Wert unter dem Key sternzeit ist keine Sternzeit, er zählt nirgends mit
		});
		enqueueBatch(batch, counts);
		AbasSternzeitImportResult result = resultOf(counts);
		log.info("Sternzeit-Import: {} gelesen, {} nicht gewählt, {} eingereiht, {} übersprungen, {} gesperrt "
				+ "(Typen {}, Datenbanken {})", result.found(), result.filtered(), result.queued(), result.skipped(),
				result.ignored(), objectTypes, abasDatabaseNumbers);
		return result;
	}

	/** Ein Paket in die Queue: Gesperrte zählen und aussortieren, der Rest geht in einer Anweisung hinein */
	private void enqueueBatch(List<String> batch, long[] counts) {
		if (batch.isEmpty()) {
			return;
		}
		Set<String> ignored = abasIgnoredSternzeitService.getIgnoredSternzeitenAmong(batch);
		List<String> allowed = new ArrayList<>(batch.size());
		for (String sternzeit : batch) {
			if (ignored.contains(sternzeit)) {
				counts[4]++;
			} else {
				allowed.add(sternzeit);
			}
		}
		int queued = abasSternzeitQueueService.enqueueAbasSternzeiten(allowed, AbasSternzeitQueueSource.IMPORT);
		counts[2] += queued;
		counts[3] += allowed.size() - queued;
		batch.clear();
	}

	// ===== Lesen =====

	/**
	 * Liest die Datei als Strom und meldet jede Sternzeit mit ihrem Objekttyp, null ohne Typ. onInvalid hört von jedem
	 * Wert unter dem Key sternzeit, der keine Sternzeit ist. Ob JSON oder Text, entscheidet das erste Zeichen, das kein
	 * Leerraum und keine Byte-Order-Mark ist.
	 */
	private void readSternzeiten(InputStream file, BiConsumer<String, String> onSternzeit, Runnable onInvalid)
			throws IOException {
		BufferedInputStream input = new BufferedInputStream(file, 1 << 16);
		int first = firstContentByte(input);
		if (first == '{' || first == '[') {
			readJsonSternzeiten(input, onSternzeit, onInvalid);
		} else {
			readTextSternzeiten(input, onSternzeit);
		}
	}

	/** Das erste Zeichen nach Leerraum und Byte-Order-Mark, ohne es zu verbrauchen; -1 bei leerer Datei */
	private static int firstContentByte(BufferedInputStream input) throws IOException {
		input.mark(FORMAT_PEEK_BYTES);
		try {
			for (int read = 0; read < FORMAT_PEEK_BYTES; read++) {
				int next = input.read();
				if (next < 0) {
					return -1;
				}
				// Leerraum und die UTF-8-Byte-Order-Mark EF BB BF überspringen
				if (next == ' ' || next == '\t' || next == '\r' || next == '\n' || next == 0xEF || next == 0xBB
						|| next == 0xBF) {
					continue;
				}
				return next;
			}
			return -1;
		} finally {
			input.reset();
		}
	}

	/**
	 * JSON als Folge von Tokens, nie als Baum im Speicher: Je Objekt merkt sich ein Rahmen Sternzeit und Typ, am Ende des
	 * Objekts wird gemeldet. Mehrere Werte hintereinander, ein Objekt je Zeile, liest der Parser von selbst.
	 */
	private void readJsonSternzeiten(InputStream input, BiConsumer<String, String> onSternzeit, Runnable onInvalid) {
		Deque<JsonContainer> containers = new ArrayDeque<>();
		try (JsonParser parser = objectMapper.createParser(input)) {
			JsonToken token;
			while ((token = parser.nextToken()) != null) {
				switch (token) {
				case START_OBJECT -> containers.push(new JsonContainer(true));
				case START_ARRAY -> containers.push(new JsonContainer(false));
				case END_ARRAY -> containers.poll();
				case END_OBJECT -> {
					JsonContainer object = containers.poll();
					if (object != null && object.sternzeit != null) {
						onSternzeit.accept(object.sternzeit, object.objectType);
					}
				}
				case VALUE_STRING -> {
					JsonContainer container = containers.peek();
					String name = container != null && container.object ? parser.currentName() : null;
					String value = parser.getString().trim();
					if (container != null && STERNZEIT_KEY.equals(name)) {
						if (AbasSternzeit.isValidSternzeit(value)) {
							container.sternzeit = value;
						} else {
							onInvalid.run();
						}
					} else if (container != null && OBJECT_TYPE_KEY.equals(name)) {
						if (!value.isEmpty() && value.length() <= MAX_OBJECT_TYPE_LENGTH) {
							container.objectType = value;
						}
					} else if (AbasSternzeit.isValidSternzeit(value)) {
						// eine Sternzeit ohne Key sternzeit, z. B. ein Verweis in einer Antwort von ABAS: ohne Typ
						onSternzeit.accept(value, null);
					}
				}
				default -> {
					// Zahlen, Wahrheitswerte, null und Namen tragen keine Sternzeit
				}
				}
			}
		}
	}

	/** Text Zeile für Zeile, jede Folge (Zahl,Zahl,Zahl) zählt ohne Typ */
	private static void readTextSternzeiten(InputStream input, BiConsumer<String, String> onSternzeit)
			throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 1 << 16);
		String line;
		while ((line = reader.readLine()) != null) {
			Matcher matcher = STERNZEIT_IN_TEXT.matcher(line);
			while (matcher.find()) {
				String sternzeit = matcher.group();
				if (AbasSternzeit.isValidSternzeit(sternzeit)) {
					onSternzeit.accept(sternzeit, null);
				}
			}
		}
	}

	/** Die ABAS-Datenbank einer Sternzeit, die mittlere Zahl: (87448,19,0) ist 19; null, wenn sie nicht in ein int passt */
	private static Integer abasDatabaseNumberOf(String sternzeit) {
		int firstComma = sternzeit.indexOf(',');
		int secondComma = sternzeit.indexOf(',', firstComma + 1);
		try {
			return Integer.valueOf(sternzeit.substring(firstComma + 1, secondComma));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static AbasSternzeitImportCheck checkOf(long[] sternzeitAndInvalidCount, Map<String, GroupCount> groupsByKey) {
		List<AbasSternzeitImportGroup> groups = new ArrayList<>(groupsByKey.size());
		for (GroupCount group : groupsByKey.values()) {
			groups.add(new AbasSternzeitImportGroup(group.objectType, group.abasDatabaseNumber, group.count,
					group.exampleSternzeit));
		}
		groups.sort(Comparator.comparingLong(AbasSternzeitImportGroup::sternzeitCount).reversed());
		return new AbasSternzeitImportCheck(sternzeitAndInvalidCount[0], sternzeitAndInvalidCount[1], groups);
	}

	private static AbasSternzeitImportResult resultOf(long[] counts) {
		return new AbasSternzeitImportResult(counts[0], counts[1], counts[2], counts[3], counts[4]);
	}

	/** Der Zähler einer Gruppe beim Prüfen */
	private static final class GroupCount {

		private final String objectType;
		private final Integer abasDatabaseNumber;
		private final String exampleSternzeit;
		private long count;

		private GroupCount(String objectType, Integer abasDatabaseNumber, String exampleSternzeit) {
			this.objectType = objectType;
			this.abasDatabaseNumber = abasDatabaseNumber;
			this.exampleSternzeit = exampleSternzeit;
		}
	}

	/** Ein offenes Objekt oder eine offene Liste beim Lesen von JSON; nur ein Objekt sammelt Sternzeit und Typ */
	private static final class JsonContainer {

		private final boolean object;
		private String sternzeit;
		private String objectType;

		private JsonContainer(boolean object) {
			this.object = object;
		}
	}

}

package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasSternzeit;
import codergod1337.app1.abas.model.AbasSternzeitForClient;
import codergod1337.app1.abas.model.AbasSternzeitOverviewCounts;
import codergod1337.app1.abas.model.AbasSternzeitPage;
import codergod1337.app1.abas.model.AbasSternzeitQueueCounts;
import codergod1337.app1.abas.repository.AbasSternzeitRepository;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Stammsätze: für die Übersicht des AbasManagers Kopfzahlen, die Liste per Keyset und der Treffer per Kennung; für
 * die Worker das Fortschreiben nach einem Fetch und das Entfernen nach einem DELETE. Alles über den Primärschlüssel,
 * den Index auf sternzeit oder Teilindizes, nichts, das bei 10^9 Zeilen die Tabelle liest.
 */
@Service
public class AbasSternzeitService {

	private static final Logger log = LoggerFactory.getLogger(AbasSternzeitService.class);

	/** So viele Zeilen zeigt eine Seite der Übersicht */
	static final int PAGE_SIZE = 100;

	/** So viele Stammsätze liest der Export je Abfrage */
	private static final int EXPORT_PAGE_SIZE = 10_000;

	private final AbasSternzeitRepository abasSternzeitRepository;
	private final AbasObjectTypeService abasObjectTypeService;
	private final AbasSternzeitQueueService abasSternzeitQueueService;
	private final AbasIgnoredSternzeitService abasIgnoredSternzeitService;

	/** Schreibt die Zeilen des Exports */
	private final ObjectMapper objectMapper;

	public AbasSternzeitService(AbasSternzeitRepository abasSternzeitRepository,
			AbasObjectTypeService abasObjectTypeService, AbasSternzeitQueueService abasSternzeitQueueService,
			AbasIgnoredSternzeitService abasIgnoredSternzeitService, ObjectMapper objectMapper) {
		this.abasSternzeitRepository = abasSternzeitRepository;
		this.abasObjectTypeService = abasObjectTypeService;
		this.abasSternzeitQueueService = abasSternzeitQueueService;
		this.abasIgnoredSternzeitService = abasIgnoredSternzeitService;
		this.objectMapper = objectMapper;
	}

	/** Die Kopfzahlen: Stammsätze, davon ohne Typ, die Queue in drei Zahlen, die Sperrliste */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitOverviewCounts getAbasSternzeitOverviewCounts() {
		AbasRowCountEstimation.EstimatedOrExactRowCount sternzeitCount = AbasRowCountEstimation
				.rowCount(abasSternzeitRepository.estimateRowCount(), abasSternzeitRepository::count);
		AbasSternzeitQueueCounts queueCounts = abasSternzeitQueueService.getAbasSternzeitQueueCounts();
		return new AbasSternzeitOverviewCounts(sternzeitCount.count(), sternzeitCount.estimated(),
				abasSternzeitRepository.countByObjectTypeIdIsNull(), queueCounts.queueCount(),
				queueCounts.queueCountEstimated(), queueCounts.claimedCount(), queueCounts.failedCount(),
				abasIgnoredSternzeitService.countIgnoredSternzeiten());
	}

	/**
	 * Eine Seite der Liste, aufsteigend nach Kennung, hinter dem Anker. null oder leer heißt von vorn. Geholt wird
	 * eine Zeile mehr als gezeigt, so steht fest, ob es weitergeht, ohne zu zählen.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitPage getAbasSternzeitPage(String afterSternzeit) {
		String anchor = afterSternzeit == null ? "" : afterSternzeit;
		List<AbasSternzeit> rows = abasSternzeitRepository.findBySternzeitGreaterThanOrderBySternzeitAsc(anchor,
				PageRequest.of(0, PAGE_SIZE + 1));
		boolean hasMore = rows.size() > PAGE_SIZE;
		List<AbasSternzeitForClient> page = new ArrayList<>(PAGE_SIZE);
		for (AbasSternzeit row : rows.subList(0, Math.min(rows.size(), PAGE_SIZE))) {
			page.add(toClient(row));
		}
		String lastSternzeit = page.isEmpty() ? null : page.get(page.size() - 1).sternzeit();
		return new AbasSternzeitPage(page, lastSternzeit, hasMore);
	}

	/** Der Treffer per Kennung. 400 bei ungültiger Klammerform, 404 ohne Stammsatz. */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitForClient findAbasSternzeit(String sternzeit) {
		if (!AbasSternzeit.isValidSternzeit(sternzeit)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sternzeit fehlt oder hat nicht die Klammerform");
		}
		return abasSternzeitRepository.findBySternzeit(sternzeit).map(this::toClient).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Stammsatz zu " + sternzeit));
	}

	/**
	 * Der Export (User 2026-10-13): jeder Stammsatz eine Zeile {"sternzeit":"(87448,19,0)","objectType":"obj:data:19:0"},
	 * dasselbe Format wie das Inventar der Vorlage, so liest der Import es wieder samt Typ. Ein Stammsatz ohne Typ hat
	 * objectType null. Gestreamt, seitenweise per Keyset über den Index auf sternzeit, jede Seite eine eigene kurze
	 * Abfrage: bei 10^9 Zeilen hält keine Transaktion stundenlang offen, und nichts liegt ganz im Speicher.
	 *
	 * @return wie viele Zeilen geschrieben wurden
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public long exportAbasSternzeiten(OutputStream target) throws IOException {
		Writer writer = new BufferedWriter(new OutputStreamWriter(target, StandardCharsets.UTF_8), 1 << 16);
		String anchor = "";
		long written = 0;
		while (true) {
			List<AbasSternzeit> page = abasSternzeitRepository.findBySternzeitGreaterThanOrderBySternzeitAsc(anchor,
					PageRequest.of(0, EXPORT_PAGE_SIZE));
			if (page.isEmpty()) {
				break;
			}
			for (AbasSternzeit row : page) {
				Map<String, Object> line = new LinkedHashMap<>();
				line.put("sternzeit", row.getSternzeit());
				line.put("objectType", abasObjectTypeService.getObjectTypeTextById(row.getObjectTypeId()));
				writer.write(objectMapper.writeValueAsString(line));
				writer.write('\n');
				written++;
			}
			// der Anker ist die zuletzt geschriebene Kennung, die nächste Seite greift lückenlos dahinter weiter
			anchor = page.get(page.size() - 1).getSternzeit();
			writer.flush();
		}
		writer.flush();
		log.info("Sternzeit-Export: {} Zeilen", written);
		return written;
	}

	// ===== Die Handgriffe der Worker, ohne @PreAuthorize: Worker haben keinen Security-Context =====

	/**
	 * Ein Worker hat eine Kennung erfolgreich geholt: Stammsatz anlegen, falls es ihn noch nicht gibt (firstPushedAt ist
	 * dann jetzt und bleibt so), Typ und lastSyncAt setzen, fetchCount hochzählen.
	 */
	@Transactional
	public void recordAbasSternzeitFetched(String sternzeit, Short objectTypeId) {
		AbasSternzeit abasSternzeit = abasSternzeitRepository.findBySternzeit(sternzeit)
				.orElseGet(() -> new AbasSternzeit(sternzeit));
		abasSternzeit.setObjectTypeId(objectTypeId);
		abasSternzeit.setLastSyncAt(Instant.now());
		abasSternzeit.setFetchCount(abasSternzeit.getFetchCount() + 1);
		abasSternzeitRepository.save(abasSternzeit);
	}

	/** Der DELETE-Weg: Eine in ABAS gelöschte Kennung hat hier nichts mehr verloren. Pusht ABAS sie je wieder, entsteht sie neu. */
	@Transactional
	public void deleteAbasSternzeit(String sternzeit) {
		long deleted = abasSternzeitRepository.deleteBySternzeit(sternzeit);
		log.info("Sternzeit {} aufgelöst, Stammsatz {}", sternzeit, deleted == 0 ? "gab es nicht (Leerlauf)" : "entfernt");
	}

	/** Die Zeile für die Übersicht, der Typ als Text aus dem Speicher */
	private AbasSternzeitForClient toClient(AbasSternzeit abasSternzeit) {
		return new AbasSternzeitForClient(abasSternzeit.getId(), abasSternzeit.getSternzeit(),
				abasObjectTypeService.getObjectTypeTextById(abasSternzeit.getObjectTypeId()),
				abasSternzeit.getFirstPushedAt(), abasSternzeit.getLastSyncAt(), abasSternzeit.getFetchCount());
	}

}

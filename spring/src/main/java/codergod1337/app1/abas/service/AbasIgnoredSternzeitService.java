package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasIgnoredSternzeit;
import codergod1337.app1.abas.model.AbasSternzeit;
import codergod1337.app1.abas.repository.AbasIgnoredSternzeitRepository;
import codergod1337.app1.abas.repository.AbasSternzeitQueueEntryRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Sperrliste (User 2026-10-10): Eine Kennung sperren, der Prüfgriff vor jedem Einreihen und die Zahl für die
 * Übersicht. Die Liste anzeigen und Sperren aufheben kommt mit dem Tab der Sperrliste.
 */
@Service
public class AbasIgnoredSternzeitService {

	private static final Logger log = LoggerFactory.getLogger(AbasIgnoredSternzeitService.class);

	private final AbasIgnoredSternzeitRepository abasIgnoredSternzeitRepository;

	/**
	 * ZIRKELSCHLUSS: Der AbasSternzeitQueueService fragt hier vor jedem Einreihen, ob die Kennung gesperrt ist. Deshalb
	 * darf dieser Service nicht auf ihn zeigen und nutzt zum Entfernen eines wartenden Eintrags ausnahmsweise direkt
	 * das Repository der Queue, nur löschend.
	 */
	private final AbasSternzeitQueueEntryRepository abasSternzeitQueueEntryRepository;

	public AbasIgnoredSternzeitService(AbasIgnoredSternzeitRepository abasIgnoredSternzeitRepository,
			AbasSternzeitQueueEntryRepository abasSternzeitQueueEntryRepository) {
		this.abasIgnoredSternzeitRepository = abasIgnoredSternzeitRepository;
		this.abasSternzeitQueueEntryRepository = abasSternzeitQueueEntryRepository;
	}

	/**
	 * Eine Kennung sperren: ab jetzt weist jedes Einreihen sie mit 403 ab, ein wartender Eintrag in der Queue wird
	 * entfernt, damit der Pool sie nicht doch noch holt. 400 bei ungültiger Klammerform, 409 wenn sie schon gesperrt
	 * ist. Der Stammsatz bleibt, er ist Wissen, keine Arbeit.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AbasIgnoredSternzeit ignoreAbasSternzeit(String sternzeit, String reason) {
		if (!AbasSternzeit.isValidSternzeit(sternzeit)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sternzeit fehlt oder hat nicht die Klammerform");
		}
		if (abasIgnoredSternzeitRepository.existsBySternzeit(sternzeit)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Sternzeit " + sternzeit + " ist schon gesperrt");
		}
		AbasIgnoredSternzeit ignored = abasIgnoredSternzeitRepository
				.save(new AbasIgnoredSternzeit(sternzeit, reason == null || reason.isBlank() ? null : reason.trim()));
		if (abasSternzeitQueueEntryRepository.existsById(sternzeit)) {
			abasSternzeitQueueEntryRepository.deleteById(sternzeit);
			log.info("Sternzeit {} gesperrt und aus der Queue entfernt", sternzeit);
		} else {
			log.info("Sternzeit {} gesperrt", sternzeit);
		}
		return ignored;
	}

	/** Steht die Kennung auf der Sperrliste? Trifft den eindeutigen Index, kostet bei jeder Größe gleich viel. */
	public boolean isSternzeitIgnored(String sternzeit) {
		return abasIgnoredSternzeitRepository.existsBySternzeit(sternzeit);
	}

	/**
	 * Welche dieser Kennungen gesperrt sind, für ein Paket des Imports (User 2026-10-13): eine Abfrage für bis zu tausend
	 * Kennungen statt eines Prüfgriffs je Kennung. Leer heißt keine.
	 */
	public Set<String> getIgnoredSternzeitenAmong(Collection<String> sternzeiten) {
		if (sternzeiten.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(abasIgnoredSternzeitRepository.findSternzeitenAmong(sternzeiten));
	}

	/** Wie viele Kennungen gesperrt sind; die Liste ist klein, count(*) ist hier in Ordnung */
	public long countIgnoredSternzeiten() {
		return abasIgnoredSternzeitRepository.count();
	}

}

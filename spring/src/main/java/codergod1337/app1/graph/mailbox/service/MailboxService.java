package codergod1337.app1.graph.mailbox.service;

import codergod1337.app1.graph.mail.model.GraphMailProperties;
import codergod1337.app1.graph.mailbox.model.Mailbox;
import codergod1337.app1.graph.mailbox.model.MailboxesOfCurrentUser;
import codergod1337.app1.graph.mailbox.repository.MailboxRepository;
import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Postfächer, aus denen archiviert wird (User 2026-10-13, nach dem MailboxService der Vorlage). Pflege und die ganze
 * Liste nur für den Admin. Jeder andere User bekommt nur seine Postfächer, und nur mit der Rolle des Archivierens: die
 * Dienstpostfächer, an denen seine ARC steht, und zusätzlich seine eigene E-Mail, wenn sie auf die Domäne der Postfächer
 * endet (app1.graph.mail.domain aus der .env). Dieselbe Regel prüft requireArchiveAccessToMailbox bei jedem Aufruf des
 * Archivierens. Eine ARC am Postfach muss es geben, ein Tippfehler ist 400 wie in der Vorlage.
 */
@Service
public class MailboxService {

	/** Die Rolle des Archivierens (wie Vorlage). Ohne sie gibt es kein Postfach, auch nicht das eigene. */
	public static final String ARCHIVE_ACCESS_ROLE_KEY = "NACHRICHTEN_ARCHIVIEREN";

	/** Der Admin darf aus jedem Postfach archivieren, auch ohne die Rolle des Archivierens. */
	private static final String ADMIN_ACCESS_ROLE_KEY = "ADMIN";

	/** Sortiert nach listingPosition und bei gleicher Position nach Adresse */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "emailAddress");

	private final MailboxRepository mailboxRepository;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final CurrentUsersProvider currentUsersProvider;
	private final GraphMailProperties graphMailProperties;

	public MailboxService(MailboxRepository mailboxRepository, AccessRoleCollectionService accessRoleCollectionService,
			CurrentUsersProvider currentUsersProvider, GraphMailProperties graphMailProperties) {
		this.mailboxRepository = mailboxRepository;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.currentUsersProvider = currentUsersProvider;
		this.graphMailProperties = graphMailProperties;
	}

	/** Neues Postfach. 400 bei ungültiger Adresse, fehlendem Namen oder unbekannter ARC, 409 wenn es die Adresse schon gibt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public Mailbox createMailbox(String emailAddress, String displayName, String description, int listingPosition,
			List<String> accessRoleCollectionKeys) {
		String checkedEmailAddress = checkedEmailAddress(emailAddress);
		// Pflicht: save mit einer vorhandenen Adresse wäre ein Update und würde das bestehende Postfach überschreiben
		if (mailboxRepository.existsById(checkedEmailAddress)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "dieses Postfach gibt es schon: " + checkedEmailAddress);
		}
		return mailboxRepository.save(new Mailbox(checkedEmailAddress, checkedDisplayName(displayName),
				blankToNull(description), listingPosition, checkedAccessRoleCollectionKeys(accessRoleCollectionKeys)));
	}

	/** Die Adresse bestimmt nur, welches Postfach geändert wird, sie selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public Mailbox updateMailbox(String emailAddress, String displayName, String description, int listingPosition,
			List<String> accessRoleCollectionKeys) {
		Mailbox mailbox = getMailboxByEmailAddress(emailAddress);
		mailbox.setDisplayName(checkedDisplayName(displayName));
		mailbox.setDescription(blankToNull(description));
		mailbox.setListingPosition(listingPosition);
		mailbox.setAccessRoleCollectionKeys(checkedAccessRoleCollectionKeys(accessRoleCollectionKeys));
		return mailboxRepository.save(mailbox);
	}

	/**
	 * Neue Positionen mehrerer Postfächer auf einmal, z. B. nach Drag and Drop. Der Controller hat Adressen und Zahlen
	 * schon geprüft. Eine Transaktion. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<Mailbox> changeMailboxPositions(Map<String, Integer> newPositionByEmailAddress) {
		for (Map.Entry<String, Integer> newPosition : newPositionByEmailAddress.entrySet()) {
			Mailbox mailbox = getMailboxByEmailAddress(newPosition.getKey());
			mailbox.setListingPosition(newPosition.getValue());
			mailboxRepository.save(mailbox);
		}
		return getAllMailboxes();
	}

	/** Löscht das Postfach endgültig. Gibt es zurück, wie es vor dem Löschen war. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public Mailbox deleteMailbox(String emailAddress) {
		Mailbox mailbox = getMailboxByEmailAddress(emailAddress);
		mailboxRepository.delete(mailbox);
		return mailbox;
	}

	/** Gibt es das Postfach nicht, kommt 404. Die Adresse zählt ohne Rücksicht auf Groß- und Kleinschreibung. */
	@Transactional(readOnly = true)
	public Mailbox getMailboxByEmailAddress(String emailAddress) {
		String normalized = emailAddress == null ? "" : emailAddress.trim().toLowerCase(Locale.ROOT);
		return mailboxRepository.findById(normalized)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Postfach mit dieser Adresse"));
	}

	/** Alle Postfächer, sortiert nach listingPosition und bei gleicher Position nach Adresse. Nur für den Admin. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<Mailbox> getAllMailboxes() {
		return mailboxRepository.findAll(LISTING_ORDER);
	}

	/**
	 * Aus welchen Postfächern der angemeldete User archivieren darf: seine Dienstpostfächer und daneben seine eigene
	 * E-Mail, wenn sie auf die Domäne der Postfächer endet. Ohne die Rolle des Archivierens 403.
	 */
	@PreAuthorize("hasAnyRole('" + ARCHIVE_ACCESS_ROLE_KEY + "', 'ADMIN')")
	@Transactional(readOnly = true)
	public MailboxesOfCurrentUser getMailboxesOfCurrentUser() {
		return mailboxesOfCurrentUser();
	}

	/**
	 * Jeder Aufruf des Archivierens ruft das als Erstes, bevor Graph gefragt wird: Darf der angemeldete User aus genau
	 * diesem Postfach archivieren? Dieselbe Regel wie getMailboxesOfCurrentUser, die Auswahl im Frontend schützt nichts.
	 * 403 ohne die Rolle des Archivierens und für jede Adresse, die nicht in seiner Liste steht.
	 */
	@PreAuthorize("hasAnyRole('" + ARCHIVE_ACCESS_ROLE_KEY + "', 'ADMIN')")
	@Transactional(readOnly = true)
	public void requireArchiveAccessToMailbox(String emailAddress) {
		String normalized = emailAddress == null ? "" : emailAddress.trim().toLowerCase(Locale.ROOT);
		MailboxesOfCurrentUser mailboxesOfCurrentUser = mailboxesOfCurrentUser();
		boolean ownEmailAddress = normalized.equals(mailboxesOfCurrentUser.ownEmailAddress());
		boolean assignedMailbox = mailboxesOfCurrentUser.assignedMailboxes().stream()
				.anyMatch(mailbox -> mailbox.getEmailAddress().equals(normalized));
		if (!ownEmailAddress && !assignedMailbox) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Zugriff auf das Postfach " + normalized);
		}
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isMailboxEmailAddressAvailable(String emailAddress) {
		return emailAddress == null || !mailboxRepository.existsById(emailAddress.trim().toLowerCase(Locale.ROOT));
	}

	/** Eine Adresse, getrimmt und klein, mit genau einem @ in der Mitte und ohne Leerraum; 400 sonst. Auch für den Import. */
	public static boolean isValidMailboxEmailAddress(String emailAddress) {
		if (emailAddress == null) {
			return false;
		}
		int at = emailAddress.indexOf('@');
		return at > 0 && at == emailAddress.lastIndexOf('@') && at < emailAddress.length() - 1
				&& emailAddress.chars().noneMatch(Character::isWhitespace);
	}

	private static String checkedEmailAddress(String emailAddress) {
		String normalized = emailAddress == null ? "" : emailAddress.trim().toLowerCase(Locale.ROOT);
		if (!isValidMailboxEmailAddress(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine E-Mail-Adresse: " + emailAddress);
		}
		return normalized;
	}

	private static String checkedDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName fehlt");
		}
		return displayName.trim();
	}

	/** Jede ARC muss es geben, ohne Doppelte; ein Tippfehler fällt dem auf, der ihn gerade macht (Vorlage) */
	private List<String> checkedAccessRoleCollectionKeys(List<String> accessRoleCollectionKeys) {
		if (accessRoleCollectionKeys == null) {
			return List.of();
		}
		Set<String> checked = new LinkedHashSet<>();
		for (String accessRoleCollectionKey : accessRoleCollectionKeys) {
			if (accessRoleCollectionService.findAccessRoleCollectionByKey(accessRoleCollectionKey).isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"es gibt keine Position mit diesem key: " + accessRoleCollectionKey);
			}
			checked.add(accessRoleCollectionKey);
		}
		return new ArrayList<>(checked);
	}

	/** Die eine Regel hinter getMailboxesOfCurrentUser und requireArchiveAccessToMailbox, damit beide nie auseinanderlaufen */
	private MailboxesOfCurrentUser mailboxesOfCurrentUser() {
		List<Mailbox> allMailboxes = mailboxRepository.findAll(LISTING_ORDER);
		if (currentUsersProvider.getCurrentUsersAccessRoleKeys().contains(ADMIN_ACCESS_ROLE_KEY)) {
			return new MailboxesOfCurrentUser(allMailboxes, ownEmailAddressOfCurrentUser());
		}
		Set<String> accessRoleCollectionKeys = accessRoleCollectionKeysOfCurrentUser();
		List<Mailbox> assignedMailboxes = allMailboxes.stream()
				.filter(mailbox -> mailbox.getAccessRoleCollectionKeys().stream().anyMatch(accessRoleCollectionKeys::contains))
				.toList();
		return new MailboxesOfCurrentUser(assignedMailboxes, ownEmailAddressOfCurrentUser());
	}

	/** Seine ARC aus dem Token und ihre Slave-ARCs, nur die direkt eingetragenen, ohne Kette wie an der ARC selbst */
	private Set<String> accessRoleCollectionKeysOfCurrentUser() {
		String accessRoleCollectionKey = currentUsersProvider.getCurrentUsersAccessRoleCollectionKey();
		if (accessRoleCollectionKey == null) {
			return Set.of();
		}
		Set<String> accessRoleCollectionKeys = new HashSet<>();
		accessRoleCollectionKeys.add(accessRoleCollectionKey);
		accessRoleCollectionService.findAccessRoleCollectionByKey(accessRoleCollectionKey)
				.map(AccessRoleCollection::getSlaveArcKeys)
				.ifPresent(slaveArcKeys -> accessRoleCollectionKeys.addAll(slaveArcKeys));
		return accessRoleCollectionKeys;
	}

	/** Seine E-Mail frisch aus der DB, klein geschrieben, wenn sie auf @ plus die Domäne endet; sonst null */
	private String ownEmailAddressOfCurrentUser() {
		String domain = graphMailProperties.domain();
		if (domain == null || domain.isBlank()) {
			return null;
		}
		String email = currentUsersProvider.getCurrentUsers().getEmail();
		if (email == null) {
			return null;
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		// ein @ vor der Domäne in der .env stört nicht
		String domainSuffix = "@" + domain.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
		return normalized.endsWith(domainSuffix) && isValidMailboxEmailAddress(normalized) ? normalized : null;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text;
	}

}

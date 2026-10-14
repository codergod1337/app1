package codergod1337.app1.message.messagesubclass.service;

import codergod1337.app1.file.filesubclass.service.FileSubClassDestinationService;
import codergod1337.app1.message.messagesubclass.model.MessageSubClass;
import codergod1337.app1.message.messagesubclass.repository.MessageSubClassRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.model.AccessRoleSymbol;
import codergod1337.app1.system.access.service.AccessRoleService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Nachrichtenarten (MSC, User 2026-10-13), nach dem Muster des FileSubClassService. Wer was darf, steht als
 * @PreAuthorize über der Methode, die Rollen kommen aus dem Token.
 *
 * AR-Keys in den Rechtelisten: Unbekannte fallen beim Speichern still heraus, wie bei der Dateiart; wird eine AR
 * gelöscht, wirkt ihr Key hier nicht mehr, weil niemand sie hat. Die Anzeigeorte kommen aus dem Katalog der Dateiarten
 * (FileSubClassDestinationService), ein unbekannter Ort ist 400. Die Postfachadressen sind lose: geprüft wird nur die
 * Form und dass keine Adresse an zwei Nachrichtenarten hängt (409). Die Trigger-Hooks müssen angelegte Hooks sein (400).
 */
@Service
public class MessageSubClassService {

	/** Sortiert nach listingPosition und bei gleicher Position nach key */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	private final MessageSubClassRepository messageSubClassRepository;
	private final AccessRoleService accessRoleService;
	private final FileSubClassDestinationService fileSubClassDestinationService;
	private final SolrHookService solrHookService;

	public MessageSubClassService(MessageSubClassRepository messageSubClassRepository,
			AccessRoleService accessRoleService, FileSubClassDestinationService fileSubClassDestinationService,
			SolrHookService solrHookService) {
		this.messageSubClassRepository = messageSubClassRepository;
		this.accessRoleService = accessRoleService;
		this.fileSubClassDestinationService = fileSubClassDestinationService;
		this.solrHookService = solrHookService;
	}

	/**
	 * Neue Nachrichtenart. 400 bei ungültigem key, unbekanntem Anzeigeort, ungültiger Postfachadresse oder unbekanntem
	 * Trigger-Hook, 409 wenn der key vergeben ist oder eine Adresse schon an einer anderen Art hängt.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public MessageSubClass createMessageSubClass(String key, String displayName, String description, int listingPosition,
			AccessRoleSymbol symbol, String color, List<String> readAccessRoleKeys, List<String> writeAccessRoleKeys,
			List<String> displayContextKeys, List<String> mailboxEmailAddresses, List<String> triggerWords,
			List<String> triggerHookKeys) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde die bestehende Art überschreiben
		if (messageSubClassRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		Set<String> knownAccessRoleKeys = knownAccessRoleKeys();
		List<String> checkedWriteAccessRoleKeys = keepKnownAccessRoleKeys(writeAccessRoleKeys, knownAccessRoleKeys);
		List<String> checkedReadAccessRoleKeys = withWriteAccessRoleKeys(
				keepKnownAccessRoleKeys(readAccessRoleKeys, knownAccessRoleKeys), checkedWriteAccessRoleKeys);
		return messageSubClassRepository.save(new MessageSubClass(key, displayName, description, listingPosition, symbol,
				color, checkedReadAccessRoleKeys, checkedWriteAccessRoleKeys, checkedDisplayContextKeys(displayContextKeys),
				checkedMailboxEmailAddresses(key, mailboxEmailAddresses), checkedTriggerWords(triggerWords),
				checkedTriggerHookKeys(triggerHookKeys)));
	}

	/** Der key bestimmt nur, welche Art geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public MessageSubClass updateMessageSubClass(String key, String displayName, String description, int listingPosition,
			AccessRoleSymbol symbol, String color, List<String> readAccessRoleKeys, List<String> writeAccessRoleKeys,
			List<String> displayContextKeys, List<String> mailboxEmailAddresses, List<String> triggerWords,
			List<String> triggerHookKeys) {
		MessageSubClass messageSubClass = getMessageSubClassByKey(key);
		Set<String> knownAccessRoleKeys = knownAccessRoleKeys();
		List<String> checkedWriteAccessRoleKeys = keepKnownAccessRoleKeys(writeAccessRoleKeys, knownAccessRoleKeys);

		messageSubClass.setDisplayName(displayName);
		messageSubClass.setDescription(description);
		messageSubClass.setListingPosition(listingPosition);
		messageSubClass.setSymbol(symbol);
		messageSubClass.setColor(color);
		messageSubClass.setReadAccessRoleKeys(withWriteAccessRoleKeys(
				keepKnownAccessRoleKeys(readAccessRoleKeys, knownAccessRoleKeys), checkedWriteAccessRoleKeys));
		messageSubClass.setWriteAccessRoleKeys(checkedWriteAccessRoleKeys);
		messageSubClass.setDisplayContextKeys(checkedDisplayContextKeys(displayContextKeys));
		messageSubClass.setMailboxEmailAddresses(checkedMailboxEmailAddresses(key, mailboxEmailAddresses));
		messageSubClass.setTriggerWords(checkedTriggerWords(triggerWords));
		messageSubClass.setTriggerHookKeys(checkedTriggerHookKeys(triggerHookKeys));
		return messageSubClassRepository.save(messageSubClass);
	}

	/** Die Triggerwörter: getrimmt, leere fallen weg, Doppelte ohne Rücksicht auf Groß- und Kleinschreibung; null heißt keine */
	private static List<String> checkedTriggerWords(List<String> triggerWords) {
		if (triggerWords == null) {
			return List.of();
		}
		Set<String> seen = new HashSet<>();
		List<String> checked = new ArrayList<>();
		for (String triggerWord : triggerWords) {
			String trimmed = triggerWord == null ? "" : triggerWord.trim();
			if (!trimmed.isEmpty() && seen.add(trimmed.toLowerCase(Locale.ROOT))) {
				checked.add(trimmed);
			}
		}
		return checked;
	}

	/** Die Trigger-Hooks: jeder ein angelegter Hook (400 sonst), ohne Doppelte; null heißt keine */
	private List<String> checkedTriggerHookKeys(List<String> triggerHookKeys) {
		if (triggerHookKeys == null) {
			return List.of();
		}
		Set<String> knownHookKeys = solrHookService.getAllSolrHooks().stream().map(SolrHook::getKey)
				.collect(Collectors.toSet());
		Set<String> checked = new LinkedHashSet<>();
		for (String triggerHookKey : triggerHookKeys) {
			if (!knownHookKeys.contains(triggerHookKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hook " + triggerHookKey + " ist nicht angelegt");
			}
			checked.add(triggerHookKey);
		}
		return new ArrayList<>(checked);
	}

	/**
	 * Neue Positionen mehrerer Arten auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<MessageSubClass> changeMessageSubClassPositions(Map<String, Integer> newPositionByKey) {
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			MessageSubClass messageSubClass = getMessageSubClassByKey(newPosition.getKey());
			messageSubClass.setListingPosition(newPosition.getValue());
			messageSubClassRepository.save(messageSubClass);
		}
		return getAllMessageSubClasses();
	}

	/**
	 * Löscht die Art endgültig. Nachrichten, die ihren key tragen, haben danach keinen Namen und kein Symbol mehr und
	 * behalten ihre readAcl. Gibt die Art zurück, wie sie vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public MessageSubClass deleteMessageSubClass(String key) {
		MessageSubClass messageSubClass = getMessageSubClassByKey(key);
		messageSubClassRepository.delete(messageSubClass);
		return messageSubClass;
	}

	/** Gibt es die Art nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	@Transactional(readOnly = true)
	public MessageSubClass getMessageSubClassByKey(String key) {
		return messageSubClassRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Nachrichtenart mit diesem key"));
	}

	/**
	 * Alle Arten, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne Login, ohne
	 * @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	@Transactional(readOnly = true)
	public List<MessageSubClass> getAllMessageSubClasses() {
		return messageSubClassRepository.findAll(LISTING_ORDER);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isMessageSubClassKeyAvailable(String key) {
		return !messageSubClassRepository.existsById(key);
	}

	/** Die Anzeigeorte: jeder ein angelegter Anzeigeort aus dem Katalog der Dateiarten (400 sonst), ohne Doppelte */
	private List<String> checkedDisplayContextKeys(List<String> displayContextKeys) {
		if (displayContextKeys == null) {
			return List.of();
		}
		Set<String> checked = new LinkedHashSet<>();
		for (String displayContextKey : displayContextKeys) {
			if (!fileSubClassDestinationService.isFileSubClassDestinationKeyKnown(displayContextKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Anzeigeort " + displayContextKey + " ist nicht angelegt");
			}
			checked.add(displayContextKey);
		}
		return new ArrayList<>(checked);
	}

	/**
	 * Die Postfachadressen: getrimmt und klein, ohne Doppelte; jede mit genau einem @ in der Mitte und ohne Leerraum
	 * (400 sonst); keine, die schon an einer anderen Nachrichtenart hängt (409), denn ein Postfach zeigt auf höchstens
	 * eine Art, sonst wäre die Zuordnung einer Nachricht nach ihrem Postfach nicht eindeutig.
	 */
	private List<String> checkedMailboxEmailAddresses(String key, List<String> mailboxEmailAddresses) {
		if (mailboxEmailAddresses == null) {
			return List.of();
		}
		Set<String> checked = new LinkedHashSet<>();
		for (String address : mailboxEmailAddresses) {
			String normalized = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
			if (!isMailboxAddress(normalized)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Postfachadresse: " + address);
			}
			checked.add(normalized);
		}
		for (MessageSubClass other : messageSubClassRepository.findAll()) {
			if (other.getKey().equals(key)) {
				continue;
			}
			for (String address : other.getMailboxEmailAddresses()) {
				if (checked.contains(address)) {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"Postfach " + address + " hängt schon an der Nachrichtenart " + other.getKey());
				}
			}
		}
		return new ArrayList<>(checked);
	}

	/** Genau ein @, nicht am Rand, kein Leerraum */
	private static boolean isMailboxAddress(String address) {
		int at = address.indexOf('@');
		return at > 0 && at == address.lastIndexOf('@') && at < address.length() - 1
				&& address.chars().noneMatch(Character::isWhitespace);
	}

	/** Alle AR-Keys, die es gibt: ein Aufruf statt einer Prüfung je Key */
	private Set<String> knownAccessRoleKeys() {
		return accessRoleService.getAllAccessRoles().stream().map(AccessRole::getKey).collect(Collectors.toSet());
	}

	/** Nur Keys, die es als AR gibt, ohne Doppelte. Unbekannte fallen still heraus, siehe Klassenkommentar. */
	private static List<String> keepKnownAccessRoleKeys(List<String> accessRoleKeys, Set<String> knownAccessRoleKeys) {
		if (accessRoleKeys == null) {
			return List.of();
		}
		return new ArrayList<>(accessRoleKeys.stream()
				.filter(knownAccessRoleKeys::contains)
				.collect(Collectors.toCollection(LinkedHashSet::new)));
	}

	/** Hookpflege schließt Lesen ein: Wer die Hooks einer Nachricht ändern soll, muss sie auch sehen (Vorlage). */
	private static List<String> withWriteAccessRoleKeys(List<String> readAccessRoleKeys,
			List<String> writeAccessRoleKeys) {
		Set<String> allReadAccessRoleKeys = new LinkedHashSet<>(readAccessRoleKeys);
		allReadAccessRoleKeys.addAll(writeAccessRoleKeys);
		return new ArrayList<>(allReadAccessRoleKeys);
	}

}

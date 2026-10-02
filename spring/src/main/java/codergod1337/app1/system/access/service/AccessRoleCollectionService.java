package codergod1337.app1.system.access.service;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.model.AccessRoleSymbol;
import codergod1337.app1.system.access.model.BadgeGradient;
import codergod1337.app1.system.access.repository.AccessRoleCollectionRepository;
import codergod1337.app1.system.access.repository.AccessRoleRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Wer was darf, steht als @PreAuthorize über der Methode. Die Rollen kommen aus dem Token, ohne Datenbank. */
@Service
public class AccessRoleCollectionService {

	/** Pflicht-Präfix jedes ARC-keys */
	public static final String KEY_PREFIX = "ARC_";

	private final AccessRoleCollectionRepository accessRoleCollectionRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diese AR?“ über den AccessRoleService. Der braucht aber
	 * diesen Service, um beim Löschen einer AR deren key aus allen ARCs zu entfernen. Deshalb hier ausnahmsweise
	 * direkt das fremde AccessRoleRepository, und zwar nur lesend (existsById).
	 */
	private final AccessRoleRepository accessRoleRepository;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Beim Löschen einer ARC fliegen ihre Zuordnungen zu Usern über diesen Service weg. Der
	 * AccessRoleCollectionUsersAssignmentService müsste umgekehrt hier fragen, ob es eine ARC gibt. Damit es keinen
	 * Kreis gibt, liest er stattdessen direkt das AccessRoleCollectionRepository (dort kommentiert).
	 */
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;

	public AccessRoleCollectionService(AccessRoleCollectionRepository accessRoleCollectionRepository,
			AccessRoleRepository accessRoleRepository,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService) {
		this.accessRoleCollectionRepository = accessRoleCollectionRepository;
		this.accessRoleRepository = accessRoleRepository;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
	}

	/** Neue ARC, noch ohne AR: Die verleiht die ARC-AR-Matrix (changeAccessRoleCollectionAccessRoleKeys). */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AccessRoleCollection createAccessRoleCollection(String key, String displayName, String description,
			List<String> slaveArcKeys, String badgeTextColor, String badgeTextShadowColor, String badgeGradientStart,
			String badgeGradientEnd, String badgeBorderColor, String badgeShadowColor, BadgeGradient badgeGradient,
			List<String> badgeStyles, AccessRoleSymbol symbol, Integer badgeOpacity, int listingPosition) {
		if (!HelperInputs.isValidKey(key) || !key.startsWith(KEY_PREFIX)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig, er muss mit ARC_ beginnen");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde die bestehende ARC überschreiben
		if (!isAccessRoleCollectionKeyAvailable(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		checkSlaveArcKeys(key, slaveArcKeys);
		return accessRoleCollectionRepository.save(new AccessRoleCollection(key, displayName, description,
				new ArrayList<>(), slaveArcKeys, badgeTextColor, badgeTextShadowColor, badgeGradientStart, badgeGradientEnd,
				badgeBorderColor, badgeShadowColor, badgeGradient, badgeStyles, symbol,
				AccessRoleService.normalizeBadgeOpacity(badgeOpacity), listingPosition));
	}

	/**
	 * Der key bestimmt nur, welche ARC geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt,
	 * außer den AR: Die verleiht nur die ARC-AR-Matrix. Deshalb ändern sich hier auch keine Rechte im Token.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AccessRoleCollection updateAccessRoleCollection(String key, String displayName, String description,
			List<String> slaveArcKeys, String badgeTextColor, String badgeTextShadowColor, String badgeGradientStart,
			String badgeGradientEnd, String badgeBorderColor, String badgeShadowColor, BadgeGradient badgeGradient,
			List<String> badgeStyles, AccessRoleSymbol symbol, Integer badgeOpacity, int listingPosition) {
		AccessRoleCollection accessRoleCollection = getAccessRoleCollectionByKey(key);
		checkSlaveArcKeys(key, slaveArcKeys);

		accessRoleCollection.setDisplayName(displayName);
		accessRoleCollection.setDescription(description);
		accessRoleCollection.setSlaveArcKeys(slaveArcKeys);
		accessRoleCollection.setBadgeTextColor(badgeTextColor);
		accessRoleCollection.setBadgeTextShadowColor(badgeTextShadowColor);
		accessRoleCollection.setBadgeGradientStart(badgeGradientStart);
		accessRoleCollection.setBadgeGradientEnd(badgeGradientEnd);
		accessRoleCollection.setBadgeBorderColor(badgeBorderColor);
		accessRoleCollection.setBadgeShadowColor(badgeShadowColor);
		accessRoleCollection.setBadgeGradient(badgeGradient);
		accessRoleCollection.setBadgeStyles(badgeStyles);
		accessRoleCollection.setSymbol(symbol);
		accessRoleCollection.setBadgeOpacity(AccessRoleService.normalizeBadgeOpacity(badgeOpacity));
		accessRoleCollection.setListingPosition(listingPosition);
		return accessRoleCollectionRepository.save(accessRoleCollection);
	}

	/**
	 * Die AR mehrerer ARCs auf einmal, für die ARC-AR-Matrix: je ARC die vollständige neue Liste. Der Controller hat die
	 * keys geprüft, 404 für eine unbekannte ARC, 400 für eine unbekannte AR. Alles in einer Transaktion. Wessen ARC
	 * sich ändert, dessen Tokens gelten nicht mehr: Beim Refresh kommen die neuen AR. Zurück kommen alle ARCs.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<AccessRoleCollection> changeAccessRoleCollectionAccessRoleKeys(
			Map<String, List<String>> accessRoleKeysByKey) {
		for (Map.Entry<String, List<String>> changedAccessRoleKeys : accessRoleKeysByKey.entrySet()) {
			AccessRoleCollection accessRoleCollection = getAccessRoleCollectionByKey(changedAccessRoleKeys.getKey());
			List<String> accessRoleKeys = new ArrayList<>(new LinkedHashSet<>(changedAccessRoleKeys.getValue()));
			checkAccessRoleKeysExist(accessRoleKeys);
			accessRoleCollection.setAccessRoleKeys(accessRoleKeys);
			accessRoleCollectionRepository.save(accessRoleCollection);
			accessRoleCollectionUsersAssignmentService.revokeUsersTokensOfAccessRoleCollection(accessRoleCollection.getKey());
		}
		return getAllAccessRoleCollections();
	}

	/**
	 * Patch der Positionen mehrerer ARCs auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen
	 * schon geprüft. Alles in einer Transaktion.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<AccessRoleCollection> changeAccessRoleCollectionPositions(Map<String, Integer> newPositionByKey) {
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			AccessRoleCollection accessRoleCollection = getAccessRoleCollectionByKey(newPosition.getKey());
			accessRoleCollection.setListingPosition(newPosition.getValue());
			accessRoleCollectionRepository.save(accessRoleCollection);
		}
		return getAllAccessRoleCollections();
	}

	/**
	 * Entfernt die ARC auch aus den slaveArcKeys aller anderen und nimmt sie allen Usern. Gibt die ARC zurück, wie sie
	 * vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AccessRoleCollection deleteAccessRoleCollection(String key) {
		AccessRoleCollection accessRoleCollection = getAccessRoleCollectionByKey(key);
		for (AccessRoleCollection other : accessRoleCollectionRepository.findAll()) {
			if (other.getSlaveArcKeys() != null && other.getSlaveArcKeys().contains(key)) {
				other.setSlaveArcKeys(withoutKey(other.getSlaveArcKeys(), key));
				accessRoleCollectionRepository.save(other);
			}
		}
		// erst sperren, dann die Zuordnungen löschen: danach wüsste niemand mehr, wer die ARC hatte
		accessRoleCollectionUsersAssignmentService.revokeUsersTokensOfAccessRoleCollection(key);
		accessRoleCollectionUsersAssignmentService.deleteAllAccessRoleCollectionUsersAssignmentsOfAccessRoleCollection(key);
		accessRoleCollectionRepository.delete(accessRoleCollection);
		return accessRoleCollection;
	}

	/**
	 * Wird eine AccessRole gelöscht, fliegt ihr key aus allen ARCs, und die Tokens ihrer User gelten nicht mehr.
	 * Aufgerufen vom AccessRoleService, deshalb ohne @PreAuthorize: die Prüfung sitzt dort.
	 */
	@Transactional
	public void removeAccessRoleKeyFromAllCollections(String accessRoleKey) {
		for (AccessRoleCollection accessRoleCollection : accessRoleCollectionRepository.findAll()) {
			if (accessRoleCollection.getAccessRoleKeys() != null
					&& accessRoleCollection.getAccessRoleKeys().contains(accessRoleKey)) {
				accessRoleCollection.setAccessRoleKeys(withoutKey(accessRoleCollection.getAccessRoleKeys(), accessRoleKey));
				accessRoleCollectionRepository.save(accessRoleCollection);
				accessRoleCollectionUsersAssignmentService.revokeUsersTokensOfAccessRoleCollection(accessRoleCollection.getKey());
			}
		}
	}

	/**
	 * Die ARC zu einem key, leer wenn es sie nicht gibt. Für das Ausstellen des Tokens, deshalb ohne @PreAuthorize und
	 * ohne 404: Eine inzwischen gelöschte ARC darf niemanden aussperren.
	 */
	@Transactional(readOnly = true)
	public Optional<AccessRoleCollection> findAccessRoleCollectionByKey(String key) {
		return accessRoleCollectionRepository.findById(key);
	}

	/** Gibt es die ARC nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	@Transactional(readOnly = true)
	public AccessRoleCollection getAccessRoleCollectionByKey(String key) {
		return accessRoleCollectionRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine ARC mit diesem key"));
	}

	/** Alle ARCs, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne Login. */
	@Transactional(readOnly = true)
	public List<AccessRoleCollection> getAllAccessRoleCollections() {
		return accessRoleCollectionRepository.findAll(Sort.by("listingPosition", "key"));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isAccessRoleCollectionKeyAvailable(String key) {
		return !accessRoleCollectionRepository.existsById(key);
	}

	/** 400, wenn es eine der AccessRoles nicht gibt: ein Tippfehler soll beim Speichern auffallen. */
	private void checkAccessRoleKeysExist(List<String> accessRoleKeys) {
		if (accessRoleKeys == null) {
			return;
		}
		for (String accessRoleKey : accessRoleKeys) {
			// ZIRKELSCHLUSS: direkt das fremde AccessRoleRepository, siehe Feld accessRoleRepository
			if (!accessRoleRepository.existsById(accessRoleKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine AccessRole mit key " + accessRoleKey);
			}
		}
	}

	/** 400, wenn die ARC sich selbst nennt oder es eine der Slave-ARCs nicht gibt. Nicht rekursiv. */
	private void checkSlaveArcKeys(String key, List<String> slaveArcKeys) {
		if (slaveArcKeys == null) {
			return;
		}
		for (String slaveArcKey : slaveArcKeys) {
			if (slaveArcKey.equals(key)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eine ARC kann nicht ihr eigener Slave sein");
			}
			if (isAccessRoleCollectionKeyAvailable(slaveArcKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine ARC mit key " + slaveArcKey);
			}
		}
	}

	private static List<String> withoutKey(List<String> keys, String key) {
		List<String> remaining = new ArrayList<>(keys);
		remaining.removeIf(key::equals);
		return remaining;
	}

}

package codergod1337.app1.system.access.service;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.model.AccessRoleSymbol;
import codergod1337.app1.system.access.model.BadgeGradient;
import codergod1337.app1.system.access.repository.AccessRoleRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Wer was darf, steht als @PreAuthorize über der Methode. Die Rollen kommen aus dem Token, ohne Datenbank. */
@Service
public class AccessRoleService {

	private final AccessRoleRepository accessRoleRepository;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Beim Löschen einer AR muss ihr key aus allen ARCs fliegen, dafür dieser Service. Der
	 * AccessRoleCollectionService müsste umgekehrt hier fragen, ob es eine AR gibt. Damit es keinen Kreis gibt, liest
	 * er stattdessen direkt das AccessRoleRepository (dort kommentiert).
	 */
	private final AccessRoleCollectionService accessRoleCollectionService;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Beim Löschen einer AR fliegen ihre Zuordnungen zu Usern über diesen Service weg. Der
	 * AccessRoleUsersAssignmentService müsste umgekehrt hier fragen, ob es eine AR gibt. Damit es keinen Kreis gibt,
	 * liest er stattdessen direkt das AccessRoleRepository (dort kommentiert).
	 */
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;

	public AccessRoleService(AccessRoleRepository accessRoleRepository,
			AccessRoleCollectionService accessRoleCollectionService,
			AccessRoleUsersAssignmentService accessRoleUsersAssignmentService) {
		this.accessRoleRepository = accessRoleRepository;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
	}

	/** Das Format des Keys prüft der Service, damit es für jeden Aufrufer gilt, auch für das System. */
	@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
	// ADMIN: der current user, ermittelt über das Token, hat die AR ADMIN.
	// SYSTEM: der System-User, eine eigene Zeile in users ohne Login-Funktion. Bei allen Systemtätigkeiten läuft
	// das System als dieser User und ist damit überall "verlinkt" (z. B. als Autor). Die Berechtigung SYSTEM bekommt
	// er im Code, nicht über eine AR in der DB.
	@Transactional
	public AccessRole createAccessRole(String key, String displayName, String description, boolean system,
			String badgeTextColor, String badgeTextShadowColor, String badgeGradientStart, String badgeGradientEnd,
			String badgeBorderColor, String badgeShadowColor, BadgeGradient badgeGradient, List<String> badgeStyles,
			AccessRoleSymbol symbol, Integer badgeOpacity, int listingPosition) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen Key wäre ein Update und würde die bestehende Rolle überschreiben
		if (!isAccessRoleKeyAvailable(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		return accessRoleRepository.save(new AccessRole(key, displayName, description, system, badgeTextColor,
				badgeTextShadowColor, badgeGradientStart, badgeGradientEnd, badgeBorderColor, badgeShadowColor,
				badgeGradient, badgeStyles, symbol, normalizeBadgeOpacity(badgeOpacity), listingPosition));
	}

	/** Der key bestimmt nur, welche Rolle geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AccessRole updateAccessRole(String key, String displayName, String description, boolean system,
			String badgeTextColor, String badgeTextShadowColor, String badgeGradientStart, String badgeGradientEnd,
			String badgeBorderColor, String badgeShadowColor, BadgeGradient badgeGradient, List<String> badgeStyles,
			AccessRoleSymbol symbol, Integer badgeOpacity, int listingPosition) {
		AccessRole accessRole = getAccessRoleByKey(key);

		// system darf nur von false auf true wechseln, nie zurück
		if (accessRole.isSystem() && !system) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "system kann nicht zurückgenommen werden");
		}

		accessRole.setDisplayName(displayName);
		accessRole.setDescription(description);
		accessRole.setSystem(system);
		accessRole.setBadgeTextColor(badgeTextColor);
		accessRole.setBadgeTextShadowColor(badgeTextShadowColor);
		accessRole.setBadgeGradientStart(badgeGradientStart);
		accessRole.setBadgeGradientEnd(badgeGradientEnd);
		accessRole.setBadgeBorderColor(badgeBorderColor);
		accessRole.setBadgeShadowColor(badgeShadowColor);
		accessRole.setBadgeGradient(badgeGradient);
		accessRole.setBadgeStyles(badgeStyles);
		accessRole.setSymbol(symbol);
		accessRole.setBadgeOpacity(normalizeBadgeOpacity(badgeOpacity));
		accessRole.setListingPosition(listingPosition);
		return accessRoleRepository.save(accessRole);
	}

	/**
	 * Patch der Positionen mehrerer Rollen auf einmal, z. B. nach Drag and Drop: jeder key bekommt seine Position,
	 * sonst ändert sich nichts. Das Frontend zählt durch, der Controller hat keys und Zahlen schon geprüft.
	 * Alles in einer Transaktion: scheitert eine, bleibt keine geändert.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<AccessRole> changeAccessRolePositions(Map<String, Integer> newPositionByKey) {
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			AccessRole accessRole = getAccessRoleByKey(newPosition.getKey());
			accessRole.setListingPosition(newPosition.getValue());
			accessRoleRepository.save(accessRole);
		}
		return getAllAccessRoles();
	}

	/** Gibt die Rolle zurück, wie sie vor dem Löschen war. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AccessRole deleteAccessRole(String key) {
		AccessRole accessRole = getAccessRoleByKey(key);
		if (accessRole.isSystem()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "system-Rollen können nicht gelöscht werden");
		}
		accessRoleCollectionService.removeAccessRoleKeyFromAllCollections(key);
		accessRoleUsersAssignmentService.deleteAllAccessRoleUsersAssignmentsOfAccessRole(key);
		accessRoleRepository.delete(accessRole);
		return accessRole;
	}

	/**
	 * Gibt es die Rolle nicht, kommt 404. Für alle, ohne @PreAuthorize: Der Admin lädt eine Rolle nach dem Ändern neu,
	 * und Keys aus dem Token werden hierüber aufgelöst und geprüft.
	 */
	@Transactional(readOnly = true)
	public AccessRole getAccessRoleByKey(String key) {
		return accessRoleRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine AccessRole mit diesem key"));
	}

	/**
	 * Alle Rollen, sortiert nach listingPosition und bei gleicher Position nach key, damit die Reihenfolge stabil
	 * bleibt. Für alle, auch ohne Login, ohne @PreAuthorize: Das Frontend braucht die Liste immer.
	 */
	@Transactional(readOnly = true)
	public List<AccessRole> getAllAccessRoles() {
		return accessRoleRepository.findAll(Sort.by("listingPosition", "key"));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isAccessRoleKeyAvailable(String key) {
		return !accessRoleRepository.existsById(key);
	}

	/**
	 * Volle Deckkraft wird immer als null gespeichert. Unter 25 % wäre die Badge kaum noch sichtbar: 400.
	 * Gilt für jede Badge, deshalb öffentlich (auch der AccessRoleCollectionService nutzt es).
	 */
	public static Integer normalizeBadgeOpacity(Integer badgeOpacity) {
		if (badgeOpacity == null || badgeOpacity == 100) {
			return null;
		}
		if (badgeOpacity < 25 || badgeOpacity > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "badgeOpacity muss zwischen 25 und 100 liegen");
		}
		return badgeOpacity;
	}

}

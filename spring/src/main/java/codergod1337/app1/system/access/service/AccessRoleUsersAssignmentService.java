package codergod1337.app1.system.access.service;

import codergod1337.app1.system.access.model.AccessRoleUsersAssignment;
import codergod1337.app1.system.access.repository.AccessRoleRepository;
import codergod1337.app1.system.access.repository.AccessRoleUsersAssignmentRepository;
import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.user.repository.UsersRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Die AR, die Usern einzeln zugewiesen sind. AR aus einer ARC kommen erst beim Ausstellen des Tokens dazu. */
@Service
public class AccessRoleUsersAssignmentService {

	private final AccessRoleUsersAssignmentRepository accessRoleUsersAssignmentRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diesen User?“ über den UsersService. Der braucht aber diesen
	 * Service, um die Zuordnungen beim guid-Wechsel umzuziehen und beim Löschen wegzuräumen. Deshalb hier ausnahmsweise
	 * direkt das fremde UsersRepository, und zwar nur lesend (existsById).
	 */
	private final UsersRepository usersRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diese AR?“ über den AccessRoleService. Der braucht aber
	 * diesen Service, um beim Löschen einer AR ihre Zuordnungen wegzuräumen. Deshalb hier ausnahmsweise direkt das
	 * fremde AccessRoleRepository, und zwar nur lesend (existsById).
	 */
	private final AccessRoleRepository accessRoleRepository;

	private final TokenRevocations tokenRevocations;

	public AccessRoleUsersAssignmentService(AccessRoleUsersAssignmentRepository accessRoleUsersAssignmentRepository,
			UsersRepository usersRepository, AccessRoleRepository accessRoleRepository,
			TokenRevocations tokenRevocations) {
		this.accessRoleUsersAssignmentRepository = accessRoleUsersAssignmentRepository;
		this.usersRepository = usersRepository;
		this.accessRoleRepository = accessRoleRepository;
		this.tokenRevocations = tokenRevocations;
	}

	/** Alle Zuordnungen, für die Userliste: ein Aufruf statt einer pro User. */
	@Transactional(readOnly = true)
	public List<AccessRoleUsersAssignment> getAllAccessRoleUsersAssignments() {
		return accessRoleUsersAssignmentRepository.findAll();
	}

	/** Die AR-Keys eines Users. Braucht später auch der Login für das Token. */
	@Transactional(readOnly = true)
	public List<String> getAccessRoleKeysByUsersGuid(UUID usersGuid) {
		return accessRoleUsersAssignmentRepository.findByUsersGuid(usersGuid).stream()
				.map(AccessRoleUsersAssignment::getAccessRoleKey)
				.toList();
	}

	/**
	 * Setzt die AR eines Users auf genau diese Liste: Fehlende kommen dazu, überzählige fliegen raus, bestehende
	 * behalten ihr assignedAt. 404 wenn es den User nicht gibt, 400 bei einem unbekannten AR-Key. Zurück kommen die
	 * neuen Keys.
	 */
	@Transactional
	public List<String> changeAccessRoleUsersAssignments(UUID usersGuid, List<String> accessRoleKeys) {
		// ZIRKELSCHLUSS: direkt das fremde UsersRepository, siehe Feld usersRepository
		if (!usersRepository.existsById(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein User mit dieser guid");
		}
		Set<String> newAccessRoleKeys = new LinkedHashSet<>(accessRoleKeys);
		for (String accessRoleKey : newAccessRoleKeys) {
			// ZIRKELSCHLUSS: direkt das fremde AccessRoleRepository, siehe Feld accessRoleRepository
			if (!accessRoleRepository.existsById(accessRoleKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine AccessRole mit key " + accessRoleKey);
			}
		}

		Set<String> currentAccessRoleKeys = new LinkedHashSet<>();
		for (AccessRoleUsersAssignment currentAssignment : accessRoleUsersAssignmentRepository.findByUsersGuid(usersGuid)) {
			if (newAccessRoleKeys.contains(currentAssignment.getAccessRoleKey())) {
				currentAccessRoleKeys.add(currentAssignment.getAccessRoleKey());
			} else {
				accessRoleUsersAssignmentRepository.delete(currentAssignment);
			}
		}
		for (String accessRoleKey : newAccessRoleKeys) {
			if (!currentAccessRoleKeys.contains(accessRoleKey)) {
				accessRoleUsersAssignmentRepository.save(new AccessRoleUsersAssignment(usersGuid, accessRoleKey));
			}
		}
		// Die Rechte im laufenden JWT sind jetzt veraltet: Mit dem nächsten Refresh kommen die neuen
		tokenRevocations.revokeUsersTokens(usersGuid);
		return getAccessRoleKeysByUsersGuid(usersGuid);
	}

	/** guid-Wechsel: alle Zuordnungen ziehen auf die neue guid um. */
	@Transactional
	public void changeAllAccessRoleUsersAssignmentsGuid(UUID usersGuidCurrent, UUID usersGuidNew) {
		for (AccessRoleUsersAssignment assignment : accessRoleUsersAssignmentRepository.findByUsersGuid(usersGuidCurrent)) {
			assignment.setUsersGuid(usersGuidNew);
		}
	}

	/** Der User wird gelöscht: alle seine Zuordnungen weg. */
	@Transactional
	public void deleteAllAccessRoleUsersAssignmentsOfUsers(UUID usersGuid) {
		accessRoleUsersAssignmentRepository.deleteByUsersGuid(usersGuid);
	}

	/** Die AR wird gelöscht: niemand trägt sie mehr. */
	@Transactional
	public void deleteAllAccessRoleUsersAssignmentsOfAccessRole(String accessRoleKey) {
		accessRoleUsersAssignmentRepository.deleteByAccessRoleKey(accessRoleKey);
	}

}

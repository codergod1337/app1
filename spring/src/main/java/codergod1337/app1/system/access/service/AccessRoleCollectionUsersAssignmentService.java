package codergod1337.app1.system.access.service;

import codergod1337.app1.system.access.model.AccessRoleCollectionUsersAssignment;
import codergod1337.app1.system.access.repository.AccessRoleCollectionRepository;
import codergod1337.app1.system.access.repository.AccessRoleCollectionUsersAssignmentRepository;
import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.user.repository.UsersRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die ARC (Position) der User, optional und höchstens eine pro User. Die AR daraus kommen beim Ausstellen des Tokens
 * zu den direkt zugewiesenen dazu.
 */
@Service
public class AccessRoleCollectionUsersAssignmentService {

	private final AccessRoleCollectionUsersAssignmentRepository accessRoleCollectionUsersAssignmentRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diesen User?“ über den UsersService. Der braucht aber diesen
	 * Service, um die Zuordnung beim guid-Wechsel umzuziehen und beim Löschen wegzuräumen. Deshalb hier ausnahmsweise
	 * direkt das fremde UsersRepository, und zwar nur lesend (existsById).
	 */
	private final UsersRepository usersRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diese ARC?“ über den AccessRoleCollectionService. Der braucht
	 * aber diesen Service, um beim Löschen einer ARC ihre Zuordnungen wegzuräumen. Deshalb hier ausnahmsweise direkt
	 * das fremde AccessRoleCollectionRepository, und zwar nur lesend (existsById).
	 */
	private final AccessRoleCollectionRepository accessRoleCollectionRepository;

	private final TokenRevocations tokenRevocations;

	public AccessRoleCollectionUsersAssignmentService(
			AccessRoleCollectionUsersAssignmentRepository accessRoleCollectionUsersAssignmentRepository,
			UsersRepository usersRepository, AccessRoleCollectionRepository accessRoleCollectionRepository,
			TokenRevocations tokenRevocations) {
		this.accessRoleCollectionUsersAssignmentRepository = accessRoleCollectionUsersAssignmentRepository;
		this.usersRepository = usersRepository;
		this.accessRoleCollectionRepository = accessRoleCollectionRepository;
		this.tokenRevocations = tokenRevocations;
	}

	/** Alle Zuordnungen, für die Matrix: ein Aufruf statt einer pro User. */
	@Transactional(readOnly = true)
	public List<AccessRoleCollectionUsersAssignment> getAllAccessRoleCollectionUsersAssignments() {
		return accessRoleCollectionUsersAssignmentRepository.findAll();
	}

	/** Die ARC eines Users, leer wenn er keine hat. Eine ARC ist optional. Für das Ausstellen des Tokens. */
	@Transactional(readOnly = true)
	public Optional<String> findAccessRoleCollectionKeyByUsersGuid(UUID usersGuid) {
		return accessRoleCollectionUsersAssignmentRepository.findById(usersGuid)
				.map(AccessRoleCollectionUsersAssignment::getAccessRoleCollectionKey);
	}

	/**
	 * Die AR einer ARC haben sich geändert oder sie wird gelöscht: Die Tokens aller User mit dieser ARC gelten nicht
	 * mehr, beim Refresh bekommen sie die neuen AR.
	 */
	@Transactional(readOnly = true)
	public void revokeUsersTokensOfAccessRoleCollection(String accessRoleCollectionKey) {
		for (AccessRoleCollectionUsersAssignment assignment : accessRoleCollectionUsersAssignmentRepository
				.findByAccessRoleCollectionKey(accessRoleCollectionKey)) {
			tokenRevocations.revokeUsersTokens(assignment.getUsersGuid());
		}
	}

	/**
	 * Setzt die ARC eines Users: Eine vorhandene wird ersetzt, {@code null} entzieht sie. 404 wenn es den User nicht
	 * gibt, 400 bei einer unbekannten ARC. Zurück kommt die neue Zuordnung, ohne ARC {@code null}.
	 */
	@Transactional
	public AccessRoleCollectionUsersAssignment changeAccessRoleCollectionUsersAssignment(UUID usersGuid,
			String accessRoleCollectionKey) {
		// ZIRKELSCHLUSS: direkt das fremde UsersRepository, siehe Feld usersRepository
		if (!usersRepository.existsById(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein User mit dieser guid");
		}
		// ZIRKELSCHLUSS: direkt das fremde AccessRoleCollectionRepository, siehe Feld accessRoleCollectionRepository
		if (accessRoleCollectionKey != null && !accessRoleCollectionRepository.existsById(accessRoleCollectionKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"keine AccessRoleCollection mit key " + accessRoleCollectionKey);
		}

		// Die Rechte im laufenden JWT sind gleich veraltet: Mit dem nächsten Refresh kommen die neuen
		tokenRevocations.revokeUsersTokens(usersGuid);

		AccessRoleCollectionUsersAssignment assignment = accessRoleCollectionUsersAssignmentRepository
				.findById(usersGuid).orElse(null);
		if (accessRoleCollectionKey == null) {
			if (assignment != null) {
				accessRoleCollectionUsersAssignmentRepository.delete(assignment);
			}
			return null;
		}
		if (assignment == null) {
			return accessRoleCollectionUsersAssignmentRepository
					.save(new AccessRoleCollectionUsersAssignment(usersGuid, accessRoleCollectionKey));
		}
		if (!accessRoleCollectionKey.equals(assignment.getAccessRoleCollectionKey())) {
			assignment.setAccessRoleCollectionKey(accessRoleCollectionKey);
		}
		return accessRoleCollectionUsersAssignmentRepository.save(assignment);
	}

	/**
	 * guid-Wechsel: Die guid ist hier der PK und lässt sich in JPA nicht ändern, deshalb neue Zeile mit derselben ARC
	 * und demselben assignedAt, alte löschen. Ohne Zuordnung passiert nichts.
	 */
	@Transactional
	public void changeAccessRoleCollectionUsersAssignmentGuid(UUID usersGuidCurrent, UUID usersGuidNew) {
		accessRoleCollectionUsersAssignmentRepository.findById(usersGuidCurrent).ifPresent(oldAssignment -> {
			AccessRoleCollectionUsersAssignment newAssignment = new AccessRoleCollectionUsersAssignment(usersGuidNew,
					oldAssignment.getAccessRoleCollectionKey(), oldAssignment.getAssignedAt());
			accessRoleCollectionUsersAssignmentRepository.delete(oldAssignment);
			accessRoleCollectionUsersAssignmentRepository.save(newAssignment);
		});
	}

	/** Der User wird gelöscht: seine Zuordnung weg. Ohne Zuordnung passiert nichts. */
	@Transactional
	public void deleteAccessRoleCollectionUsersAssignmentOfUsers(UUID usersGuid) {
		accessRoleCollectionUsersAssignmentRepository.deleteById(usersGuid);
	}

	/** Die ARC wird gelöscht: niemand hat sie mehr. */
	@Transactional
	public void deleteAllAccessRoleCollectionUsersAssignmentsOfAccessRoleCollection(String accessRoleCollectionKey) {
		accessRoleCollectionUsersAssignmentRepository.deleteByAccessRoleCollectionKey(accessRoleCollectionKey);
	}

}

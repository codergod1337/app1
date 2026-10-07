package codergod1337.app1.solr.grant.service;

import codergod1337.app1.solr.grant.model.SolrHookGrantAccess;
import codergod1337.app1.solr.grant.repository.SolrHookGrantAccessRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.service.UsersService;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Hook-Freigaben, serverweit (Vorgabe 2026-10-07): das Recht, nach einem Hook aufzulösen, obwohl der User
 * {@code HOOK_FULL} nicht hat, in jedem Kern, in dem er die Tür {@code <KERN>} hat. Der Hook wird damit auch lesbar.
 * Reines Postgres, nichts davon in Solr. Die aktiven Zeilen liegen im Speicher, weil die Zugriffsrechnung sie bei jedem
 * Request eines eingeschränkten Users braucht: unveränderliche Liste, nur ersetzt, neu geladen nach jedem Commit, wie
 * bei der Feldfreigabe.
 *
 * Nichts wird gelöscht, eine Freigabe wechselt nur den Status (GrantEntityStateService). Beim Lesen zählen nur ACTIVE
 * und nicht abgelaufen: Eine abgelaufene Zeile ist sofort wirkungslos, auch bevor der Ablauf sie auf EXPIRED setzt.
 *
 * Wer freigeben darf: vorerst ADMIN. Später eine eigene AR, z. B. für die Datenpflege.
 */
@Service
public class SolrHookGrantAccessService {

	/** Neueste zuerst, in der Datenbank wie in der Liste */
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "grantedAt", "id");

	private final SolrHookGrantAccessRepository repository;
	private final SolrHookService solrHookService;
	private final UsersService usersService;
	private final CurrentUsersProvider currentUsersProvider;

	/** Nur die aktiven Zeilen, für die Zugriffsrechnung. Wird nur ersetzt, nie verändert. */
	private volatile List<SolrHookGrantAccess> activeGrants = List.of();

	public SolrHookGrantAccessService(SolrHookGrantAccessRepository repository, SolrHookService solrHookService,
			UsersService usersService, CurrentUsersProvider currentUsersProvider) {
		this.repository = repository;
		this.solrHookService = solrHookService;
		this.usersService = usersService;
		this.currentUsersProvider = currentUsersProvider;
	}

	@PostConstruct
	void loadActiveGrants() {
		reloadActiveGrants();
	}

	/**
	 * Die Hooks, nach denen dieser User zusätzlich zu seinen Rollen auflösen darf: aktive, nicht abgelaufene Zeilen aus
	 * dem Speicher. Für die Zugriffsrechnung, ohne @PreAuthorize: Der Aufrufer fragt nach sich selbst.
	 */
	public Set<String> activeGrantedHookKeysOfUsers(UUID granteeUsersGuid) {
		Instant now = Instant.now();
		Set<String> hookKeys = new HashSet<>();
		for (SolrHookGrantAccess grant : activeGrants) {
			if (grant.getGranteeUsersGuid().equals(granteeUsersGuid) && isInForce(grant, now)) {
				hookKeys.add(grant.getHookKey());
			}
		}
		return hookKeys;
	}

	/** Alle Zeilen, auch entzogene und abgelaufene, neueste zuerst: das Protokoll für die Maske. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrHookGrantAccess> getAllHookGrants() {
		return repository.findAll(NEWEST_FIRST);
	}

	/**
	 * Die Hook-Freigaben des current users, auch entzogene und abgelaufene, neueste zuerst: der Tab „Meine Freigaben“.
	 * Ohne @PreAuthorize, der User fragt nach sich selbst, die guid kommt aus dem Token.
	 */
	public List<SolrHookGrantAccess> hookGrantsOfCurrentUser() {
		List<SolrHookGrantAccess> own = new ArrayList<>(
				repository.findByGranteeUsersGuid(currentUsersProvider.getCurrentUsersGuid()));
		own.sort(newestFirst());
		return own;
	}

	/**
	 * Gibt dem User diese Hooks frei, eine Zeile je Hook. 404 bei unbekanntem User, 400 bei einem Key, der kein Hook
	 * ist, bei einer schon aktiven Freigabe desselben Hooks und bei einem Ablauf in der Vergangenheit. Alles oder
	 * nichts: eine Transaktion.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<SolrHookGrantAccess> grantHooksToUsers(UUID granteeUsersGuid, List<String> hookKeys, Instant validUntil) {
		usersService.getUsersByGuid(granteeUsersGuid);
		requireValidUntilInFuture(validUntil);
		if (hookKeys == null || hookKeys.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hookKeys fehlt oder ist leer");
		}
		Set<String> knownHookKeys = new LinkedHashSet<>();
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			knownHookKeys.add(solrHook.getKey());
		}
		Set<String> alreadyGranted = activeGrantedHookKeysOfUsers(granteeUsersGuid);
		List<String> problems = new ArrayList<>();
		for (String hookKey : new LinkedHashSet<>(hookKeys)) {
			if (!knownHookKeys.contains(hookKey)) {
				problems.add(hookKey + ": kein Hook");
			} else if (alreadyGranted.contains(hookKey)) {
				problems.add(hookKey + ": schon freigegeben");
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		Instant now = Instant.now();
		UUID grantedByUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		List<SolrHookGrantAccess> created = new ArrayList<>();
		for (String hookKey : new LinkedHashSet<>(hookKeys)) {
			created.add(new SolrHookGrantAccess(granteeUsersGuid, hookKey, grantedByUsersGuid, now, validUntil,
					GrantEntityStateService.ACTIVE));
		}
		List<SolrHookGrantAccess> saved = repository.saveAll(created);
		reloadActiveGrantsAfterCommit();
		return saved;
	}

	/** Entzieht die Freigabe: Status REVOKED, wer und wann. 404 ohne die Zeile, 409 wenn sie nicht mehr aktiv ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHookGrantAccess revokeHookGrant(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		SolrHookGrantAccess grant = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Hook-Freigabe mit dieser id"));
		if (!GrantEntityStateService.ACTIVE.equals(grant.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Freigabe ist nicht mehr aktiv");
		}
		grant.changeStatus(GrantEntityStateService.REVOKED, Instant.now(), currentUsersProvider.getCurrentUsersGuid());
		SolrHookGrantAccess saved = repository.save(grant);
		reloadActiveGrantsAfterCommit();
		return saved;
	}

	/**
	 * Setzt alle aktiven Zeilen mit erreichtem Ablauf auf EXPIRED. Wirkungslos sind sie schon vorher (isInForce). Bis es
	 * den System-User gibt, stößt das der Admin an, danach ein Systemjob einmal am Tag. Gibt zurück, wie viele es waren.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public int expireHookGrants() {
		Instant now = Instant.now();
		UUID byUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		List<SolrHookGrantAccess> expired = new ArrayList<>();
		for (SolrHookGrantAccess grant : repository.findByStatus(GrantEntityStateService.ACTIVE)) {
			if (grant.getValidUntil() != null && !grant.getValidUntil().isAfter(now)) {
				grant.changeStatus(GrantEntityStateService.EXPIRED, now, byUsersGuid);
				expired.add(grant);
			}
		}
		repository.saveAll(expired);
		reloadActiveGrantsAfterCommit();
		return expired.size();
	}

	private static Comparator<SolrHookGrantAccess> newestFirst() {
		return Comparator.comparing(SolrHookGrantAccess::getGrantedAt).thenComparing(SolrHookGrantAccess::getId)
				.reversed();
	}

	private static boolean isInForce(SolrHookGrantAccess grant, Instant now) {
		return GrantEntityStateService.ACTIVE.equals(grant.getStatus())
				&& (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
	}

	private static void requireValidUntilInFuture(Instant validUntil) {
		if (validUntil != null && !validUntil.isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validUntil liegt in der Vergangenheit");
		}
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie bei der Feldfreigabe. */
	private void reloadActiveGrantsAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadActiveGrants();
				}
			});
		} else {
			reloadActiveGrants();
		}
	}

	/** Nur die aktiven Zeilen in den Speicher, mehr braucht die Zugriffsrechnung nicht. */
	private void reloadActiveGrants() {
		List<SolrHookGrantAccess> active = new ArrayList<>(repository.findByStatus(GrantEntityStateService.ACTIVE));
		active.sort(Comparator.comparing(SolrHookGrantAccess::getId));
		activeGrants = List.copyOf(active);
	}

}

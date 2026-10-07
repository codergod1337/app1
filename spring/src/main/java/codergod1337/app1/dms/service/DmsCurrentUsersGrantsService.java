package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsCurrentUsersGrantsForClient;
import codergod1337.app1.dms.model.DmsResolvedDocumentsPage;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.dms.model.DmsSolrDocumentGrantsForClient;
import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.model.SolrHookGrantAccess;
import codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService;
import codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService;
import codergod1337.app1.solr.grant.service.SolrHookGrantAccessService;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.service.UsersService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Freigaben aus Sicht des DMS: der Tab „Meine Freigaben“ (Vorgabe 2026-10-04), der Weg eines eingeschränkten
 * Users zu seinen Dokumenten, denn mit der AR DMS allein löst er nur nach id auf. Das Register (seine Datei- und
 * Feldfreigaben im Kern DMS, seine Hook-Freigaben serverweit) und, über die Lese-Tür des DMS, die Dokumente mit seiner
 * guid in userGrantedAccess. Dazu der Kontrollblick des Admins auf die Freigaben eines Dokuments. Die guid des current
 * users kommt aus dem Token, nie vom Client.
 */
@Service
public class DmsCurrentUsersGrantsService {

	private final SolrDocumentGrantedDocsPerCoreService documentGrantRegisterService;
	private final SolrAdvancedFieldGrantAccessPerCoreService fieldGrantService;
	private final SolrHookGrantAccessService hookGrantService;
	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final UsersService usersService;

	public DmsCurrentUsersGrantsService(SolrDocumentGrantedDocsPerCoreService documentGrantRegisterService,
			SolrAdvancedFieldGrantAccessPerCoreService fieldGrantService, SolrHookGrantAccessService hookGrantService,
			DmsExactFieldValueResolveService dmsExactFieldValueResolveService, UsersService usersService) {
		this.documentGrantRegisterService = documentGrantRegisterService;
		this.fieldGrantService = fieldGrantService;
		this.hookGrantService = hookGrantService;
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.usersService = usersService;
	}

	/** Das Register des current users: Datei- und Feldfreigaben im Kern DMS, Hook-Freigaben serverweit, mit den Namen der Freigebenden */
	@PreAuthorize("hasRole('DMS')")
	public DmsCurrentUsersGrantsForClient currentUsersGrantsForClient() {
		List<SolrDocumentGrantedDocsPerCore> documentGrants = documentGrantRegisterService
				.documentGrantsOfCurrentUserInCore(DmsSolrDocumentFieldNames.CORE_KEY);
		List<SolrAdvancedFieldGrantAccessPerCore> fieldGrants = fieldGrantService
				.fieldGrantsOfCurrentUserInCore(DmsSolrDocumentFieldNames.CORE_KEY);
		List<SolrHookGrantAccess> hookGrants = hookGrantService.hookGrantsOfCurrentUser();
		LinkedHashSet<UUID> guids = new LinkedHashSet<>();
		for (SolrDocumentGrantedDocsPerCore grant : documentGrants) {
			guids.add(grant.getGrantedByUsersGuid());
		}
		for (SolrAdvancedFieldGrantAccessPerCore grant : fieldGrants) {
			guids.add(grant.getGrantedByUsersGuid());
		}
		for (SolrHookGrantAccess grant : hookGrants) {
			guids.add(grant.getGrantedByUsersGuid());
		}
		return new DmsCurrentUsersGrantsForClient(documentGrants, fieldGrants, hookGrants, usersDisplayNameByGuid(guids));
	}

	/** Die Dokumente mit der guid des current users in userGrantedAccess, seitenweise, mit seiner Leseprüfung */
	@PreAuthorize("hasRole('DMS')")
	public DmsResolvedDocumentsPage resolveDocumentsPageGrantedToCurrentUser(Integer page, Integer size) {
		return dmsExactFieldValueResolveService.resolveDmsSolrDocumentsPageForMyGrants(page != null ? page : 0,
				size != null ? size : DmsExactFieldValueResolveService.DEFAULT_PAGE_SIZE);
	}

	/**
	 * Der Kontrollblick auf die Freigaben eines Dokuments (Vorgabe 2026-10-04): die guids aus userGrantedAccess, gelesen
	 * fürs Backend mit der Leseprüfung des Users, dazu die wirksamen Dateifreigaben dieser User im Kern DMS und ihre
	 * Namen. Vorerst ADMIN, später weitere Rollen über denselben Endpunkt. 404, wenn er das Dokument nicht sehen darf.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public DmsSolrDocumentGrantsForClient loadGrantsOfDmsSolrDocumentForAdminCheck(String id) {
		List<String> grantedUsersGuids = dmsExactFieldValueResolveService.loadGranteesOfDmsSolrDocument(id);
		if (grantedUsersGuids == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit dieser id");
		}
		List<UUID> parsed = new ArrayList<>();
		for (String text : grantedUsersGuids) {
			try {
				parsed.add(UUID.fromString(text));
			} catch (IllegalArgumentException ignored) {
				// ein Eintrag, der keine guid ist, bleibt als Text stehen und hat kein Register
			}
		}
		List<SolrDocumentGrantedDocsPerCore> inForce = parsed.isEmpty() ? List.of()
				: documentGrantRegisterService.documentGrantsInForceOfUsersInCore(parsed, DmsSolrDocumentFieldNames.CORE_KEY);
		return new DmsSolrDocumentGrantsForClient(grantedUsersGuids, inForce, usersDisplayNameByGuid(parsed));
	}

	/**
	 * guid zu lesbarem Namen: Vorname Nachname, sonst username, sonst email, sonst die guid. Ein User, den es nicht mehr
	 * gibt, bleibt als guid stehen, nichts bricht ab.
	 */
	public Map<String, String> usersDisplayNameByGuid(Collection<UUID> usersGuids) {
		Map<String, String> names = new LinkedHashMap<>();
		for (UUID guid : new LinkedHashSet<>(usersGuids)) {
			names.put(guid.toString(), displayNameOf(guid));
		}
		return names;
	}

	private String displayNameOf(UUID guid) {
		Users users;
		try {
			users = usersService.getUsersByGuid(guid);
		} catch (ResponseStatusException e) {
			return guid.toString();
		}
		String fullName = ((users.getVorname() == null ? "" : users.getVorname()) + " "
				+ (users.getNachname() == null ? "" : users.getNachname())).strip();
		if (!fullName.isEmpty()) {
			return fullName;
		}
		if (users.getUsername() != null && !users.getUsername().isBlank()) {
			return users.getUsername();
		}
		if (users.getEmail() != null && !users.getEmail().isBlank()) {
			return users.getEmail();
		}
		return guid.toString();
	}

}

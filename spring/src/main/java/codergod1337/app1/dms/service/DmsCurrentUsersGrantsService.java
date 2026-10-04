package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsCurrentUsersGrantsForClient;
import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService.SolrResolvedDocumentsPage;
import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService;
import codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.service.UsersService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Tab „Meine Freigaben“ im DMS (Vorgabe 2026-10-04): der Weg eines eingeschränkten Users zu seinen Dokumenten, denn
 * mit der AR DMS allein löst er nur nach id auf. Das Register (seine Datei- und Feldfreigaben im Kern DMS) und,
 * seitenweise, die Dokumente, die seine guid in userGrantedAccess tragen, mit seiner Leseprüfung und seinen lesbaren
 * Feldern. Die guid kommt aus dem Token, nie vom Client.
 */
@Service
public class DmsCurrentUsersGrantsService {

	/** Neueste Dokumente zuerst, wenn der User das Feld lesen darf, sonst in Indexreihenfolge */
	private static final String PREFERRED_SORT_FIELD_NAME = "documentCreatedAt";

	private final SolrDocumentGrantedDocsPerCoreService documentGrantService;
	private final SolrAdvancedFieldGrantAccessPerCoreService fieldGrantService;
	private final SolrExactFieldValueResolveService solrExactFieldValueResolveService;
	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final UsersService usersService;
	private final CurrentUsersProvider currentUsersProvider;

	public DmsCurrentUsersGrantsService(SolrDocumentGrantedDocsPerCoreService documentGrantService,
			SolrAdvancedFieldGrantAccessPerCoreService fieldGrantService,
			SolrExactFieldValueResolveService solrExactFieldValueResolveService,
			SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService, UsersService usersService,
			CurrentUsersProvider currentUsersProvider) {
		this.documentGrantService = documentGrantService;
		this.fieldGrantService = fieldGrantService;
		this.solrExactFieldValueResolveService = solrExactFieldValueResolveService;
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.usersService = usersService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Das Register des current users im Kern DMS, alle Status, neueste zuerst, mit den Namen der Freigebenden */
	@PreAuthorize("hasRole('DMS')")
	public DmsCurrentUsersGrantsForClient currentUsersGrantsForClient() {
		List<SolrDocumentGrantedDocsPerCore> documentGrants = documentGrantService
				.documentGrantsOfCurrentUserInCore(DmsUploadService.CORE_KEY);
		List<SolrAdvancedFieldGrantAccessPerCore> fieldGrants = fieldGrantService
				.fieldGrantsOfCurrentUserInCore(DmsUploadService.CORE_KEY);
		LinkedHashSet<UUID> guids = new LinkedHashSet<>();
		for (SolrDocumentGrantedDocsPerCore grant : documentGrants) {
			guids.add(grant.getGrantedByUsersGuid());
		}
		for (SolrAdvancedFieldGrantAccessPerCore grant : fieldGrants) {
			guids.add(grant.getGrantedByUsersGuid());
		}
		return new DmsCurrentUsersGrantsForClient(documentGrants, fieldGrants, usersDisplayNameByGuid(guids));
	}

	/** Die Dokumente mit der guid des current users in userGrantedAccess, seitenweise, mit seiner Leseprüfung */
	@PreAuthorize("hasRole('DMS')")
	public SolrResolvedDocumentsPage resolveDocumentsPageGrantedToCurrentUser(Integer page, Integer size) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(DmsUploadService.CORE_KEY);
		String sortFieldName = access.isReadableByCurrentUser(PREFERRED_SORT_FIELD_NAME) ? PREFERRED_SORT_FIELD_NAME
				: null;
		return solrExactFieldValueResolveService.resolveDocumentsPageCarryingGranteeUsersGuid(DmsUploadService.CORE_KEY,
				currentUsersProvider.getCurrentUsersGuid(), page != null ? page : 0,
				size != null ? size : SolrExactFieldValueResolveService.DEFAULT_PAGE_SIZE, sortFieldName, true);
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

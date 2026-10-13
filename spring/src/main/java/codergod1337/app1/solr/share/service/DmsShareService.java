package codergod1337.app1.solr.share.service;

import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsUtilsService;
import codergod1337.app1.solr.share.model.CreatedShareToken;
import codergod1337.app1.solr.share.model.ShareToken;
import codergod1337.app1.solr.share.model.ShareTokenKind;
import codergod1337.app1.solr.share.model.UserGrant;
import codergod1337.app1.solr.share.model.UserGrantStatus;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.service.UsersService;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Freigabe von DMS-Dokumenten per Abholschein: anlegen, auflisten, zurückziehen, und die Station „Detailansicht“
 * lesen. Hier liegt alles, was Abholschein und DMS verbindet; der SolrDocumentDmsService kennt davon nur die
 * Download-Methode getDownloadByToken. Die Tabelle führt der ShareTokenService, den Hash ins Dokument schreibt dieser
 * Service selbst (downloadGrantedAccess), über den Client des Kerns DMS.
 */
@Service
public class DmsShareService {

	private static final String DMS_CORE_KEY = "DMS";

	private final ShareTokenService shareTokenService;
	private final SolrDocumentDmsService solrDocumentDmsService;
	private final SolrDocumentDmsUtilsService solrDocumentDmsUtilsService;
	private final CurrentUsersProvider currentUsersProvider;
	private final SolrClientRegistry solrClientRegistry;
	private final UsersService usersService;
	private final UserGrantService userGrantService;

	public DmsShareService(ShareTokenService shareTokenService, SolrDocumentDmsService solrDocumentDmsService,
			SolrDocumentDmsUtilsService solrDocumentDmsUtilsService, CurrentUsersProvider currentUsersProvider,
			SolrClientRegistry solrClientRegistry, UsersService usersService, UserGrantService userGrantService) {
		this.shareTokenService = shareTokenService;
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.solrDocumentDmsUtilsService = solrDocumentDmsUtilsService;
		this.currentUsersProvider = currentUsersProvider;
		this.solrClientRegistry = solrClientRegistry;
		this.usersService = usersService;
		this.userGrantService = userGrantService;
	}

	// ===== Freigabe an einen User: seine guid im Dokument, das Register sagt wer, wann, wem (User 2026-10-10) =====

	public List<UserGrant> getUserGrants(String id) {
		// Das Freigabe-Popup ruft das: die History der User-Freigaben dieses Dokuments aus dem Register, jüngste zuerst.
		// 404, wenn der Freigebende das Dokument nicht sieht.
		requireShareDoor();
		if (solrDocumentDmsService.getById(id) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
		return userGrantService.getUserGrantsByResource(DMS_CORE_KEY, id);
	}

	public List<UserGrant> getAllUserGrants() {
		// Die Übersicht „Freigaben“ ruft das: alle User-Freigaben des Kerns DMS, jüngste zuerst, auch zurückgenommene
		requireShareDoor();
		return userGrantService.getUserGrantsByCore(DMS_CORE_KEY);
	}

	public UserGrant grantToUser(String id, String usersGuid) {
		// Das Freigabe-Popup ruft das: Zeile im Register (wer, wann, wem), dann die guid per add-distinct in
		// userGrantedAccess; danach liest, sucht, löst und lädt der User das Dokument wie über die readAcl
		// (Leseprüfung in buildUserReadAclFilterQuery). Der User muss existieren.
		requireShareDoor();
		UUID guid = checkedUsersGuid(usersGuid);
		if (solrDocumentDmsService.getById(id) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
		usersService.getUsersByGuid(guid);
		UserGrant userGrant = userGrantService.createUserGrant(DMS_CORE_KEY, id, guid,
				currentUsersProvider.getCurrentUsersGuid());
		SolrInputDocument update = new SolrInputDocument();
		update.addField("id", id);
		update.addField("userGrantedAccess", Map.of("add-distinct", guid.toString()));
		send(update);
		return userGrant;
	}

	public void revokeFromUser(String id, String usersGuid) {
		// Das Freigabe-Popup ruft das: die aktiven Zeilen des Empfängers werden REVOKED, die guid geht aus
		// userGrantedAccess, der User sieht das Dokument nur noch über readAcl
		requireShareDoor();
		UUID guid = checkedUsersGuid(usersGuid);
		if (solrDocumentDmsService.getById(id) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
		userGrantService.revokeUserGrants(DMS_CORE_KEY, id, guid, currentUsersProvider.getCurrentUsersGuid());
		removeUserFromDocument(id, guid);
	}

	public void deleteUserGrant(long userGrantId) {
		// Der Admin-Endpunkt ruft das aus der Übersicht: war die Freigabe noch aktiv, geht die guid aus dem Dokument,
		// dann die Zeile samt History weg
		if (!currentUsersProvider.getCurrentUsersAccessRoleKeys().contains("ADMIN")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Löschen nur als ADMIN");
		}
		UserGrant userGrant = userGrantService.getUserGrantById(userGrantId);
		if (userGrant.getStatus() == UserGrantStatus.ACTIVE) {
			removeUserFromDocument(userGrant.getDocumentId(), userGrant.getGranteeUsersGuid());
		}
		userGrantService.deleteUserGrant(userGrant);
	}

	private void removeUserFromDocument(String id, UUID guid) {
		SolrInputDocument update = new SolrInputDocument();
		update.addField("id", id);
		update.addField("userGrantedAccess", Map.of("remove", guid.toString()));
		send(update);
	}

	private static UUID checkedUsersGuid(String usersGuid) {
		if (!HelperInputs.isValidGuid(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid ist keine gültige guid");
		}
		return UUID.fromString(usersGuid);
	}

	private void requireShareDoor() {
		// Jede Freigabe-Methode ruft das zuerst: Freigeben darf nur ADMIN oder die AR der Internetfreigabe (User 2026-10-09)
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.contains("ADMIN") && !accessRoleKeys.contains("DMS_PUBLIC")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Freigeben ohne DMS_PUBLIC");
		}
	}

	public CreatedShareToken createShareToken(String id, ShareTokenKind kind, List<String> includedFieldNames,
			boolean binaryIncluded, String recipient, int maxUses, Instant expiresAt) {
		// Der Freigabe-Controller ruft das, wenn ein User auf der Dokumentseite einen Abholschein anlegt: Recht, das Dokument
		// muss für ihn sichtbar sein, die Felder muss er selbst lesen dürfen; dann Zeile in Postgres, Hash ins Dokument.
		requireShareDoor();
		if (solrDocumentDmsService.getById(id) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
		// BINARY hat keine Felder und immer den Download; DOCUMENT braucht mindestens ein Feld
		List<String> fieldNames = kind == ShareTokenKind.BINARY ? List.of() : includedFieldNames;
		if (kind == ShareTokenKind.DOCUMENT && fieldNames.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "includedFieldNames ist leer");
		}
		List<String> readableFieldNames = solrDocumentDmsUtilsService.readableFieldNamesOfCurrentUser();
		for (String fieldName : fieldNames) {
			// ein Feld, das der Freigebende selbst nicht lesen darf, gibt er auch nicht weiter
			if (!readableFieldNames.contains(fieldName)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Freigeben von " + fieldName);
			}
		}
		CreatedShareToken created = shareTokenService.createShareToken(DMS_CORE_KEY, id, kind, fieldNames,
				kind == ShareTokenKind.BINARY || binaryIncluded, currentUsersProvider.getCurrentUsersGuid(), recipient,
				maxUses, expiresAt);
		addPickupToken(id, created.shareToken().getTokenHash());
		return created;
	}

	public List<ShareToken> getShareTokens(String id) {
		// Der Freigabe-Controller ruft das für die Liste auf der Dokumentseite: alle Scheine des Dokuments, auch abgelaufene
		requireShareDoor();
		return shareTokenService.getShareTokensByResource(DMS_CORE_KEY, id);
	}

	public List<ShareToken> getAllShareTokens() {
		// Die Übersicht „Freigaben“ ruft das: alle Scheine des Kerns DMS, jüngste zuerst, auch abgelaufene (User 2026-10-10)
		requireShareDoor();
		return shareTokenService.getShareTokensByCore(DMS_CORE_KEY);
	}

	public ShareToken expireShareToken(long shareTokenId) {
		// Der Freigabe-Controller ruft das beim Zurückziehen: Zeile EXPIRED, Hash vom Dokument, die History bleibt
		requireShareDoor();
		ShareToken shareToken = shareTokenService.getShareTokenById(shareTokenId);
		ShareToken expired = shareTokenService.expireShareToken(shareToken);
		removePickupToken(shareToken.getDocumentId(), shareToken.getTokenHash());
		return expired;
	}

	public void deleteShareToken(long shareTokenId) {
		// Der Admin-Endpunkt ruft das aus der Übersicht „Freigaben“ (User 2026-10-10): Hash vom Dokument, dann die Zeile
		// samt History weg. Der Pfad /admin/** lässt nur ADMIN herein, hier zur Sicherheit noch einmal.
		if (!currentUsersProvider.getCurrentUsersAccessRoleKeys().contains("ADMIN")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Löschen nur als ADMIN");
		}
		ShareToken shareToken = shareTokenService.getShareTokenById(shareTokenId);
		removePickupToken(shareToken.getDocumentId(), shareToken.getTokenHash());
		shareTokenService.deleteShareToken(shareToken);
	}

	public Map<String, Object> getByToken(ShareToken shareToken) {
		// Die Station "Detailansicht" ruft das mit einem einlösbaren Schein, ohne angemeldeten User: nur die Felder aus der
		// Liste des Scheins, frisch aus Solr, plus id. null, wenn das Dokument den Hash nicht mehr trägt oder weg ist.
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		params.set("rows", 1);
		params.add("fq", "id:" + ClientUtils.escapeQueryChars(shareToken.getDocumentId()));
		params.add("fq", "downloadGrantedAccess:" + ClientUtils.escapeQueryChars(shareToken.getTokenHash()));
		params.add("fq", "status:(ACTIVE OR ARCHIVED)");
		Set<String> fieldList = new LinkedHashSet<>();
		fieldList.add("id");
		fieldList.addAll(shareToken.getIncludedFieldNames());
		params.set("fl", String.join(",", fieldList));
		try {
			QueryResponse response = client().query(params);
			if (response.getResults().isEmpty()) {
				return null;
			}
			// SolrDocument ist selbst eine Map Feldname -> Wert
			return new LinkedHashMap<>(response.getResults().get(0));
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public void addPickupToken(String id, String tokenHash) {
		// createShareToken ruft das nach dem Anlegen der Zeile: der Hash kommt ins Dokument, daran prüfen die Stationen
		SolrInputDocument update = new SolrInputDocument();
		update.addField("id", id);
		// add-distinct: anhängen, wenn noch nicht drin, die anderen Hashes bleiben
		update.addField("downloadGrantedAccess", Map.of("add-distinct", tokenHash));
		send(update);
	}

	public void removePickupToken(String id, String tokenHash) {
		// Zurückziehen und die letzte erlaubte Einlösung rufen das: der Hash geht aus dem Dokument, der Schein ist damit
		// auch in Solr nicht mehr gültig
		SolrInputDocument update = new SolrInputDocument();
		update.addField("id", id);
		update.addField("downloadGrantedAccess", Map.of("remove", tokenHash));
		send(update);
	}

	private SolrClient client() {
		return solrClientRegistry.clientFor(DMS_CORE_KEY);
	}

	private void send(SolrInputDocument update) {
		// Atomic Update an …/solr/DMS/update, dann Soft Commit, damit der Hash sofort gilt
		try {
			client().add(update);
			client().commit(false, false, true);
		} catch (SolrException e) {
			throw new ResponseStatusException(e.code() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE,
					"Solr lehnt das Schreiben ab (Kern " + DMS_CORE_KEY + "): " + e.getMessage(), e);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	private static ResponseStatusException unavailable(Exception e) {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + DMS_CORE_KEY + "): " + e.getMessage(), e);
	}

}

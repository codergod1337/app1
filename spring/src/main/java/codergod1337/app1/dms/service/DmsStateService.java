package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;

import codergod1337.app1.system.state.model.EntityState;
import codergod1337.app1.system.state.service.EntityStateService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Die Status eines DMS-Dokuments (Feld status im Kern DMS), als EntityState im Bereich DMS. Beim Start legt dieser
 * Service die vier Status an, falls sie fehlen, mit dreisprachigem Namen; was der Admin danach daran ändert, bleibt.
 * Die keys stehen hier fest, weil das Backend mit ihnen rechnet (ACTIVE beim Upload, ARCHIVED für ältere Fassungen).
 */
@Service
public class DmsStateService {

	/** Der Bereich der DMS-Status in EntityState.scope */
	public static final String SCOPE = "DMS";

	public static final String ACTIVE = "ACTIVE";
	public static final String ARCHIVED = "ARCHIVED";
	public static final String PENDING_ADMIN = "PENDING_ADMIN";
	public static final String DELETED = "DELETED";

	/**
	 * Das fq jeder User-Abfrage im Kern DMS (User 2026-10-08): Ein User bekommt nur ACTIVE und ARCHIVED, in der Suche,
	 * beim Auflösen, auf der Dokumentseite, in der Versionskette, beim Download und beim Schreibcheck. DELETED und
	 * PENDING_ADMIN sieht nur der Admin im DMS-Manager. Nur der Existenzcheck läuft ohne, die Upload-Maske muss
	 * „gelöscht“ erkennen.
	 */
	public static final String VISIBLE_STATUS_FILTER_QUERY = DmsSolrDocumentFieldNames.STATUS + ":(" + ACTIVE + " OR "
			+ ARCHIVED + ")";

	private final EntityStateService entityStateService;

	public DmsStateService(EntityStateService entityStateService) {
		this.entityStateService = entityStateService;
	}

	@PostConstruct
	void ensureDmsStates() {
		entityStateService.createEntityStateIfMissing(ACTIVE,
				"{\"de\":\"aktiv\",\"en\":\"active\",\"bg\":\"активен\"}", SCOPE, 1);
		entityStateService.createEntityStateIfMissing(ARCHIVED,
				"{\"de\":\"archiviert (ältere Fassung)\",\"en\":\"archived (older version)\",\"bg\":\"архивиран (по-стара версия)\"}",
				SCOPE, 2);
		entityStateService.createEntityStateIfMissing(PENDING_ADMIN,
				"{\"de\":\"wartet auf den Admin\",\"en\":\"pending admin\",\"bg\":\"чака администратор\"}", SCOPE, 3);
		entityStateService.createEntityStateIfMissing(DELETED,
				"{\"de\":\"gelöscht (weich)\",\"en\":\"deleted (soft)\",\"bg\":\"изтрит (меко)\"}", SCOPE, 4);
	}

	/** Die Status, die ein DMS-Dokument tragen darf, in Listenreihenfolge: Bereich DMS und die ohne Bereich */
	public List<EntityState> getDmsStates() {
		return entityStateService.getEntityStatesByScope(SCOPE);
	}

	public boolean isDmsState(String key) {
		return getDmsStates().stream().anyMatch(entityState -> entityState.getKey().equals(key));
	}

}

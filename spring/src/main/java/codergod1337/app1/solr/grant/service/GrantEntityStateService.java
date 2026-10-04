package codergod1337.app1.solr.grant.service;

import codergod1337.app1.system.state.model.EntityState;
import codergod1337.app1.system.state.service.EntityStateService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Die Status einer Freigabe (Dateifreigabe und Feldfreigabe), als EntityState im Bereich GRANT. Beim Start legt dieser
 * Service die drei Status an, falls sie fehlen, mit dreisprachigem Namen; was der Admin danach daran ändert, bleibt.
 * Die keys stehen hier fest, weil das Backend mit ihnen rechnet: Nur ACTIVE zählt beim Lesen, Entzug setzt REVOKED,
 * der Ablauf EXPIRED. Nichts wird gelöscht, eine Freigabe wechselt nur den Status.
 */
@Service
public class GrantEntityStateService {

	/** Der Bereich der Freigabe-Status in EntityState.scope */
	public static final String SCOPE = "GRANT";

	public static final String ACTIVE = "ACTIVE";
	public static final String REVOKED = "REVOKED";
	public static final String EXPIRED = "EXPIRED";

	private final EntityStateService entityStateService;

	public GrantEntityStateService(EntityStateService entityStateService) {
		this.entityStateService = entityStateService;
	}

	@PostConstruct
	void ensureGrantEntityStates() {
		// mehrsprachige Texte immer in de, en und bg
		entityStateService.createEntityStateIfMissing(ACTIVE,
				"{\"de\":\"aktiv\",\"en\":\"active\",\"bg\":\"активна\"}", SCOPE, 1);
		entityStateService.createEntityStateIfMissing(REVOKED,
				"{\"de\":\"entzogen\",\"en\":\"revoked\",\"bg\":\"отнета\"}", SCOPE, 2);
		entityStateService.createEntityStateIfMissing(EXPIRED,
				"{\"de\":\"abgelaufen\",\"en\":\"expired\",\"bg\":\"изтекла\"}", SCOPE, 3);
	}

	/** Die Status, die eine Freigabe tragen darf, in Listenreihenfolge: Bereich GRANT und die ohne Bereich */
	public List<EntityState> getGrantEntityStates() {
		return entityStateService.getEntityStatesByScope(SCOPE);
	}

	public boolean isGrantEntityState(String key) {
		return getGrantEntityStates().stream().anyMatch(entityState -> entityState.getKey().equals(key));
	}

}

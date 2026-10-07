package codergod1337.app1.graph.mail.service;

import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import codergod1337.app1.system.user.model.Users;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Die Gruppe users: Felder des Users, um den es in der Mail geht, z. B. der Empfänger der 2FA-Mail. Wer eine Mail
 * schickt, baut die Werte mit {@link #valuesOf(Users)}.
 */
@Service
public class UsersGraphMailPlaceholderGroup implements GraphMailPlaceholderGroup {

	public static final String EMAIL = "email";
	public static final String USERNAME = "username";
	public static final String VORNAME = "vorname";
	public static final String NACHNAME = "nachname";

	private static final List<String> PLACEHOLDER_NAMES = List.of(EMAIL, USERNAME, VORNAME, NACHNAME);

	@Override
	public String groupKey() {
		return GraphMailTemplateType.PLACEHOLDER_GROUP_USERS;
	}

	@Override
	public List<String> placeholderNames() {
		return PLACEHOLDER_NAMES;
	}

	@Override
	public Map<String, String> sampleValues() {
		return Map.of(EMAIL, "alice@firma.de", USERNAME, "alice", VORNAME, "Alice", NACHNAME, "Beispiel");
	}

	/** Die echten Werte eines Users. Leere Felder werden zu leerem Text, nie zu „null“. */
	public static Map<String, String> valuesOf(Users users) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(EMAIL, textOrEmpty(users.getEmail()));
		values.put(USERNAME, textOrEmpty(users.getUsername()));
		values.put(VORNAME, textOrEmpty(users.getVorname()));
		values.put(NACHNAME, textOrEmpty(users.getNachname()));
		return values;
	}

	private static String textOrEmpty(String text) {
		return text == null ? "" : text;
	}

}

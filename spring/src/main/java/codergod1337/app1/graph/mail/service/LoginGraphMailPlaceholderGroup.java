package codergod1337.app1.graph.mail.service;

import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Die Gruppe login: der Aktivierungslink, das Token zum Kopieren und die Gültigkeit in Minuten. Die Werte baut der
 * LoginService mit {@link #valuesOf(String, String, long)}.
 */
@Service
public class LoginGraphMailPlaceholderGroup implements GraphMailPlaceholderGroup {

	public static final String CONFIRM_LINK = "confirmLink";
	public static final String CONFIRMATION_TOKEN = "confirmationToken";
	public static final String VALIDITY_MINUTES = "validityMinutes";

	private static final List<String> PLACEHOLDER_NAMES = List.of(CONFIRM_LINK, CONFIRMATION_TOKEN, VALIDITY_MINUTES);

	@Override
	public String groupKey() {
		return GraphMailTemplateType.PLACEHOLDER_GROUP_LOGIN;
	}

	@Override
	public List<String> placeholderNames() {
		return PLACEHOLDER_NAMES;
	}

	@Override
	public Map<String, String> sampleValues() {
		return Map.of(CONFIRM_LINK, "https://example.invalid/login/confirm#BEISPIEL", CONFIRMATION_TOKEN,
				"BEISPIEL-TOKEN", VALIDITY_MINUTES, "10");
	}

	public static Map<String, String> valuesOf(String confirmLink, String confirmationToken, long validityMinutes) {
		return Map.of(CONFIRM_LINK, confirmLink, CONFIRMATION_TOKEN, confirmationToken, VALIDITY_MINUTES,
				Long.toString(validityMinutes));
	}

}

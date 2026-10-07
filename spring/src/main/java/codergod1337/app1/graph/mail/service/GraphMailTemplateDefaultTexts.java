package codergod1337.app1.graph.mail.service;

import codergod1337.app1.graph.mail.model.GraphMailTemplate;
import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Die Standardtexte je Vorlagentyp, in de, en und bg. Nur für das Anlegen beim Start, wenn für einen Typ noch keine
 * Vorlage da ist. Danach gilt, was in der Datenbank steht. Der key der angelegten Vorlage ist der Name des Typs.
 */
final class GraphMailTemplateDefaultTexts {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private GraphMailTemplateDefaultTexts() {
	}

	/** Die Vorlage mit Standardtexten für diesen Typ, Position nach der Reihenfolge der Typen. */
	static GraphMailTemplate defaultGraphMailTemplate(GraphMailTemplateType type) {
		return switch (type) {
			case LOGIN_2FA -> new GraphMailTemplate(type.name(), type,
					multilingual("Anmeldung bestätigen (2FA)", "Confirm login (2FA)", "Потвърждаване на влизането (2FA)"),
					multilingual(
							"Geht bei jedem Login an die E-Mail des Users, mit dem Aktivierungslink und dem Token zum Kopieren.",
							"Sent to the user's email on every login, with the activation link and the token to copy.",
							"Изпраща се на имейла на потребителя при всяко влизане, с линка за активиране и кода за копиране."),
					multilingual("Anmeldung bestätigen", "Confirm your login", "Потвърдете влизането"),
					multilingual(LOGIN_2FA_BODY_DE, LOGIN_2FA_BODY_EN, LOGIN_2FA_BODY_BG), type.ordinal() + 1);
		};
	}

	private static final String LOGIN_2FA_BODY_DE = """
			<p>Hallo {users.vorname},</p>
			<p>jemand meldet sich gerade mit deinem Konto {users.email} an. Wenn du das bist, bestätige die Anmeldung:</p>
			<p><a href="{login.confirmLink}">Anmeldung bestätigen</a></p>
			<p>Oder kopiere diesen Code in das Anmeldefenster:</p>
			<p><code>{login.confirmationToken}</code></p>
			<p>Link und Code gelten {login.validityMinutes} Minuten. Danach meldet sich das Fenster, in dem du dein Passwort eingegeben hast, von selbst an.</p>
			<p>Wenn du das nicht warst, ignoriere diese Mail: Ohne Link oder Code passiert nichts. Dein Passwort solltest du dann aber ändern.</p>
			""";

	private static final String LOGIN_2FA_BODY_EN = """
			<p>Hello {users.vorname},</p>
			<p>someone is signing in to your account {users.email} right now. If that is you, confirm the login:</p>
			<p><a href="{login.confirmLink}">Confirm login</a></p>
			<p>Or copy this code into the login window:</p>
			<p><code>{login.confirmationToken}</code></p>
			<p>Link and code are valid for {login.validityMinutes} minutes. After that, the window where you entered your password signs in by itself.</p>
			<p>If this was not you, ignore this email: nothing happens without the link or the code. You should change your password, though.</p>
			""";

	private static final String LOGIN_2FA_BODY_BG = """
			<p>Здравейте, {users.vorname},</p>
			<p>някой в момента влиза във вашия акаунт {users.email}. Ако това сте вие, потвърдете влизането:</p>
			<p><a href="{login.confirmLink}">Потвърждаване на влизането</a></p>
			<p>Или копирайте този код в прозореца за вход:</p>
			<p><code>{login.confirmationToken}</code></p>
			<p>Линкът и кодът са валидни {login.validityMinutes} минути. След това прозорецът, в който сте въвели паролата си, ще влезе автоматично.</p>
			<p>Ако не сте били вие, игнорирайте този имейл: без линка или кода нищо не се случва. Все пак сменете паролата си.</p>
			""";

	/** {"de": …, "en": …, "bg": …} als Text, so wie jedes mehrsprachige Feld gespeichert wird. */
	private static String multilingual(String de, String en, String bg) {
		ObjectNode texts = JSON.createObjectNode();
		texts.put("de", de);
		texts.put("en", en);
		texts.put("bg", bg);
		return JSON.writeValueAsString(texts);
	}

}

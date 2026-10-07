package codergod1337.app1.graph.mail.model;

import java.util.List;

/**
 * Wofür eine Vorlage da ist. Der Typ legt fest, welche Platzhaltergruppen die Vorlage benutzen darf: Beim Senden
 * liefert der Aufrufer genau die Werte dieser Gruppen, der Editor zeigt ihre Namen an, das Muster füllt sie mit
 * Beispielwerten. Eine neue Mailart ist ein neuer Wert hier plus die Standardtexte in GraphMailTemplateDefaultTexts.
 * Je Typ gibt es genau eine Vorlage.
 */
public enum GraphMailTemplateType {

	/** Der Aktivierungslink der 2FA beim Login, an die Login-E-Mail des Users */
	LOGIN_2FA(List.of(GraphMailTemplateType.PLACEHOLDER_GROUP_USERS, GraphMailTemplateType.PLACEHOLDER_GROUP_LOGIN));

	/** Gruppe users: Felder des Users, um den es geht (UsersGraphMailPlaceholderGroup) */
	public static final String PLACEHOLDER_GROUP_USERS = "users";

	/** Gruppe login: Link, Token und Gültigkeit der Anmeldung (LoginGraphMailPlaceholderGroup) */
	public static final String PLACEHOLDER_GROUP_LOGIN = "login";

	private final List<String> placeholderGroupKeys;

	GraphMailTemplateType(List<String> placeholderGroupKeys) {
		this.placeholderGroupKeys = placeholderGroupKeys;
	}

	/** Die Gruppen, deren Platzhalter eine Vorlage dieses Typs benutzen darf, in Anzeigereihenfolge */
	public List<String> getPlaceholderGroupKeys() {
		return placeholderGroupKeys;
	}

}

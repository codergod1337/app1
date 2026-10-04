package codergod1337.app1.solr.grant.model;

import java.util.List;
import java.util.Map;

/**
 * Die Vorschau einer Dateifreigabe: die Treffer der Bedingungen, die der Freigebende sieht und ändern darf,
 * seitenweise, damit er sieht, was er freigibt. grantAllowed sagt, ob „Freigeben“ erlaubt ist: mindestens eine
 * Bedingung, mindestens ein Treffer und weniger als maxDocumentsPerGrant Treffer. Die lesbaren Feldnamen sind die des
 * Freigebenden in diesem Kern, danach baut die Maske ihre Spalten.
 */
public record GrantableDocumentsPreviewPage(long totalFound, int maxDocumentsPerGrant, boolean grantAllowed, int page,
		int size, List<Map<String, Object>> documents, List<String> readableFieldAndHookNamesOfCoreForCurrentUser) {
}

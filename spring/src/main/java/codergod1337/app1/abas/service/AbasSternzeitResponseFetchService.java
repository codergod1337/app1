package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasSternzeit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Holt die Antwort von ABAS zu einer Sternzeit: {@code GET obj/data/byId/{sternzeit}?headFields=…[&tableFields=…]},
 * einmal geparst. Ohne Feldnamen alle Kopffelder ({@code headFields=*}), 1 bis 3000 KB; mit Feldnamen nur die
 * genannten, dann wird die Antwort klein (User 2026-10-10). Die Antwort ist das rohe JSON von ABAS mit
 * {@code content.data.meta}, {@code head.fields[]} und {@code table[]}; was daraus wird, entscheidet der Abnehmer: der
 * Worker heute den Objekttyp, der Admin das Popup und der Mapping-Tab, später das Mapping. Kein @PreAuthorize: Die
 * Worker haben keinen Security-Context, die Admin-Controller liegen unter /admin.
 */
@Service
public class AbasSternzeitResponseFetchService {

	/** Alle Kopffelder: der Wert von headFields, wenn keine Feldnamen genannt sind */
	private static final String ALL_FIELDS = "*";

	private final AbasErpRestClientService abasErpRestClientService;

	public AbasSternzeitResponseFetchService(AbasErpRestClientService abasErpRestClientService) {
		this.abasErpRestClientService = abasErpRestClientService;
	}

	/**
	 * @param sternzeit       die Kennung in Klammerform, sonst 400
	 * @param headFieldNames  die Kopffelder, die ABAS liefern soll; null oder leer heißt alle
	 * @param tableFieldNames die Tabellenfelder; null oder leer heißt, der Parameter geht nicht mit und ABAS liefert
	 *                        wie ohne Angabe
	 * @return die Antwort; 404 wenn ABAS die Kennung nicht kennt, 503 wenn ABAS nicht antwortet
	 */
	public JsonNode fetchAbasResponseBySternzeit(String sternzeit, List<String> headFieldNames,
			List<String> tableFieldNames) {
		if (!AbasSternzeit.isValidSternzeit(sternzeit)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sternzeit fehlt oder hat nicht die Klammerform");
		}
		UriComponentsBuilder uri = abasErpRestClientService.buildUri("obj", "data", "byId", sternzeit)
				.queryParam("headFields", joinFieldNames(headFieldNames, ALL_FIELDS));
		String tableFields = joinFieldNames(tableFieldNames, null);
		if (tableFields != null) {
			uri.queryParam("tableFields", tableFields);
		}
		return abasErpRestClientService.exchange(HttpMethod.GET, uri.build().toUri(), null, "Objekt " + sternzeit);
	}

	/** Feldnamen getrimmt und mit Komma verbunden, wie ABAS sie in headFields und tableFields erwartet; ohne Namen der Rückfall */
	private static String joinFieldNames(List<String> fieldNames, String fallback) {
		if (fieldNames == null) {
			return fallback;
		}
		String joined = fieldNames.stream().filter(Objects::nonNull).map(String::trim).filter(name -> !name.isEmpty())
				.collect(Collectors.joining(","));
		return joined.isEmpty() ? fallback : joined;
	}

}

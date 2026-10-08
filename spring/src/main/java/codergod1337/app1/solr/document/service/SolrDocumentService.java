package codergod1337.app1.solr.document.service;

import codergod1337.app1.solr.client.SolrClientRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die rohe Solr-Schicht ohne Fachwissen und ohne Rechteprüfung: eine fertige Abfrage ausführen, ein Dokument voll
 * schreiben, per Atomic Update ändern (eins oder viele). In jedem Kern dieselben Wege. Das Dokument ist eine Map, kein
 * DTO: die Feldnamen wie in Solr, Zeitpunkte als Instant, multiValued als Liste.
 *
 * Wer diese Methoden ruft, steht im Register docs/backend/solr-zugriff.md und sonst niemand (Vorgabe 2026-10-07): je
 * Kern seine drei Lese-Services und sein Schreibservice im Fachpaket, die ihre Abfragen selbst bauen und die Rechte
 * selbst prüfen. Ein hartes Löschen gibt es nicht, gelöscht wird weich über den Status des Kerns.
 *
 * Nach jedem Schreiben ein Soft-Commit: Das Dokument ist sofort sichtbar, dauerhaft wird es mit dem Hard-Commit des
 * Kerns (solrconfig.xml). Ohne den Soft-Commit sähe der Aufrufer sein eigenes Dokument erst nach bis zu 5 s.
 *
 * _version_ ist Solrs eigener Zähler je Dokument, Solr setzt ihn bei jedem Schreiben selbst. Beim Lesen bleibt er im
 * Dokument, beim Schreiben wird er nie mitgeschickt (Vorgabe 2026-10-07): keine Prüfung, kein 409, der Letzte gewinnt.
 */
@Service
public class SolrDocumentService {

	public static final String ID_FIELD_NAME = "id";
	public static final String VERSION_FIELD_NAME = "_version_";

	private static final Logger log = LoggerFactory.getLogger(SolrDocumentService.class);

	private final SolrClientRegistry solrClientRegistry;

	public SolrDocumentService(SolrClientRegistry solrClientRegistry) {
		this.solrClientRegistry = solrClientRegistry;
	}

	/**
	 * Das Ergebnis einer Abfrage: wie viele Dokumente insgesamt treffen, die Dokumente der angeforderten Seite und, wenn
	 * die Abfrage Highlighting verlangt hat, je Dokument-id und Feld die Ausschnitte von Solr, sonst leer.
	 */
	public record SolrQueriedDocumentsPage(long totalFound, List<Map<String, Object>> documents,
			Map<String, Map<String, List<String>>> highlightingByDocumentId) {
	}

	/**
	 * Eine Abfrage, wie der Lese-Service des Kerns sie gebaut hat (q, fq, fl, start, rows, sort, hl). Keine Prüfung hier.
	 * Lehnt Solr die Abfrage ab (400, z. B. Sortieren nach einem Feld ohne docValues), ist das ein Fehler des Aufrufers
	 * und kommt als 400 mit der Meldung von Solr zurück.
	 */
	public SolrQueriedDocumentsPage queryDocumentsPage(String coreKey, SolrQuery query) {
		try {
			// per POST wie in der Vorlage: eine qf mit allen Hooks und Zwillingen plus Highlighting sprengt sonst die URL
			QueryResponse response = client(coreKey).query(query, SolrRequest.METHOD.POST);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				documents.add(toMap(document));
			}
			Map<String, Map<String, List<String>>> highlighting = response.getHighlighting() == null ? Map.of()
					: response.getHighlighting();
			return new SolrQueriedDocumentsPage(response.getResults().getNumFound(), documents, highlighting);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			// die ganze Abfrage ins Log: Welches Feld oder welcher Parameter Solr nicht passt, steht sonst nirgends
			log.warn("Solr lehnt die Abfrage ab (Kern {}, HTTP {}): {} | Abfrage: {}", coreKey, e.code(), e.getMessage(),
					query);
			if (e.code() == HttpStatus.BAD_REQUEST.value()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Solr lehnt die Abfrage ab (Kern " + coreKey + "): " + e.getMessage(), e);
			}
			throw rejected(coreKey, e);
		}
	}

	/**
	 * Volles Schreiben eines neuen Dokuments. Ob die id frei ist, hat der Schreibservice des Kerns vorher geprüft: Solr
	 * würde ein vorhandenes Dokument stillschweigend ersetzen und jedes nicht geschickte Feld löschen. null-Werte fallen
	 * weg, ebenso ein versehentlich mitgegebenes _version_, sonst lehnt Solr das Anlegen ab.
	 */
	public void writeNewDocument(String coreKey, Map<String, Object> document) {
		SolrInputDocument input = new SolrInputDocument();
		for (Map.Entry<String, Object> field : document.entrySet()) {
			if (field.getValue() != null && !VERSION_FIELD_NAME.equals(field.getKey())) {
				input.addField(field.getKey(), toSolrValue(field.getValue()));
			}
		}
		try {
			SolrClient client = client(coreKey);
			client.add(input);
			client.commit(false, false, true);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			throw rejected(coreKey, e);
		}
	}

	/**
	 * Atomic Update eines Dokuments: je Feld ein Modifier und sein Wert, z. B. {"status": {"set": "ARCHIVED"},
	 * "artikelNummer": {"add-distinct": ["A1", "A2"]}, "downloadCount": {"inc": 1}}. Solr setzt das Dokument aus seinen
	 * stored fields neu zusammen, alles andere bleibt. Gibt es die id nicht, legt Solr ein Dokument nur mit diesen
	 * Feldern an, deshalb prüft der Schreibservice des Kerns vorher.
	 */
	public void atomicUpdateDocument(String coreKey, String id, Map<String, Map<String, Object>> modifiersByFieldName) {
		SolrInputDocument input = inputWithModifiers(id, modifiersByFieldName);
		try {
			SolrClient client = client(coreKey);
			client.add(input);
			client.commit(false, false, true);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			throw rejected(coreKey, e);
		}
	}

	/**
	 * Dasselbe Atomic Update für viele Dokumente auf einmal, ein Commit am Ende: z. B. die guid einer Dateifreigabe in
	 * jeden Treffer schreiben. Die ids hat der Schreibservice des Kerns vorher gegen writeAcl geprüft.
	 */
	public void atomicUpdateManyDocuments(String coreKey, List<String> ids,
			Map<String, Map<String, Object>> modifiersByFieldName) {
		if (ids.isEmpty()) {
			return;
		}
		List<SolrInputDocument> inputs = new ArrayList<>(ids.size());
		for (String id : ids) {
			inputs.add(inputWithModifiers(id, modifiersByFieldName));
		}
		try {
			SolrClient client = client(coreKey);
			client.add(inputs);
			client.commit(false, false, true);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			throw rejected(coreKey, e);
		}
	}

	private static SolrInputDocument inputWithModifiers(String id, Map<String, Map<String, Object>> modifiersByFieldName) {
		SolrInputDocument input = new SolrInputDocument();
		input.addField(ID_FIELD_NAME, id);
		for (Map.Entry<String, Map<String, Object>> change : modifiersByFieldName.entrySet()) {
			Map<String, Object> modifier = new LinkedHashMap<>();
			change.getValue().forEach((operation, value) -> modifier.put(operation, toSolrValue(value)));
			input.addField(change.getKey(), modifier);
		}
		return input;
	}

	private SolrClient client(String coreKey) {
		return solrClientRegistry.clientFor(coreKey);
	}

	/** Solr antwortet nicht: 503, der Aufrufer darf es später noch einmal versuchen */
	private static ResponseStatusException unavailable(String coreKey, Exception e) {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + coreKey + "): " + e.getMessage(), e);
	}

	/** Solr lehnt ab, z. B. ein Feld, das es im Kern nicht gibt: ein Fehler bei uns, keiner des Aufrufers */
	private static ResponseStatusException rejected(String coreKey, SolrException e) {
		return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
				"Solr lehnt ab (Kern " + coreKey + "): " + e.getMessage(), e);
	}

	/** Instant wird Date, Listen werden elementweise umgesetzt, alles andere nimmt SolrJ, wie es ist */
	private static Object toSolrValue(Object value) {
		if (value instanceof Instant instant) {
			return Date.from(instant);
		}
		if (value instanceof Collection<?> values) {
			List<Object> converted = new ArrayList<>(values.size());
			for (Object element : values) {
				converted.add(toSolrValue(element));
			}
			return converted;
		}
		return value;
	}

	/** Date wird Instant, multiValued kommt als Liste, einwertig als Wert */
	private static Map<String, Object> toMap(SolrDocument document) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (String name : document.getFieldNames()) {
			map.put(name, fromSolrValue(document.get(name)));
		}
		return map;
	}

	private static Object fromSolrValue(Object value) {
		if (value instanceof Date date) {
			return date.toInstant();
		}
		if (value instanceof Collection<?> values) {
			List<Object> converted = new ArrayList<>(values.size());
			for (Object element : values) {
				converted.add(fromSolrValue(element));
			}
			return converted;
		}
		return value;
	}

}

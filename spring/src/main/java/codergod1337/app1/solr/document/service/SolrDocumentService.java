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
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Solr-Schicht ohne Fachwissen: ein Dokument holen, voll schreiben, per Atomic Update ändern (eins oder viele),
 * löschen, und eine fertige Abfrage ausführen. In jedem Kern dieselben Wege. Das Dokument ist eine Map, kein DTO: die Feldnamen wie in Solr, Zeitpunkte als Instant,
 * multiValued als Liste. Was ein Feld bedeutet und wer es ändern darf, weiß der Service des Kerns (z. B. das DMS),
 * der vorher mit dem SolrDocumentChecker prüft.
 *
 * Nach jedem Schreiben ein Soft-Commit: Das Dokument ist sofort sichtbar, dauerhaft wird es mit dem Hard-Commit des
 * Kerns (solrconfig.xml, alle 15 s). Ohne den Soft-Commit sähe der Aufrufer sein eigenes Dokument erst nach bis zu 5 s.
 *
 * _version_ bleibt beim Lesen im Dokument (Optimistic Concurrency, später als Bedingung), beim Vollschreiben wird es
 * gestrichen, sonst lehnt Solr das Dokument wegen einer alten Version ab.
 */
@Service
public class SolrDocumentService {

	public static final String ID_FIELD_NAME = "id";
	public static final String VERSION_FIELD_NAME = "_version_";

	private final SolrClientRegistry solrClientRegistry;

	public SolrDocumentService(SolrClientRegistry solrClientRegistry) {
		this.solrClientRegistry = solrClientRegistry;
	}

	/** Das Dokument mit dieser id als Map, null wenn es keins gibt. */
	public Map<String, Object> get(String coreKey, String id) {
		try {
			SolrDocument document = client(coreKey).getById(id);
			return document == null ? null : toMap(document);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			throw rejected(coreKey, e);
		}
	}

	public boolean exists(String coreKey, String id) {
		return get(coreKey, id) != null;
	}

	/**
	 * Volles Schreiben: ein neues Dokument oder ein komplettes Ersetzen. Alles, was nicht in der Map steht, ist danach
	 * weg, deshalb nur für neue Dokumente oder nach einem vollständigen Lesen. null-Werte und _version_ fallen weg.
	 */
	public void write(String coreKey, Map<String, Object> document) {
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
	 * Atomic Update: je Feld ein Modifier und sein Wert, z. B. {"status": {"set": "ARCHIVED"}, "artikelNummer":
	 * {"add-distinct": ["A1", "A2"]}, "downloadCount": {"inc": 1}}. Solr setzt das Dokument aus seinen stored fields
	 * neu zusammen, alles andere bleibt. Gibt es die id nicht, legt Solr ein Dokument nur mit diesen Feldern an,
	 * deshalb vorher mit exists prüfen.
	 */
	public void atomicUpdate(String coreKey, String id, Map<String, Map<String, Object>> changes) {
		SolrInputDocument input = new SolrInputDocument();
		input.addField(ID_FIELD_NAME, id);
		for (Map.Entry<String, Map<String, Object>> change : changes.entrySet()) {
			Map<String, Object> modifier = new LinkedHashMap<>();
			change.getValue().forEach((operation, value) -> modifier.put(operation, toSolrValue(value)));
			input.addField(change.getKey(), modifier);
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
	 * Dasselbe Atomic Update für viele Dokumente auf einmal, ein Commit am Ende: z. B. die guid einer Dateifreigabe in
	 * jeden Treffer schreiben. Gibt es eine id nicht, legt Solr ein Dokument nur mit diesen Feldern an, deshalb nur mit
	 * ids aus einer Abfrage von eben.
	 */
	public void atomicUpdateManyDocuments(String coreKey, List<String> ids, Map<String, Map<String, Object>> changes) {
		if (ids.isEmpty()) {
			return;
		}
		List<SolrInputDocument> inputs = new ArrayList<>(ids.size());
		for (String id : ids) {
			SolrInputDocument input = new SolrInputDocument();
			input.addField(ID_FIELD_NAME, id);
			for (Map.Entry<String, Map<String, Object>> change : changes.entrySet()) {
				Map<String, Object> modifier = new LinkedHashMap<>();
				change.getValue().forEach((operation, value) -> modifier.put(operation, toSolrValue(value)));
				input.addField(change.getKey(), modifier);
			}
			inputs.add(input);
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

	/** Das Ergebnis einer Abfrage: wie viele Dokumente insgesamt treffen und die Dokumente der angeforderten Seite */
	public record SolrQueriedDocumentsPage(long totalFound, List<Map<String, Object>> documents) {
	}

	/**
	 * Eine Abfrage, wie der Aufrufer sie gebaut hat (q, fq, fl, start, rows, sort). Keine Prüfung hier: Wer die
	 * Prüfungen des Users einbaut, ist der SolrExactFieldValueResolveService, nicht diese Schicht. Lehnt Solr die
	 * Abfrage ab (400, z. B. Sortieren nach einem Feld ohne docValues), ist das ein Fehler des Aufrufers und kommt als
	 * 400 mit der Meldung von Solr zurück.
	 */
	public SolrQueriedDocumentsPage queryDocumentsPage(String coreKey, SolrQuery query) {
		try {
			QueryResponse response = client(coreKey).query(query);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				documents.add(toMap(document));
			}
			return new SolrQueriedDocumentsPage(response.getResults().getNumFound(), documents);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			if (e.code() == HttpStatus.BAD_REQUEST.value()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Solr lehnt die Abfrage ab (Kern " + coreKey + "): " + e.getMessage(), e);
			}
			throw rejected(coreKey, e);
		}
	}

	/** Löscht das Dokument. Gab es keins, gilt das als Erfolg. */
	public void delete(String coreKey, String id) {
		try {
			SolrClient client = client(coreKey);
			client.deleteById(id);
			client.commit(false, false, true);
		} catch (SolrServerException | IOException e) {
			throw unavailable(coreKey, e);
		} catch (SolrException e) {
			throw rejected(coreKey, e);
		}
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

package codergod1337.app1.solr.kontakte.service;

import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.document.service.SolrMirrorCoreDocumentWriteService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.kontakte.model.KontakteResolvedDocumentsPage;
import codergod1337.app1.solr.kontakte.model.KontakteSearchHit;
import codergod1337.app1.solr.kontakte.model.KontakteSearchHitReason;
import codergod1337.app1.solr.kontakte.model.KontakteSearchedDocumentsPage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lesen und Schreiben im Kern KONTAKTE (User 2026-10-13), dem Spiegel der ABAS-19er: Firmen und Ansprechpartner, ein
 * Dokument je 19er, id ist seine Sternzeit. ABAS ist die Quelle der Wahrheit, bei uns wird nichts gepflegt.
 *
 * Schreiben: nur das Dokument aus den gemappten Werten der beiden Mappings auf 19:0 (Firma, Ansprechpartner), über die
 * Mechanik aller Spiegelkerne im SolrMirrorCoreDocumentWriteService, Vorgang KONTAKTE_WRITE_FROM_MAPPING. Vorerst nur
 * mit ADMIN; sobald der Worker schreibt, läuft er als System-User durch dieselbe Methode.
 *
 * Suchen (seit 2026-10-13): die Suchmaske über alle Kerne, nach dem Ablauf des Kerns ARTIKEL, Tür KONTAKTE_SUCHE.
 *
 * Lesen: Auflösen nach exakten Werten, nicht Suchen. So findet man die Firma nach kundenNummer oder lieferantenNummer
 * mit kontaktorig Adresse, ihre Ansprechpartner mit kontaktorig Kontakt, einen Ansprechpartner nach E-Mail ohne
 * Rücksicht auf Groß- und Kleinschreibung, die Firma eines ABAS-Kunden nach kundeKontaktreferenz. Die Tür ist die AR
 * KONTAKTE, die Feldgrenze die Lese-ARs der Felder. Rollen, fl, Debuffs und Bedingungen liegen im
 * SolrDocumentKontakteUtilsService.
 */
@Service
public class SolrDocumentKontakteService {

	public static final String CORE_KEY = "KONTAKTE";

	/** Das Ziel der Mappings, deren Dokumente in diesen Kern gehören */
	public static final String TARGET_KEY = "SOLR_KONTAKTE";

	/** Die Sync-Felder des Kerns, mit Vorsilbe, weil der Artikelkern syncDatum und Co. schon hat */
	public static final String SYNC_DATE_FIELD_NAME = "kontaktSyncDatum";
	public static final String SYNC_STATUS_FIELD_NAME = "kontaktSyncStatus";
	public static final String SYNC_ERROR_FIELD_NAME = "kontaktSyncFehler";

	/** Der Vorgang im Log, Register docs/backend/solr-zugriff.md */
	private static final String WRITE_PROCESS_NAME = "KONTAKTE_WRITE_FROM_MAPPING";

	/** Firma oder Ansprechpartner: kontaktorig trägt den Text des 19ers, „Adresse“ oder „Kontakt“ */
	public static final String CONTACT_KIND_FIELD_NAME = "kontaktorig";

	/** Der Wert von kontaktorig an einer Firma */
	public static final String CONTACT_KIND_COMPANY = "Adresse";

	/**
	 * Firma vor ihren Ansprechpartnern (nach der Vorlage): Nummer und Name tragen Firma und Personen gleichermaßen, erst
	 * dieser Zusatz zum Score holt die Firma nach vorn
	 */
	private static final String COMPANY_FIRST_BOOST_QUERY = CONTACT_KIND_FIELD_NAME + ":" + CONTACT_KIND_COMPANY + "^20";

	/** Die Felder der Trefferzeile, dazu kommen die durchsuchten */
	private static final List<String> DISPLAY_FIELD_NAMES = List.of("id", CONTACT_KIND_FIELD_NAME, "kontaktName",
			"kundenNummer", "lieferantenNummer", "nort", "funktion", "tele", "email", "sternzeit", SYNC_STATUS_FIELD_NAME);

	/** Der Name einer Firma, wie ihn jeder Kontakt trägt */
	private static final String NAME_FIELD_NAME = "kontaktName";

	/** Die beiden Nummern, über die ein Ansprechpartner zu seiner Firma gehört: die Hooks der eigenen Nummern */
	private static final String CUSTOMER_NUMBER_FIELD_NAME = "kundenNummer";
	private static final String SUPPLIER_NUMBER_FIELD_NAME = "lieferantenNummer";

	/**
	 * Kein Feld des Kerns, nur in der Antwort der Suche (User 2026-10-13, nach firmenName der Vorlage): an jedem
	 * Ansprechpartner der Name seiner Firma, zur Lesezeit über die Nummern geholt, damit er nie veraltet. Die Maske zeigt
	 * ihn in der Trefferzeile und als Zusatz der Nummern-Angebote.
	 */
	public static final String COMPANY_NAME_OF_CONTACT_PERSON_FIELD_NAME = "kontaktFirmenName";

	/** Höchstens so viele Firmen holt der Sammelgriff für die Firmennamen einer Suche */
	private static final int MAX_COMPANIES_FOR_NAMES = 500;

	private final SolrClientRegistry solrClientRegistry;
	private final SolrFieldService solrFieldService;
	private final SolrMirrorCoreDocumentWriteService solrMirrorCoreDocumentWriteService;
	private final SolrDocumentKontakteUtilsService utils;

	/** Treffer je Seite beim Auflösen, app1.solr.resolve.page-size */
	private final int resolvePageSize;

	/** Treffer insgesamt beim Auflösen, app1.solr.resolve.max-results; eine Seite darüber ist leer */
	private final int resolveMaxResults;

	/** Treffer der Suche, app1.solr.search.hits: nur die besten, keine Seiten */
	private final int searchHits;

	public SolrDocumentKontakteService(SolrClientRegistry solrClientRegistry, SolrFieldService solrFieldService,
			SolrMirrorCoreDocumentWriteService solrMirrorCoreDocumentWriteService, SolrDocumentKontakteUtilsService utils,
			@Value("${app1.solr.resolve.page-size}") int resolvePageSize,
			@Value("${app1.solr.resolve.max-results}") int resolveMaxResults,
			@Value("${app1.solr.search.hits}") int searchHits) {
		this.solrClientRegistry = solrClientRegistry;
		this.solrFieldService = solrFieldService;
		this.solrMirrorCoreDocumentWriteService = solrMirrorCoreDocumentWriteService;
		this.utils = utils;
		this.resolvePageSize = resolvePageSize;
		this.resolveMaxResults = resolveMaxResults;
		this.searchHits = searchHits;
	}

	// ===== Lesen =====

	/**
	 * Ein Kontakt über seine 19er-Sternzeit, mit denselben Rechten wie das Auflösen; null, wenn es ihn nicht gibt oder
	 * der User ihn nicht sehen darf. Echtzeit-Lesen (/get) statt Suche: sieht auch einen gerade geschriebenen Kontakt, den
	 * Solr noch nicht aufgefrischt hat (User 2026-10-14, Auffrischen alle 10 s).
	 */
	public Map<String, Object> getById(String sternzeit) {
		utils.requireAccessRole();
		ModifiableSolrParams params = new ModifiableSolrParams();
		applyUserReadRules(params, Map.of());
		try {
			SolrDocument document = client().getById(sternzeit, params);
			return document == null ? null : new LinkedHashMap<>(document);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	/** Auflösen und getById rufen das: fl und Debuffs nach den Rechten des Users, dazu die Bedingungen des Clients */
	private void applyUserReadRules(ModifiableSolrParams params, Map<String, List<String>> valuesByFieldName) {
		if (utils.isEveryFieldReadable()) {
			// ADMIN, SOLR_USERS_VIEW_ALL, KONTAKTE_SUCHE: alles, kein fl, keine Debuffs; der Spiegelkern hat weder readAcl noch Status
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, null, List.of());
		} else {
			List<String> readableFieldNames = utils.buildUserSolrResponseFieldList(params);
			List<String> debuffedFieldNames = utils.buildUserDebuffsForSolrRequest(params);
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, readableFieldNames, debuffedFieldNames);
		}
	}

	/**
	 * Auflösen nach exakten Feldwerten, mehrere Felder UND: keine Suchtreffer mit Score, Seiten ab 0 mit der Größe aus
	 * der YAML, aufsteigend nach id. Tür ist die AR KONTAKTE. Eine Bedingung auf ein Feld, das der User nicht lesen
	 * darf, ist 403.
	 */
	public KontakteResolvedDocumentsPage resolveKontakteDocuments(Map<String, String> exactValueByFieldName, int page) {
		utils.requireAccessRole();
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist kleiner als 0");
		}
		if (exactValueByFieldName.isEmpty()) {
			// Alles ausgeben ist Sache der Suche, nicht des Auflösens
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auflösen braucht mindestens eine Bedingung");
		}
		Map<String, List<String>> valuesByFieldName = new LinkedHashMap<>();
		for (Map.Entry<String, String> condition : exactValueByFieldName.entrySet()) {
			valuesByFieldName.put(condition.getKey(), List.of(condition.getValue()));
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		// feste Reihenfolge, sonst sind die Seiten nicht stabil: die Sternzeit ist der uniqueKey
		params.set("sort", "id asc");
		int start = page * resolvePageSize;
		int rows = Math.min(resolvePageSize, resolveMaxResults - start);
		if (rows <= 0) {
			return new KontakteResolvedDocumentsPage(0, page, resolvePageSize, List.of());
		}
		params.set("start", start);
		params.set("rows", rows);
		applyUserReadRules(params, valuesByFieldName);
		try {
			QueryResponse response = client().query(params);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				documents.add(new LinkedHashMap<>(document));
			}
			return new KontakteResolvedDocumentsPage(response.getResults().getNumFound(), page, resolvePageSize,
					documents);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	/**
	 * Wird aufgerufen, wenn ein User im Suchmodul tippt oder Chips setzt: die Treffer mit Score und Gründen für die
	 * Trefferbox, eine Seite ab 0 mit app1.solr.search.hits Treffern. Derselbe Ablauf wie die Suche im Kern ARTIKEL, der
	 * Kern hat weder readAcl noch Status. Tür ist KONTAKTE_SUCHE oder ADMIN, beide geben alle Felder und Hooks frei.
	 * Dazu nach der Vorlage: die Firma steht vor ihren Ansprechpartnern, mit Text über einen Zusatz zum Score, ohne Text
	 * über die Sortierung nach kontaktorig („Adresse“ vor „Kontakt“).
	 *
	 * E-Mail-Modus (User 2026-10-13, nach der Vorlage): Ist der Suchtext genau eine Adresse (ein @ in der Mitte, kein
	 * Leerraum), trifft nur diese Adresse in den Feldern vom Typ EMAIL, ohne Rücksicht auf Groß- und Kleinschreibung, über
	 * denselben regulären Ausdruck wie das Auflösen; keine Wortsuche links und rechts vom @. Die Maske setzt die Adresse
	 * über die Vervollständigung ein. Eine Adresse muss nicht eindeutig sein, es kommen alle Kontakte mit ihr.
	 *
	 * Jeder Ansprechpartner bekommt den Namen seiner Firma (COMPANY_NAME_OF_CONTACT_PERSON_FIELD_NAME), ein Sammelgriff je
	 * Suche über die Nummern.
	 */
	public KontakteSearchedDocumentsPage searchKontakteDocuments(String searchText,
			Map<String, List<String>> exactFilterValuesByFieldName, int page,
			boolean allWordsRequired) {
		String text = searchText == null ? "" : searchText.trim();
		if (text.isEmpty() && exactFilterValuesByFieldName.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weder Suchtext noch Filter");
		}
		long start = (long) page * searchHits;
		if (page < 0 || start > Integer.MAX_VALUE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page muss zwischen 0 und " + Integer.MAX_VALUE / searchHits + " liegen");
		}
		utils.requireSearchAccessRole();
		ModifiableSolrParams params = new ModifiableSolrParams();
		// die durchsuchten Felder des Kerns, Gewicht ab 0, in Listenreihenfolge: daraus qf, fl und die Gründe
		List<SolrField> searchedFields = new ArrayList<>();
		List<String> qf = new ArrayList<>();
		List<String> highlightedFieldNames = new ArrayList<>();
		// die Felder vom Typ EMAIL, auch ohne Suchgewicht: auf ihnen sucht der E-Mail-Modus
		List<String> emailFieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(CORE_KEY)) {
			if (solrField.getType() == SolrFieldType.EMAIL) {
				emailFieldNames.add(solrField.getName());
			}
			if (solrField.getSearchWeight() < 0) {
				continue;
			}
			searchedFields.add(solrField);
			// suggest und Hooks über den Zwilling <name>Suggested, der Wortanfänge kennt; Texttypen und string direkt
			String queriedName = solrField.isSuggest() ? solrField.getName() + "Suggested" : solrField.getName();
			// ergibt z. B. kundenNummerSuggested^10 oder kontaktName^4
			qf.add(queriedName + "^" + solrField.getSearchWeight());
			// Solr markiert Treffer nur in Texttypen, der Zwilling ist nicht stored
			if (solrField.getType().isText()) {
				highlightedFieldNames.add(solrField.getName());
			}
		}
		if (text.isEmpty()) {
			// nur Chips: alles trifft, die fq schränken ein; Firmen zuerst, denn „Adresse“ steht vor „Kontakt“
			params.set("q", "*:*");
			params.set("sort", CONTACT_KIND_FIELD_NAME + " asc, id asc");
		} else if (isEmailAddress(text) && !emailFieldNames.isEmpty()) {
			// E-Mail-Modus: nur genau diese Adresse, z. B. email:(/[dD][eE].../), Firmen zuerst
			String exactAddress = SolrDocumentKontakteUtilsService.caseInsensitiveExactRegex(text);
			List<String> clauses = new ArrayList<>();
			for (String emailFieldName : emailFieldNames) {
				clauses.add(emailFieldName + ":(" + exactAddress + ")");
			}
			params.set("q", String.join(" OR ", clauses));
			params.set("sort", CONTACT_KIND_FIELD_NAME + " asc, id asc");
		} else {
			if (qf.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Feld im Kern " + CORE_KEY + " hat ein Suchgewicht");
			}
			// edismax: jedes Wort gegen jedes Feld der qf mit dessen Gewicht; + und - vor einem Wort versteht es selbst
			params.set("defType", "edismax");
			params.set("q", text);
			params.set("qf", String.join(" ", qf));
			// alle Wörter (Schalter über dem Suchfeld, User 2026-10-14): jedes Wort muss in irgendeinem Feld stehen, ein - schließt
			// weiter aus; sonst reicht eines, mehr Treffer heben den Treffer nach oben
			params.set("q.op", allWordsRequired ? "AND" : "OR");
			params.set("mm", allWordsRequired ? "100%" : "1");
			// sow: erst an Leerzeichen zerlegen, dann jedes Wort einzeln durch jeden Feld-Analyzer (User 2026-10-09)
			params.set("sow", "true");
			// die Firma vor ihren Ansprechpartnern: ein Zusatz zum Score, kein Filter
			params.set("bq", COMPANY_FIRST_BOOST_QUERY);
			if (!highlightedFieldNames.isEmpty()) {
				params.set("hl", true);
				params.set("hl.fl", String.join(",", highlightedFieldNames));
				params.set("hl.method", "unified");
				// jedes Suchwort in jedem Feld markieren, in dem es steht (User 2026-10-14); sonst markiert Solr ein
				// Feld nur, wenn alle +Wörter in genau diesem Feld stehen
				params.set("hl.weightMatches", false);
				params.set("hl.tag.pre", "\u0002");
				params.set("hl.tag.post", "\u0003");
			}
			// das Treffergewicht entscheidet, die Sternzeit nur bei gleichem Score
			params.set("sort", "score desc, id asc");
		}
		// die Seite: „weitere laden“ in der Maske (User 2026-10-14)
		params.set("start", (int) start);
		params.set("rows", searchHits);
		Set<String> fieldList = new LinkedHashSet<>(DISPLAY_FIELD_NAMES);
		for (SolrField solrField : searchedFields) {
			if (solrField.getType() != SolrFieldType.TEXT_SUCHE) {
				fieldList.add(solrField.getName());
			}
		}
		params.set("fl", String.join(",", fieldList));
		// die Chips des Clients: jedes Feld erlaubt, denn KONTAKTE_SUCHE gibt alle Felder und Hooks frei, kein Debuff
		utils.buildClientConditionsForSolrRequest(params, exactFilterValuesByFieldName, null, List.of());
		try {
			QueryResponse response = client().query(params);
			Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
			List<String> searchWords = utils.searchWordsOf(text);
			List<KontakteSearchHit> hits = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				Map<String, Object> fieldValues = new LinkedHashMap<>(document);
				Map<String, List<String>> snippetsByFieldName = highlighting == null ? null
						: highlighting.get(String.valueOf(fieldValues.get("id")));
				List<KontakteSearchHitReason> reasons = new ArrayList<>();
				for (SolrField solrField : searchedFields) {
					String fieldName = solrField.getName();
					if (solrField.getType().isText()) {
						List<String> snippets = snippetsByFieldName == null ? null : snippetsByFieldName.get(fieldName);
						if (snippets != null && !snippets.isEmpty()) {
							String snippet = solrField.getType() == SolrFieldType.TEXT_SUCHE ? null : snippets.get(0);
							reasons.add(new KontakteSearchHitReason(fieldName, snippet));
						}
						continue;
					}
					// string, suggest, Hooks: Solr markiert dort nichts, der Vergleich läuft über die gelieferten Werte
					String snippet = utils.startingWithSearchWord(fieldValues.get(fieldName), searchWords);
					if (snippet != null) {
						reasons.add(new KontakteSearchHitReason(fieldName, snippet));
					}
				}
				hits.add(new KontakteSearchHit(fieldValues, reasons));
			}
			addCompanyNamesToContactPersons(hits);
			return new KontakteSearchedDocumentsPage(response.getResults().getNumFound(), page, searchHits, hits);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	/** Genau eine Adresse: ein @, nicht am Rand, kein Leerraum; nach siehtAusWieEmail der Vorlage */
	private static boolean isEmailAddress(String text) {
		int at = text.indexOf('@');
		return at > 0 && at == text.lastIndexOf('@') && at < text.length() - 1
				&& text.chars().noneMatch(Character::isWhitespace);
	}

	/**
	 * Jedem Ansprechpartner unter den Treffern den Namen seiner Firma geben, nach reichereFirmenNamenAn der Vorlage: ein
	 * Sammelgriff über alle Kunden- und Lieferantennummern der Ansprechpartner, Firmen sind die mit kontaktorig
	 * „Adresse“. Erst die Kundennummer, dann die Lieferantennummer. Ohne auflösbare Firma kein Name: Sein eigener wäre eine
	 * falsche Auskunft.
	 */
	private void addCompanyNamesToContactPersons(List<KontakteSearchHit> hits) throws SolrServerException, IOException {
		Set<String> customerNumbers = new LinkedHashSet<>();
		Set<String> supplierNumbers = new LinkedHashSet<>();
		List<Map<String, Object>> contactPersons = new ArrayList<>();
		for (KontakteSearchHit hit : hits) {
			Map<String, Object> fieldValues = hit.fieldValues();
			if (CONTACT_KIND_COMPANY.equals(firstValueOf(fieldValues.get(CONTACT_KIND_FIELD_NAME)))) {
				continue;
			}
			contactPersons.add(fieldValues);
			String customerNumber = firstValueOf(fieldValues.get(CUSTOMER_NUMBER_FIELD_NAME));
			if (customerNumber != null) {
				customerNumbers.add(customerNumber);
			}
			String supplierNumber = firstValueOf(fieldValues.get(SUPPLIER_NUMBER_FIELD_NAME));
			if (supplierNumber != null) {
				supplierNumbers.add(supplierNumber);
			}
		}
		if (customerNumbers.isEmpty() && supplierNumbers.isEmpty()) {
			return;
		}
		List<String> numberClauses = new ArrayList<>();
		if (!customerNumbers.isEmpty()) {
			numberClauses.add(CUSTOMER_NUMBER_FIELD_NAME + ":(" + escapedAlternatives(customerNumbers) + ")");
		}
		if (!supplierNumbers.isEmpty()) {
			numberClauses.add(SUPPLIER_NUMBER_FIELD_NAME + ":(" + escapedAlternatives(supplierNumbers) + ")");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		params.add("fq", CONTACT_KIND_FIELD_NAME + ":" + ClientUtils.escapeQueryChars(CONTACT_KIND_COMPANY));
		// ergibt z. B. kundenNummer:(120120 OR 120121) OR lieferantenNummer:(70010)
		params.add("fq", String.join(" OR ", numberClauses));
		params.set("fl", String.join(",", "id", NAME_FIELD_NAME, CUSTOMER_NUMBER_FIELD_NAME, SUPPLIER_NUMBER_FIELD_NAME));
		// feste Reihenfolge: Tragen zwei Firmen dieselbe Nummer, gewinnt immer dieselbe
		params.set("sort", "id asc");
		params.set("rows", Math.min(MAX_COMPANIES_FOR_NAMES, 2 * (customerNumbers.size() + supplierNumbers.size())));
		Map<String, String> companyNameByCustomerNumber = new LinkedHashMap<>();
		Map<String, String> companyNameBySupplierNumber = new LinkedHashMap<>();
		for (SolrDocument company : client().query(params).getResults()) {
			String companyName = firstValueOf(company.getFieldValue(NAME_FIELD_NAME));
			if (companyName == null) {
				continue;
			}
			for (String number : valuesOf(company.getFieldValue(CUSTOMER_NUMBER_FIELD_NAME))) {
				companyNameByCustomerNumber.putIfAbsent(number, companyName);
			}
			for (String number : valuesOf(company.getFieldValue(SUPPLIER_NUMBER_FIELD_NAME))) {
				companyNameBySupplierNumber.putIfAbsent(number, companyName);
			}
		}
		for (Map<String, Object> contactPerson : contactPersons) {
			String companyName = companyNameByCustomerNumber.get(firstValueOf(contactPerson.get(CUSTOMER_NUMBER_FIELD_NAME)));
			if (companyName == null) {
				companyName = companyNameBySupplierNumber.get(firstValueOf(contactPerson.get(SUPPLIER_NUMBER_FIELD_NAME)));
			}
			if (companyName != null) {
				contactPerson.put(COMPANY_NAME_OF_CONTACT_PERSON_FIELD_NAME, companyName);
			}
		}
	}

	/** Die Werte escaped und mit OR verbunden, für feld:(a OR b) */
	private static String escapedAlternatives(Set<String> values) {
		List<String> escaped = new ArrayList<>();
		for (String value : values) {
			escaped.add(ClientUtils.escapeQueryChars(value));
		}
		return String.join(" OR ", escaped);
	}

	/** Alle Werte eines Feldes als Texte, ein einzelner Wert als Liste mit einem Eintrag, leer bei nichts */
	private static List<String> valuesOf(Object fieldValue) {
		if (fieldValue == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (Object value : fieldValue instanceof Collection<?> many ? many : List.of(fieldValue)) {
			String text = String.valueOf(value);
			if (!text.isBlank()) {
				values.add(text);
			}
		}
		return values;
	}

	/** Der erste Wert eines Feldes als Text, null bei nichts */
	private static String firstValueOf(Object fieldValue) {
		List<String> values = valuesOf(fieldValue);
		return values.isEmpty() ? null : values.get(0);
	}

	// ===== Schreiben =====

	/**
	 * Das Dokument, wie es geschrieben würde: id aus dem PRIMARY-Key, die Sternzeit des 19ers, je Feld mit command der
	 * Wert seines Keys, dazu die Sync-Felder. 400, wenn das Mapping nicht auf SOLR_KONTAKTE zielt, keinen PRIMARY hat
	 * oder dessen Wert leer ist.
	 */
	public Map<String, Object> buildKontakteDocumentFromMappedValues(ResponseMappingExecutionResult result) {
		return solrMirrorCoreDocumentWriteService.buildMirrorCoreDocumentFromMappedValues(CORE_KEY, TARGET_KEY, result,
				SYNC_DATE_FIELD_NAME, SYNC_STATUS_FIELD_NAME, SYNC_ERROR_FIELD_NAME);
	}

	/** Schreibt das Dokument in den Kern KONTAKTE: neu voll, vorhanden per Atomic Update mit SET je Feld. Nur ADMIN. */
	@PreAuthorize("hasRole('ADMIN')")
	public void writeKontakteDocument(Map<String, Object> document) {
		solrMirrorCoreDocumentWriteService.writeMirrorCoreDocument(CORE_KEY, document, WRITE_PROCESS_NAME);
	}

	private SolrClient client() {
		// die HTTP-Verbindung zum Kern KONTAKTE, je Kern genau ein Client
		return solrClientRegistry.clientFor(CORE_KEY);
	}

	private static ResponseStatusException unavailable(Exception e) {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + CORE_KEY + "): " + e.getMessage(), e);
	}

}

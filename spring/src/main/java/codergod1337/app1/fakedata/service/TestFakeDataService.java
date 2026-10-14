package codergod1337.app1.fakedata.service;

import codergod1337.app1.fakedata.model.TestFakeDataRequest;
import codergod1337.app1.fakedata.model.TestFakeDataStatus;
import codergod1337.app1.fakedata.service.TestFakeDataWordLists.FakeArticle;
import codergod1337.app1.fakedata.service.TestFakeDataWordLists.FakeCompanyName;
import codergod1337.app1.file.filedata.model.FileDataUploadResult;
import codergod1337.app1.file.filedata.service.FileDataService;
import codergod1337.app1.message.archive.service.MailAttachmentMultipartFile;
import codergod1337.app1.solr.artikel.service.SolrDocumentArtikelService;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.kontakte.service.SolrDocumentKontakteService;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.datafaker.Faker;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Testdaten für die Solr-Kerne (User 2026-10-14): Ziel ist, früh die spätere Performance einzuschätzen. Erzeugt Kunden
 * und Lieferanten im Kern KONTAKTE, Artikel im Kern ARTIKEL und Datenblätter als TXT im DMS, alles über die normalen
 * Schreibwege des Backends (writeKontakteDocument, writeArtikelDocument, FileDataService.upload), kein Batch. So misst
 * der Lauf zugleich, wie schnell diese Wege sind.
 *
 * Gefüllt werden nur die Felder, die die Suche des jeweiligen Kerns nutzt, dazu id, kontaktorig, tele und die Hooks.
 * Jedes Dokument trägt im Hook wtf den Wert TEST_DATA: Daran findet das Aufräumen alles wieder.
 *
 * Jeder Lauf legt WEITERE Daten an (User 2026-10-14): Vor dem Start fragt der Service Solr einmal nach dem
 * vorhandenen Testbestand (wtf:TEST_DATA) und zählt dahinter weiter, je Art ihr eigener Zähler (Plan).
 *
 * - Kunden und Lieferanten: je eine Firma mit 1 bis 15 Ansprechpartnern, im Mittel 5, normalverteilt. Namen, Straßen
 * und Orte von Datafaker; Kundennummern ab 2000001, Lieferantennummern ab 7000001 (die echten sind sechsstellig);
 * Sternzeiten ab (900000000,19,0) und (950000000,19,0), je Firma 16 Plätze. Weiter geht es hinter der höchsten
 * Sternzeit des Testbestands.
 * - Artikel: Nummern fortlaufend ab 10000000, weiter hinter der höchsten Testartikelnummer, 997… sind Werkzeuge
 * (TestFakeDataWordLists). Jeder hat einen Kunden und 1 bis 3 Lieferanten aus allen Testfirmen, rund jeder zweite eine
 * EAN.
 * - Datenblätter: Inhalt die laufende Nummer, weiter hinter dem höchsten vorhandenen Datenblatt, dazu 1 bis 5 Sätze zu
 * ihren Artikeln, Dateiart DATENBLATT. Sie hängen an den Artikeln dieses Laufs, legt er keine an, an allen
 * Testartikeln. Je Artikel eine Zahl Datenblätter aus der Normalverteilung um das Mittel, je Datenblatt eine Zahl
 * Artikel um Artikel × Mittel ÷ Datenblätter, mindestens einer; beide Seiten ziehen aus demselben Vorrat, deshalb geht
 * die Rechnung auf.
 *
 * Der Zufall hängt an der Nummer: Derselbe Artikel, dieselbe Firma heißt immer gleich, neue Nummern bringen neue
 * Namen. Was es schon gibt (id im Kern, Hash im DMS), wird übersprungen, nie überschrieben; das Echtzeit-Lesen sieht
 * dabei auch, was Solr noch nicht aufgefrischt hat. Ein Lauf zur Zeit, im Hintergrund mit dem Security-Context des
 * Admins, der ihn startet.
 */
@Service
public class TestFakeDataService {

	private static final Logger log = LoggerFactory.getLogger(TestFakeDataService.class);

	/** Der Wert im Hook wtf an jedem erzeugten Dokument (User 2026-10-14) */
	public static final String TEST_DATA_FLAG = "TEST_DATA";

	private static final String FLAG_HOOK_KEY = "wtf";

	/** Der Startwert des Zufalls: derselbe Lauf, dieselben Daten */
	private static final long SEED = 20_261_014L;

	public static final int FIRST_ARTICLE_NUMBER = 10_000_000;

	/** Bis 99999999 bleiben die Nummern achtstellig wie die echten */
	public static final int MAX_ARTICLE_COUNT = 90_000_000;

	private static final int FIRST_CUSTOMER_NUMBER = 2_000_001;
	private static final int FIRST_SUPPLIER_NUMBER = 7_000_001;

	/** Die Nummernkreise der Kunden und Lieferanten bleiben siebenstellig */
	public static final int MAX_PARTNER_COUNT = 999_999;

	private static final long FIRST_CUSTOMER_STERNZEIT = 900_000_000L;
	private static final long FIRST_SUPPLIER_STERNZEIT = 950_000_000L;

	private static final int MIN_CONTACT_PERSONS = 1;
	private static final int MAX_CONTACT_PERSONS = 15;
	private static final double CONTACT_PERSONS_MEAN = 5;
	private static final double CONTACT_PERSONS_DEVIATION = 3;

	/** Je Firma so viele Sternzeiten: die Firma und höchstens 15 Ansprechpartner */
	private static final int STERNZEIT_STEP = MAX_CONTACT_PERSONS + 1;

	/** Der Wert von kontaktorig an einem Ansprechpartner, wie ABAS ihn liefert; die Firma ist CONTACT_KIND_COMPANY */
	private static final String CONTACT_KIND_PERSON = "Kontakt";

	private static final int MAX_SUPPLIERS_PER_ARTICLE = 3;

	private static final String DOCUMENT_FILE_SUB_CLASS_KEY = "DATENBLATT";

	private static final String DMS_CORE_KEY = "DMS";

	/** So viele Sätze zu den Artikeln stehen höchstens in einem Datenblatt */
	private static final int MAX_DATA_SHEET_SENTENCES = 5;

	/** So viele Fehler hintereinander, dann bricht der Lauf ab: Dann ist Solr oder der SFTP weg, nicht ein Eintrag kaputt */
	private static final int MAX_FAILURES_IN_A_ROW = 50;

	private static final String PHASE_CUSTOMERS = "CUSTOMERS";
	private static final String PHASE_SUPPLIERS = "SUPPLIERS";
	private static final String PHASE_ARTICLES = "ARTICLES";
	private static final String PHASE_DOCUMENTS = "DOCUMENTS";

	/** Der Dateiname eines Datenblatts trägt seine Nummer: datenblatt-123.txt */
	private static final String DOCUMENT_FILE_NAME_PREFIX = "datenblatt-";
	private static final Pattern DOCUMENT_FILE_NAME = Pattern.compile(DOCUMENT_FILE_NAME_PREFIX + "(\\d+)\\.txt");

	/** So viele der jüngsten Datenblätter liest der Plan, um die höchste Nummer zu finden */
	private static final int NEWEST_DOCUMENTS_FOR_PLAN = 100;

	/**
	 * Wo dieser Lauf anfängt, hinter dem vorhandenen Testbestand: der erste Index der Artikel (Nummer minus 10000000),
	 * der Kunden und der Lieferanten und die erste Nummer der Datenblätter. Alles davor gibt es schon.
	 */
	private record Plan(int firstArticleIndex, int firstCustomerIndex, int firstSupplierIndex, int firstDocumentNumber) {
	}

	private final SolrDocumentKontakteService solrDocumentKontakteService;
	private final SolrDocumentArtikelService solrDocumentArtikelService;
	private final FileDataService fileDataService;
	private final SolrDocumentService solrDocumentService;
	private final ObjectMapper objectMapper;

	/** Ein Thread: nie zwei Läufe gleichzeitig */
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "test-fake-data");
		thread.setDaemon(true);
		return thread;
	});

	/** Der letzte Lauf, null, solange seit dem Start des Backends keiner lief */
	private volatile Run currentRun;

	public TestFakeDataService(SolrDocumentKontakteService solrDocumentKontakteService,
			SolrDocumentArtikelService solrDocumentArtikelService, FileDataService fileDataService,
			SolrDocumentService solrDocumentService, ObjectMapper objectMapper) {
		this.solrDocumentKontakteService = solrDocumentKontakteService;
		this.solrDocumentArtikelService = solrDocumentArtikelService;
		this.fileDataService = fileDataService;
		this.solrDocumentService = solrDocumentService;
		this.objectMapper = objectMapper;
	}

	@PreDestroy
	void shutdown() {
		Run run = currentRun;
		if (run != null) {
			run.cancelRequested = true;
		}
		executor.shutdownNow();
	}

	// ===== Steuerung =====

	/** Startet einen Lauf im Hintergrund. 400 bei unbrauchbaren Zahlen, 409, wenn schon einer läuft. */
	@PreAuthorize("hasRole('ADMIN')")
	public synchronized TestFakeDataStatus start(TestFakeDataRequest request) {
		requireUsable(request);
		Run running = currentRun;
		if (running != null && running.finishedAt == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "es läuft schon ein Testdaten-Lauf");
		}
		Plan plan = planOf();
		requireFits(request, plan);
		Run run = new Run(request, plan);
		currentRun = run;
		// der Security-Context des Admins geht mit: Die Schreibwege prüfen ADMIN und Schreibrolle wie bei jedem Request
		executor.submit(new DelegatingSecurityContextRunnable(() -> execute(run)));
		log.info("Testdaten: Lauf gestartet mit {}, weiter ab {}", request, plan);
		return statusOf(run);
	}

	// ===== Der Plan: hinter dem vorhandenen Testbestand weiter =====

	/**
	 * Eine Abfrage je Zähler an Solr, nur Dokumente mit wtf:TEST_DATA: die höchste Artikelnummer (alle achtstellig, die
	 * Sortierung als Text stimmt), die höchste Sternzeit der Kunden und der Lieferanten (alle neunstellig) und die
	 * höchste Nummer unter den jüngsten Datenblättern. Was in den letzten 10 s geschrieben und noch nicht aufgefrischt
	 * ist, sieht die Suche nicht; die Prüfung je Eintrag überspringt es dann, statt es zu überschreiben.
	 */
	private Plan planOf() {
		String highestArticle = highestTestDocumentId(SolrDocumentArtikelService.CORE_KEY, null);
		int firstArticleIndex = highestArticle == null ? 0 : Integer.parseInt(highestArticle) - FIRST_ARTICLE_NUMBER + 1;
		int firstCustomerIndex = nextPartnerIndex("kundenNummer", FIRST_CUSTOMER_STERNZEIT);
		int firstSupplierIndex = nextPartnerIndex("lieferantenNummer", FIRST_SUPPLIER_STERNZEIT);
		return new Plan(firstArticleIndex, firstCustomerIndex, firstSupplierIndex, nextDocumentNumber());
	}

	/** Die höchste id unter den Testdokumenten des Kerns, mit einem weiteren Filter; null ohne Testdokument */
	private String highestTestDocumentId(String coreKey, String extraFilterQuery) {
		SolrQuery query = new SolrQuery("*:*");
		query.addFilterQuery(FLAG_HOOK_KEY + ":" + TEST_DATA_FLAG);
		if (extraFilterQuery != null) {
			query.addFilterQuery(extraFilterQuery);
		}
		query.setSort(SolrDocumentService.ID_FIELD_NAME, SolrQuery.ORDER.desc);
		query.setFields(SolrDocumentService.ID_FIELD_NAME);
		query.setRows(1);
		List<Map<String, Object>> documents = solrDocumentService.queryDocumentsPage(coreKey, query).documents();
		return documents.isEmpty() ? null : String.valueOf(documents.get(0).get(SolrDocumentService.ID_FIELD_NAME));
	}

	/** Der nächste freie Index der Kunden oder Lieferanten: aus der höchsten Sternzeit ihres Blocks, je Firma 16 Plätze */
	private int nextPartnerIndex(String numberHookKey, long firstSternzeit) {
		String highest = highestTestDocumentId(SolrDocumentKontakteService.CORE_KEY, numberHookKey + ":[* TO *]");
		if (highest == null) {
			return 0;
		}
		// (900000016,19,0) → 900000016
		long sternzeit = Long.parseLong(highest.substring(1, highest.indexOf(',')));
		return (int) ((sternzeit - firstSternzeit) / STERNZEIT_STEP) + 1;
	}

	/** Die nächste Nummer der Datenblätter: die höchste unter den jüngsten Test-Datenblättern plus eins, sonst 0 */
	private int nextDocumentNumber() {
		SolrQuery query = new SolrQuery("*:*");
		query.addFilterQuery(FLAG_HOOK_KEY + ":" + TEST_DATA_FLAG);
		query.addFilterQuery("fileSubClassKey:" + DOCUMENT_FILE_SUB_CLASS_KEY);
		query.setSort("documentCreatedAt", SolrQuery.ORDER.desc);
		query.setFields("fileName");
		query.setRows(NEWEST_DOCUMENTS_FOR_PLAN);
		int next = 0;
		for (Map<String, Object> document : solrDocumentService.queryDocumentsPage(DMS_CORE_KEY, query).documents()) {
			Matcher matcher = DOCUMENT_FILE_NAME.matcher(String.valueOf(document.get("fileName")));
			if (matcher.matches()) {
				next = Math.max(next, Integer.parseInt(matcher.group(1)) + 1);
			}
		}
		return next;
	}

	/** Die Zähler dürfen ihre Nummernkreise nicht verlassen, und jeder Artikel braucht Testkunden und -lieferanten */
	private static void requireFits(TestFakeDataRequest request, Plan plan) {
		List<String> problems = new ArrayList<>();
		if ((long) plan.firstArticleIndex() + request.articleCount() > MAX_ARTICLE_COUNT) {
			problems.add("es gibt schon Testartikel bis " + (FIRST_ARTICLE_NUMBER + plan.firstArticleIndex() - 1)
					+ ", höchstens " + (MAX_ARTICLE_COUNT - plan.firstArticleIndex()) + " weitere passen in die achtstelligen Nummern");
		}
		if ((long) plan.firstCustomerIndex() + request.customerCount() > MAX_PARTNER_COUNT) {
			problems.add("höchstens " + (MAX_PARTNER_COUNT - plan.firstCustomerIndex()) + " weitere Kunden");
		}
		if ((long) plan.firstSupplierIndex() + request.supplierCount() > MAX_PARTNER_COUNT) {
			problems.add("höchstens " + (MAX_PARTNER_COUNT - plan.firstSupplierIndex()) + " weitere Lieferanten");
		}
		if (request.articleCount() > 0 && (plan.firstCustomerIndex() + request.customerCount() == 0
				|| plan.firstSupplierIndex() + request.supplierCount() == 0)) {
			problems.add("jeder Artikel braucht einen Kunden und Lieferanten: es gibt noch keine Testkunden oder -lieferanten");
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
	}

	/** Der Stand des letzten Laufs */
	@PreAuthorize("hasRole('ADMIN')")
	public TestFakeDataStatus getStatus() {
		return statusOf(currentRun);
	}

	/** Bricht den laufenden Lauf nach dem aktuellen Eintrag ab; ohne Lauf ändert sich nichts */
	@PreAuthorize("hasRole('ADMIN')")
	public TestFakeDataStatus cancel() {
		Run run = currentRun;
		if (run != null && run.finishedAt == null) {
			run.cancelRequested = true;
			log.info("Testdaten: Abbrechen angefordert");
		}
		return statusOf(run);
	}

	private static void requireUsable(TestFakeDataRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Eingabe");
		}
		List<String> problems = new ArrayList<>();
		if (request.articleCount() < 0 || request.articleCount() > MAX_ARTICLE_COUNT) {
			problems.add("articleCount muss zwischen 0 und " + MAX_ARTICLE_COUNT + " liegen");
		}
		if (request.customerCount() < 0 || request.customerCount() > MAX_PARTNER_COUNT) {
			problems.add("customerCount muss zwischen 0 und " + MAX_PARTNER_COUNT + " liegen");
		}
		if (request.supplierCount() < 0 || request.supplierCount() > MAX_PARTNER_COUNT) {
			problems.add("supplierCount muss zwischen 0 und " + MAX_PARTNER_COUNT + " liegen");
		}
		if (request.documentCount() < 0) {
			problems.add("documentCount darf nicht negativ sein");
		}
		if (!Double.isFinite(request.documentsPerArticleMean()) || request.documentsPerArticleMean() < 0) {
			problems.add("documentsPerArticleMean darf nicht negativ sein");
		}
		if (!Double.isFinite(request.documentsPerArticleDeviation()) || request.documentsPerArticleDeviation() < 0) {
			problems.add("documentsPerArticleDeviation darf nicht negativ sein");
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
	}

	// ===== Der Lauf =====

	private void execute(Run run) {
		try {
			writePartners(run, run.phase(PHASE_CUSTOMERS), true);
			writePartners(run, run.phase(PHASE_SUPPLIERS), false);
			writeArticles(run, run.phase(PHASE_ARTICLES));
			writeDocuments(run, run.phase(PHASE_DOCUMENTS));
		} catch (AbortException e) {
			run.errorMessage = e.getMessage();
			log.warn("Testdaten: Lauf abgebrochen: {}", e.getMessage());
		} catch (RuntimeException e) {
			run.errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			log.error("Testdaten: Lauf mit Fehler beendet", e);
		} finally {
			run.currentPhase = null;
			run.finishedAt = Instant.now();
			log.info("Testdaten: Lauf beendet{}", run.cancelRequested ? " (abgebrochen)" : "");
		}
	}

	/** Kunden oder Lieferanten, hinter den vorhandenen weiter: je Firma ihr Dokument und die ihrer Ansprechpartner */
	private void writePartners(Run run, PhaseProgress progress, boolean customers) {
		int count = customers ? run.request.customerCount() : run.request.supplierCount();
		int firstIndex = customers ? run.plan.firstCustomerIndex() : run.plan.firstSupplierIndex();
		// ein Random für Datafaker und die Wortlisten, je Firma neu gesetzt: Dieselbe Firma heißt immer gleich
		Random random = new Random();
		Faker faker = new Faker(Locale.GERMANY, random);
		begin(run, progress);
		for (int index = firstIndex; index < firstIndex + count && !run.cancelRequested; index++) {
			random.setSeed(SEED * 1_000_033L + (customers ? 0 : 500_000_000L) + index);
			List<Map<String, Object>> documents = partnerDocuments(customers, index, random, faker);
			attempt(run, progress, () -> {
				if (solrDocumentKontakteService.getById(String.valueOf(documents.get(0).get("id"))) != null) {
					progress.skipped.incrementAndGet();
					return;
				}
				for (Map<String, Object> document : documents) {
					solrDocumentKontakteService.writeKontakteDocument(document);
					progress.written.incrementAndGet();
				}
			});
			progress.done.incrementAndGet();
		}
		end(progress);
	}

	/** Die Firma zuerst, dann ihre Ansprechpartner, alle mit derselben Kunden- oder Lieferantennummer */
	private static List<Map<String, Object>> partnerDocuments(boolean customer, int index, Random random, Faker faker) {
		String partnerNumber = String.valueOf((customer ? FIRST_CUSTOMER_NUMBER : FIRST_SUPPLIER_NUMBER) + index);
		String numberHookKey = customer ? "kundenNummer" : "lieferantenNummer";
		long sternzeitBase = (customer ? FIRST_CUSTOMER_STERNZEIT : FIRST_SUPPLIER_STERNZEIT) + (long) index * STERNZEIT_STEP;
		FakeCompanyName company = TestFakeDataWordLists.companyNameOf(faker.name().lastName(), faker.name().lastName(), random);
		String phone = TestFakeDataWordLists.phoneNumberOf(random);
		List<Map<String, Object>> documents = new ArrayList<>();

		Map<String, Object> companyDocument = contactDocument(sternzeitBase, SolrDocumentKontakteService.CONTACT_KIND_COMPANY);
		companyDocument.put("ans", company.name());
		companyDocument.put("kontaktName", company.name());
		companyDocument.put("kontaktSuch", TestFakeDataWordLists.searchWordOf(company.name(), 8));
		companyDocument.put("str", faker.address().streetName() + " " + (1 + random.nextInt(180)));
		companyDocument.put("nort", faker.address().city());
		companyDocument.put("email", (random.nextBoolean() ? "info@" : "vertrieb@") + company.domain());
		companyDocument.put("tele", phone);
		companyDocument.put(numberHookKey, List.of(partnerNumber));
		documents.add(companyDocument);

		int persons = normalCount(random, CONTACT_PERSONS_MEAN, CONTACT_PERSONS_DEVIATION, MIN_CONTACT_PERSONS,
				MAX_CONTACT_PERSONS);
		for (int person = 1; person <= persons; person++) {
			String firstName = random.nextBoolean() ? faker.name().femaleFirstName() : faker.name().maleFirstName();
			String lastName = faker.name().lastName();
			// wie in ABAS: „Nachname Vorname“, Suchwort Nachname plus drei Buchstaben des Vornamens
			String contactName = lastName + " " + firstName;
			Map<String, Object> personDocument = contactDocument(sternzeitBase + person, CONTACT_KIND_PERSON);
			personDocument.put("kontakt", contactName);
			personDocument.put("kontaktName", contactName);
			personDocument.put("kontaktSuch", TestFakeDataWordLists.searchWordOf(lastName, 6)
					+ TestFakeDataWordLists.searchWordOf(firstName, 3));
			personDocument.put("funktion", TestFakeDataWordLists.pick(TestFakeDataWordLists.FUNCTIONS, random));
			personDocument.put("email", TestFakeDataWordLists.slugOf(firstName) + "." + TestFakeDataWordLists.slugOf(lastName)
					+ "@" + company.domain());
			personDocument.put("tele", phone + "-" + (10 + random.nextInt(890)));
			personDocument.put(numberHookKey, List.of(partnerNumber));
			documents.add(personDocument);
		}
		return documents;
	}

	/** Was jeder Kontakt trägt: Sternzeit als id und Hook, nummer, Firma oder Ansprechpartner, das Testdaten-Flag */
	private static Map<String, Object> contactDocument(long sternzeitNumber, String contactKind) {
		String sternzeit = "(" + sternzeitNumber + ",19,0)";
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("id", sternzeit);
		document.put("sternzeit", List.of(sternzeit));
		document.put(SolrDocumentKontakteService.CONTACT_KIND_FIELD_NAME, contactKind);
		// Kunden und Lieferanten liegen 50 Millionen auseinander, so bleibt die Nummer eindeutig
		document.put("nummer", String.valueOf(sternzeitNumber % 100_000_000L));
		document.put(FLAG_HOOK_KEY, List.of(TEST_DATA_FLAG));
		return document;
	}

	/** Die Artikel, Nummer für Nummer hinter dem vorhandenen Testbestand */
	private void writeArticles(Run run, PhaseProgress progress) {
		TestFakeDataRequest request = run.request;
		int firstIndex = run.plan.firstArticleIndex();
		// Kunden und Lieferanten aus dem ganzen Testbestand, die dieses Laufs eingeschlossen
		int customerCount = run.plan.firstCustomerIndex() + request.customerCount();
		int supplierCount = run.plan.firstSupplierIndex() + request.supplierCount();
		begin(run, progress);
		for (int index = firstIndex; index < firstIndex + request.articleCount() && !run.cancelRequested; index++) {
			Random random = articleRandom(index);
			FakeArticle article = TestFakeDataWordLists.articleOf(FIRST_ARTICLE_NUMBER + index, random);
			Map<String, Object> document = articleDocument(article, random, customerCount, supplierCount);
			attempt(run, progress, () -> {
				if (solrDocumentArtikelService.getById(article.number()) != null) {
					progress.skipped.incrementAndGet();
					return;
				}
				solrDocumentArtikelService.writeArtikelDocument(document);
				progress.written.incrementAndGet();
			});
			progress.done.incrementAndGet();
		}
		end(progress);
	}

	/** Je Artikel ein eigener Zufall aus seinem Index: Die Datenblätter erzeugen denselben Artikel noch einmal */
	private static Random articleRandom(int index) {
		return new Random(SEED * 1_000_003L + index);
	}

	/** Die durchsuchten Felder des Artikels, ein Kunde, 1 bis 3 Lieferanten, rund jeder zweite eine EAN */
	private static Map<String, Object> articleDocument(FakeArticle article, Random random, int customerCount,
			int supplierCount) {
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("id", article.number());
		document.put("name", article.name());
		document.put("such", article.searchWord());
		document.put("zeichn", article.drawingNumber());
		document.put("vbez", article.salesName());
		document.put("bem", article.remark());
		document.put("beschr", article.description());
		document.put("artikelNummer", List.of(article.number()));
		document.put("kundenNummer", List.of(String.valueOf(FIRST_CUSTOMER_NUMBER + random.nextInt(customerCount))));
		int suppliers = 1 + random.nextInt(Math.min(MAX_SUPPLIERS_PER_ARTICLE, supplierCount));
		Set<String> supplierNumbers = new LinkedHashSet<>();
		while (supplierNumbers.size() < suppliers) {
			supplierNumbers.add(String.valueOf(FIRST_SUPPLIER_NUMBER + random.nextInt(supplierCount)));
		}
		document.put("lieferantenNummer", new ArrayList<>(supplierNumbers));
		if (random.nextBoolean()) {
			document.put("ean", List.of(eanOf(random)));
		}
		document.put(FLAG_HOOK_KEY, List.of(TEST_DATA_FLAG));
		return document;
	}

	/** Eine EAN-13 mit deutschem Präfix 40 bis 44 und richtiger Prüfziffer */
	private static String eanOf(Random random) {
		StringBuilder digits = new StringBuilder().append(4).append(random.nextInt(5));
		while (digits.length() < 12) {
			digits.append(random.nextInt(10));
		}
		int sum = 0;
		for (int position = 0; position < 12; position++) {
			sum += (digits.charAt(position) - '0') * (position % 2 == 0 ? 1 : 3);
		}
		return digits.append((10 - sum % 10) % 10).toString();
	}

	/**
	 * Die Datenblätter. Erst je Artikel seine Zahl Datenblätter aus der Normalverteilung, das ist der Vorrat an
	 * Verknüpfungen. Dann je Datenblatt eine Zahl Artikel um den Rest des Vorrats geteilt durch die übrigen Datenblätter,
	 * mindestens einer: So zieht jedes Datenblatt im Mittel an articleCount × Mittel ÷ documentCount Artikeln, und am Ende
	 * ist der Vorrat aufgebraucht. Ist er vorher leer, nimmt ein Datenblatt einen beliebigen Artikel.
	 */
	private void writeDocuments(Run run, PhaseProgress progress) {
		TestFakeDataRequest request = run.request;
		int documentCount = request.documentCount();
		int firstDocumentNumber = run.plan.firstDocumentNumber();
		// die Artikel, an denen die Datenblätter hängen: die dieses Laufs, legt er keine an, alle Testartikel davor
		int rangeStart = request.articleCount() > 0 ? run.plan.firstArticleIndex() : 0;
		int articleCount = request.articleCount() > 0 ? request.articleCount() : run.plan.firstArticleIndex();
		begin(run, progress);
		// je Lauf ein anderer Zufall: Die Nummer des ersten Datenblatts unterscheidet die Läufe
		Random random = new Random(SEED * 1_000_037L + firstDocumentNumber);
		int[] remaining = new int[0];
		int[] pool = new int[0];
		int poolSize = 0;
		long remainingLinks = 0;
		double deviationPerDocument = 0;
		if (articleCount > 0 && documentCount > 0) {
			remaining = new int[articleCount];
			pool = new int[articleCount];
			for (int local = 0; local < articleCount; local++) {
				int links = normalCount(random, request.documentsPerArticleMean(), request.documentsPerArticleDeviation(), 0,
						Integer.MAX_VALUE);
				remaining[local] = links;
				remainingLinks += links;
				if (links > 0) {
					pool[poolSize++] = local;
				}
			}
			double articlesPerDocument = (double) remainingLinks / documentCount;
			// dieselbe relative Streuung wie je Artikel
			deviationPerDocument = request.documentsPerArticleMean() > 0
					? request.documentsPerArticleDeviation() * articlesPerDocument / request.documentsPerArticleMean() : 0;
			log.info("Testdaten: {} Verknüpfungen, im Mittel {} Artikel je Datenblatt", remainingLinks,
					String.format(Locale.ROOT, "%.2f", articlesPerDocument));
		}
		for (int offset = 0; offset < documentCount && !run.cancelRequested; offset++) {
			List<Integer> articleIndexes = new ArrayList<>();
			if (articleCount > 0) {
				double expected = (double) remainingLinks / (documentCount - offset);
				int wanted = normalCount(random, expected, deviationPerDocument, 1, articleCount);
				Set<Integer> chosen = new LinkedHashSet<>();
				int attempts = 0;
				while (chosen.size() < wanted && attempts < wanted * 10 + 10) {
					attempts++;
					if (poolSize > 0) {
						int position = random.nextInt(poolSize);
						int local = pool[position];
						if (!chosen.add(local)) {
							continue;
						}
						remainingLinks--;
						if (--remaining[local] == 0) {
							pool[position] = pool[--poolSize];
						}
					} else {
						chosen.add(random.nextInt(articleCount));
					}
				}
				for (int local : chosen) {
					articleIndexes.add(rangeStart + local);
				}
			}
			int documentNumber = firstDocumentNumber + offset;
			Map<String, Object> values = new LinkedHashMap<>();
			byte[] bytes = dataSheetOf(documentNumber, articleIndexes, random, values);
			attempt(run, progress, () -> {
				FileDataUploadResult result = fileDataService.upload(new MailAttachmentMultipartFile(
						DOCUMENT_FILE_NAME_PREFIX + documentNumber + ".txt", "text/plain", bytes), values);
				if (result.status() == FileDataUploadResult.FileDataUploadStatus.CREATED) {
					progress.written.incrementAndGet();
				} else {
					// dieselben Bytes lagen schon im DMS
					progress.skipped.incrementAndGet();
				}
			});
			progress.done.incrementAndGet();
		}
		end(progress);
	}

	/**
	 * Das Datenblatt: die laufende Nummer, dann 1 bis 5 Sätze zu seinen Artikeln, die dafür noch einmal aus ihrem Index
	 * erzeugt werden. Füllt die Werte des Uploads: Dateiart, Beschreibung in de, en, bg, die Artikelnummern, das Flag.
	 */
	private byte[] dataSheetOf(int documentNumber, List<Integer> articleIndexes, Random random, Map<String, Object> values) {
		List<String> articleNumbers = new ArrayList<>();
		for (int articleIndex : articleIndexes) {
			articleNumbers.add(String.valueOf(FIRST_ARTICLE_NUMBER + articleIndex));
		}
		// die Sätze kommen aus höchstens fünf der Artikel; ohne Artikel aus einem, der nur für den Text erzeugt wird
		List<FakeArticle> sources = new ArrayList<>();
		for (int position = 0; position < Math.min(MAX_DATA_SHEET_SENTENCES, articleIndexes.size()); position++) {
			int articleIndex = articleIndexes.get(position);
			sources.add(TestFakeDataWordLists.articleOf(FIRST_ARTICLE_NUMBER + articleIndex, articleRandom(articleIndex)));
		}
		if (sources.isEmpty()) {
			sources.add(TestFakeDataWordLists.articleOf(FIRST_ARTICLE_NUMBER + random.nextInt(MAX_ARTICLE_COUNT), random));
		}
		StringBuilder text = new StringBuilder().append(documentNumber).append('\n');
		int sentences = 1 + random.nextInt(MAX_DATA_SHEET_SENTENCES);
		for (int sentence = 0; sentence < sentences; sentence++) {
			FakeArticle source = sources.get(random.nextInt(sources.size()));
			List<String> candidates = source.dataSheetSentences();
			text.append(candidates.get(random.nextInt(candidates.size()))).append('\n');
		}

		String subject = sources.get(0).name();
		Map<String, String> description = new LinkedHashMap<>();
		description.put("de", "Datenblatt " + documentNumber + ": " + subject);
		description.put("en", "Data sheet " + documentNumber + ": " + subject);
		description.put("bg", "Информационен лист " + documentNumber + ": " + subject);
		values.put("fileSubClassKey", DOCUMENT_FILE_SUB_CLASS_KEY);
		values.put("fileDescription", objectMapper.writeValueAsString(description));
		if (!articleNumbers.isEmpty()) {
			values.put("artikelNummer", articleNumbers);
		}
		values.put(FLAG_HOOK_KEY, List.of(TEST_DATA_FLAG));
		return text.toString().getBytes(StandardCharsets.UTF_8);
	}

	// ===== Hilfen =====

	/** Eine ganze Zahl aus der Normalverteilung um mean, gerundet und auf min bis max begrenzt */
	private static int normalCount(Random random, double mean, double deviation, int min, int max) {
		long value = Math.round(mean + deviation * random.nextGaussian());
		return (int) Math.max(min, Math.min(max, value));
	}

	/** Ein Eintrag; scheitert er, zählt er als Fehler, und nach zu vielen hintereinander bricht der Lauf ab */
	private static void attempt(Run run, PhaseProgress progress, Runnable action) {
		try {
			action.run();
			run.failuresInARow = 0;
		} catch (RuntimeException e) {
			progress.failed.incrementAndGet();
			progress.lastErrorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			if (++run.failuresInARow >= MAX_FAILURES_IN_A_ROW) {
				throw new AbortException(MAX_FAILURES_IN_A_ROW + " Fehler hintereinander in " + progress.key + ", zuletzt: "
						+ progress.lastErrorMessage);
			}
		}
	}

	private static void begin(Run run, PhaseProgress progress) {
		run.currentPhase = progress.key;
		progress.startedAt = Instant.now();
		log.info("Testdaten: Phase {} mit {} Einheiten", progress.key, progress.total);
	}

	private static void end(PhaseProgress progress) {
		progress.finishedAt = Instant.now();
		log.info("Testdaten: Phase {} fertig, {} erledigt, {} geschrieben, {} übersprungen, {} Fehler", progress.key,
				progress.done.get(), progress.written.get(), progress.skipped.get(), progress.failed.get());
	}

	private static TestFakeDataStatus statusOf(Run run) {
		if (run == null) {
			return new TestFakeDataStatus(false, false, null, null, null, null, List.of(), null);
		}
		List<TestFakeDataStatus.Phase> phases = new ArrayList<>();
		for (PhaseProgress progress : run.phases.values()) {
			phases.add(progress.snapshot());
		}
		return new TestFakeDataStatus(run.finishedAt == null, run.cancelRequested, run.currentPhase, run.startedAt,
				run.finishedAt, run.request, phases, run.errorMessage);
	}

	/** Der Abbruch nach zu vielen Fehlern hintereinander */
	private static final class AbortException extends RuntimeException {
		AbortException(String message) {
			super(message);
		}
	}

	/** Ein Lauf: seine Eingabe, wo er anfängt, sein Stand und seine Phasen */
	private static final class Run {
		final TestFakeDataRequest request;
		final Plan plan;
		final Instant startedAt = Instant.now();
		final Map<String, PhaseProgress> phases = new LinkedHashMap<>();
		volatile Instant finishedAt;
		volatile boolean cancelRequested;
		volatile String currentPhase;
		volatile String errorMessage;
		/** nur der Lauf-Thread zählt */
		int failuresInARow;

		Run(TestFakeDataRequest request, Plan plan) {
			this.request = request;
			this.plan = plan;
			// je Phase die erste Nummer, die sie vergibt: Kunden- und Lieferantennummer, Artikelnummer, Datenblatt-Nummer
			phases.put(PHASE_CUSTOMERS, new PhaseProgress(PHASE_CUSTOMERS, request.customerCount(),
					FIRST_CUSTOMER_NUMBER + plan.firstCustomerIndex()));
			phases.put(PHASE_SUPPLIERS, new PhaseProgress(PHASE_SUPPLIERS, request.supplierCount(),
					FIRST_SUPPLIER_NUMBER + plan.firstSupplierIndex()));
			phases.put(PHASE_ARTICLES, new PhaseProgress(PHASE_ARTICLES, request.articleCount(),
					FIRST_ARTICLE_NUMBER + plan.firstArticleIndex()));
			phases.put(PHASE_DOCUMENTS, new PhaseProgress(PHASE_DOCUMENTS, request.documentCount(), plan.firstDocumentNumber()));
		}

		PhaseProgress phase(String key) {
			return phases.get(key);
		}
	}

	/** Der Fortschritt einer Phase, vom Lauf-Thread geschrieben, von der Statusabfrage gelesen */
	private static final class PhaseProgress {
		final String key;
		final long total;
		/** die erste Nummer der Phase; die letzte ist firstNumber + total − 1 */
		final long firstNumber;
		final AtomicLong done = new AtomicLong();
		final AtomicLong skipped = new AtomicLong();
		final AtomicLong failed = new AtomicLong();
		final AtomicLong written = new AtomicLong();
		volatile Instant startedAt;
		volatile Instant finishedAt;
		volatile String lastErrorMessage;

		PhaseProgress(String key, long total, long firstNumber) {
			this.key = key;
			this.total = total;
			this.firstNumber = firstNumber;
		}

		TestFakeDataStatus.Phase snapshot() {
			Instant start = startedAt;
			Instant end = finishedAt != null ? finishedAt : Instant.now();
			double seconds = start == null ? 0 : Math.max(0.001, Duration.between(start, end).toMillis() / 1000.0);
			double perSecond = start == null ? 0 : written.get() / seconds;
			return new TestFakeDataStatus.Phase(key, total, firstNumber, firstNumber + total - 1, done.get(), skipped.get(),
					failed.get(), written.get(), start, finishedAt, perSecond, lastErrorMessage);
		}
	}

}

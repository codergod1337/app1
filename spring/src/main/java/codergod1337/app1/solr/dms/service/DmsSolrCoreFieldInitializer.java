package codergod1337.app1.solr.dms.service;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.model.SolrFieldClientWriteRule;
import codergod1337.app1.solr.field.model.SolrFieldCodeDefinition;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der handgemachte Schema-Initialisierer des Kerns DMS (Vorgabe 2026-10-08): Beim Start legt er den Kern DMS an, falls
 * er fehlt, die Felder, die jeder Kern hat (ohne Kern in solr_field), und jedes Feld des Kerns DMS mit Typ, Schaltern,
 * Schreibregel, Startwert der AR-Liste, Name und Beschreibung in de, en und bg. Das Bild dazu ist
 * {@code codergod1337.app1.solr.dms.model.DmsSolrDocument}.
 *
 * Code und Postgres teilen sich die Wahrheit (SolrFieldCodeDefinition): Alles, was Solr braucht und was das Backend mit
 * einem Feld tut, kommt bei jedem Start aus dem Code; welche ARs ein Feld lesen dürfen und in welcher Reihenfolge die
 * Felder stehen, pflegt der Admin im Kern-Tab, der Code liefert nur den Startwert. Ein Feld, das hier nicht steht,
 * bleibt, wie der Admin es angelegt hat.
 *
 * id steht wie cursorDate ohne Kern in der Tabelle, auf Platz 1 (User 2026-10-09); das Basis-Configset hat es schon
 * beim Anlegen des Kerns, der Abgleich zieht nur die Schalter nach. Hooks haben ihre eigene Tabelle. readAcl und
 * writeAcl entstehen aus der Dateiart, das schreibt der Hintergrundjob nach jedem Schreiben, nie der Schreibvorgang
 * selbst (User 2026-10-08).
 *
 * required heißt: Der Client muss das Feld beim Anlegen mitschicken, sonst wird der Upload abgelehnt. Ein Feld mit
 * Schreibregel Backend ist deshalb nie required, das Backend setzt es selbst (User 2026-10-09).
 *
 * Läuft nach dem Start der Anwendung, in einer Transaktion, ohne Token: deshalb die Methoden ohne @PreAuthorize in
 * SolrCoreService und SolrFieldService. Der Abgleich mit Solr folgt von selbst über das Ereignis nach dem Commit.
 */
@Component
public class DmsSolrCoreFieldInitializer {

	private static final Logger log = LoggerFactory.getLogger(DmsSolrCoreFieldInitializer.class);

	/** Der Kern DMS */
	public static final String CORE_KEY = "DMS";

	/** Die Grundmenge (User 2026-10-09): Felder, die jeder User braucht, damit sich ein Dokument darstellen lässt */
	private static final String USER_ROLE_KEY = "USER";

	/** Vertrauliche Felder, z. B. die ACLs */
	private static final String CONFIDENTIAL_ROLE_KEY = "SOLR_FIELD_VIEW_CONFIDENTIAL";

	/** „immer“: jeder angemeldete User */
	private static final List<String> ALWAYS = List.of(USER_ROLE_KEY);
	private static final List<String> CONFIDENTIAL = List.of(CONFIDENTIAL_ROLE_KEY);
	/** niemand außer SOLR_USERS_VIEW_ALL */
	private static final List<String> NOBODY = List.of();

	private static final SolrFieldClientWriteRule CLIENT = SolrFieldClientWriteRule.CLIENT_SETS_AND_CHANGES;
	private static final SolrFieldClientWriteRule BACKEND = SolrFieldClientWriteRule.BACKEND_ONLY;

	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;

	public DmsSolrCoreFieldInitializer(SolrCoreService solrCoreService, SolrFieldService solrFieldService) {
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
	}

	/** Nach dem Start, vor dem ersten Abgleich mit Solr (der hat Order 0): Kern, Felder jedes Kerns, Felder des DMS. */
	@Order(-10)
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void defineDmsCoreAndFields() {
		solrCoreService.createSolrCoreIfMissing(CORE_KEY,
				multilingual("Dokumente", "Documents", "Документи"), null, 1);
		int created = 0;
		int updated = 0;
		for (SolrFieldCodeDefinition definition : definitions()) {
			if (solrFieldService.defineSolrFieldFromCode(definition)) {
				created++;
			} else {
				updated++;
			}
		}
		log.info("Kern DMS aus dem Code: {} Felder neu, {} nachgezogen", created, updated);
	}

	/** Alle Felder in der Reihenfolge des Kern-Tabs: erst die jedes Kerns, dann die des DMS */
	static List<SolrFieldCodeDefinition> definitions() {
		return List.of(

				// ===== in jedem Kern =====

				field(null, SolrCore.ID_FIELD_NAME, SolrFieldType.STRING, true, true, false, true, false, false, ALWAYS,
						false, BACKEND, 0,
						multilingual("ID", "ID", "ID"),
						multilingual(
								"Der Primärschlüssel (uniqueKey) jedes Kerns, in jedem Kern derselbe Name. Was hineinkommt, sagt der Code des Kerns, beim DMS der Hash der Bytes der Datei. Immer lesbar, setzt nur das Backend.",
								"The primary key (uniqueKey) of every core, the same name in every core. What goes in is decided by the core's code, for the DMS the hash of the file's bytes. Always readable, set only by the backend.",
								"Първичният ключ (uniqueKey) на всяко ядро, едно и също име във всяко ядро. Какво влиза решава кодът на ядрото, при DMS хешът на байтовете на файла. Винаги четим, задава се само от бекенда.")),

				field(null, SolrCore.CURSOR_DATE_FIELD_NAME, SolrFieldType.DATE, true, true, false, true, false, false,
						ALWAYS, false, BACKEND, 1,
						multilingual("Durchlauf-Zeiger", "Run marker", "Маркер за обхождане"),
						multilingual(
								"Wann ein Durchlauf dieses Dokument zuletzt angefasst hat, der Zeiger zum Durchiterieren aller Dokumente eines Kerns, weil Paging ans Limit kommt. In jedem Kern, setzt nur das Backend.",
								"When a run last touched this document, the marker for iterating over all documents of a core, because paging hits its limit. In every core, set only by the backend.",
								"Кога обхождане последно е докоснало този документ, маркерът за итериране през всички документи на ядро, защото страницирането достига лимита си. Във всяко ядро, задава се само от бекенда.")),

				field(null, SolrCore.USER_GRANTED_ACCESS_FIELD_NAME, SolrFieldType.STRING, true, true, true, true, false,
						false, NOBODY, false, BACKEND, 2,
						multilingual("Freigegeben für", "Granted to", "Споделено с"),
						multilingual(
								"Die guids der User, denen dieses Dokument per Dateifreigabe geöffnet ist. Der Freigabe-Service liest allein danach. Wer das Feld sieht, erkennt, dass die Datei freigegeben ist. In jedem Kern, setzt nur das Backend.",
								"The guids of the users who were granted this document. The grant service reads by this field alone. Whoever sees the field knows the file is shared. In every core, set only by the backend.",
								"guid-ите на потребителите, на които този документ е споделен. Услугата за споделяне чете само по това поле. Който вижда полето, знае, че файлът е споделен. Във всяко ядро, задава се само от бекенда.")),

				// ===== Versionskette =====

				field(CORE_KEY, "versionChain", SolrFieldType.STRING, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 10,
						multilingual("Versionskette", "Version chain", "Верига от версии"),
						multilingual(
								"Alle Fassungen einer Datei tragen dieselbe UUID. Auflösbar: liefert die Kette als Liste, jedes Glied mit der Lesegrenze des Users. Setzt nur das Backend, bei einer neuen Fassung aus dem Parameter des Uploads.",
								"All versions of a file carry the same UUID. Resolvable: returns the chain as a list, each member within the user's read limit. Set only by the backend, for a new version from the upload parameter.",
								"Всички версии на един файл носят един и същ UUID. Може да се разрешава: връща веригата като списък, всяко звено в границата за четене на потребителя. Задава се само от бекенда, при нова версия от параметъра на качването.")),

				field(CORE_KEY, "version", SolrFieldType.LONG, true, true, false, true, false, false, ALWAYS, false, BACKEND,
						11,
						multilingual("Fassung", "Version", "Версия"),
						multilingual(
								"Nummer der Fassung in ihrer Kette, 1 beim Upload, bei jeder neuen Fassung eins mehr. Setzt nur das Backend.",
								"Number of the version within its chain, 1 on upload, one more for every new version. Set only by the backend.",
								"Номер на версията в нейната верига, 1 при качване, с едно повече при всяка нова версия. Задава се само от бекенда.")),

				// ===== Rechte =====

				field(CORE_KEY, "readAcl", SolrFieldType.STRING, true, true, true, true, false, false, CONFIDENTIAL,
						false, BACKEND, 20,
						multilingual("Leserechte", "Read ACL", "Права за четене"),
						multilingual(
								"Die AR-Keys, die dieses Dokument lesen dürfen, aus der Dateiart. Nur mit einem Treffer darin kommt ein Dokument aus Solr. Schreibt der Hintergrundjob nach jedem Schreiben aus der Dateiart, nie direkt.",
								"The AR keys allowed to read this document, from the file sub class. Only a match here lets a document out of Solr. Written by the background job after every write from the file sub class, never directly.",
								"AR ключовете, които могат да четат този документ, от вида на файла. Само съвпадение тук пуска документ от Solr. Записва се от фоновата задача след всяко записване от вида на файла, никога директно.")),

				field(CORE_KEY, "writeAcl", SolrFieldType.STRING, true, true, true, true, false, false, CONFIDENTIAL,
						false, BACKEND, 21,
						multilingual("Schreibrechte", "Write ACL", "Права за запис"),
						multilingual(
								"Die AR-Keys, die dieses Dokument ändern dürfen, aus der Dateiart. Jeder Schreibvorgang an einem vorhandenen Dokument prüft dagegen. Schreiben impliziert kein Lesen. Schreibt der Hintergrundjob aus der Dateiart.",
								"The AR keys allowed to change this document, from the file sub class. Every write to an existing document checks against it. Writing does not imply reading. Written by the background job from the file sub class.",
								"AR ключовете, които могат да променят този документ, от вида на файла. Всяко записване в съществуващ документ се проверява срещу него. Записването не означава четене. Записва се от фоновата задача от вида на файла.")),

				field(CORE_KEY, "isPublic", SolrFieldType.BOOLEAN, true, true, false, true, false, false, ALWAYS, false,
						CLIENT, 22,
						multilingual("Internetfreigabe", "Public on the internet", "Публичен в интернет"),
						multilingual(
								"Ein öffentlicher Controller zeigt und liefert solche Dokumente auch ohne Anmeldung. Setzt und ändert nur, wer die Rolle DMS_PUBLIC hat.",
								"A public controller shows and serves such documents even without login. Set and changed only by holders of the role DMS_PUBLIC.",
								"Публичен контролер показва и доставя такива документи и без вход. Задава се и променя само от притежатели на ролята DMS_PUBLIC.")),

				field(CORE_KEY, "downloadGrantedAccess", SolrFieldType.STRING, true, true, true, true, false, false, ALWAYS,
						false, BACKEND, 23,
						multilingual("Abholscheine", "Pickup tokens", "Талони за изтегляне"),
						multilingual(
								"Die Hashes der Abholscheine: Download-Links mit Ablauf und Zähler. Der Link trägt das Original, der Controller hasht es und löst exakt danach auf; wer die Hashes sieht, kann nichts damit anfangen. Zähler und Ablauf stehen in Postgres. Setzt nur das Backend.",
								"The hashes of the pickup tokens: download links with expiry and counter. The link carries the original, the controller hashes it and resolves exactly by it; whoever sees the hashes cannot use them. Counter and expiry live in Postgres. Set only by the backend.",
								"Хешовете на талоните за изтегляне: връзки за изтегляне с срок и брояч. Връзката носи оригинала, контролерът го хешира и разрешава точно по него; който вижда хешовете, не може да ги използва. Броячът и срокът са в Postgres. Задава се само от бекенда.")),

				// ===== fachlich, vom Client =====

				field(CORE_KEY, "fileSubClassKey", SolrFieldType.STRING, true, true, false, true, true, false, ALWAYS,
						false, CLIENT, 30,
						multilingual("Dateiart", "File sub class", "Вид на файла"),
						multilingual(
								"Die Dateiart (FileSubClass.key). Aus ihr kommen readAcl und writeAcl, und sie entscheidet, welche Endungen erlaubt sind. Eine Versionskette hat durchgehend dieselbe Dateiart. Pflicht beim Anlegen, ein Wechsel braucht die Schreibrolle der neuen Dateiart.",
								"The file sub class (FileSubClass.key). It yields readAcl and writeAcl and decides which extensions are allowed. A version chain keeps one file sub class throughout. Required on create, a change needs the write role of the new sub class.",
								"Видът на файла (FileSubClass.key). От него идват readAcl и writeAcl и той решава кои разширения са позволени. Веригата от версии има един и същ вид навсякъде. Задължителен при създаване, смяна изисква ролята за запис на новия вид.")),

				field(CORE_KEY, "status", SolrFieldType.STRING, true, true, false, true, true, false, ALWAYS, false, CLIENT,
						31,
						multilingual("Status", "Status", "Статус"),
						multilingual(
								"Ein EntityState des Bereichs DMS: ACTIVE, ARCHIVED, PENDING_ADMIN, DELETED. User sehen nur ACTIVE und ARCHIVED, alles andere braucht eigene Rechte und eigene Controller. Weiches Löschen ist ein SET auf DELETED.",
								"An EntityState of the scope DMS: ACTIVE, ARCHIVED, PENDING_ADMIN, DELETED. Users see only ACTIVE and ARCHIVED, everything else needs its own rights and controllers. Soft delete is a SET to DELETED.",
								"EntityState от обхвата DMS: ACTIVE, ARCHIVED, PENDING_ADMIN, DELETED. Потребителите виждат само ACTIVE и ARCHIVED, всичко останало изисква собствени права и контролери. Мекото изтриване е SET на DELETED.")),

				field(CORE_KEY, "fileName", SolrFieldType.TEXT_EDGE, true, true, false, false, true, false, ALWAYS, false,
						CLIENT, 32,
						multilingual("Dateiname", "File name", "Име на файла"),
						multilingual(
								"Der Dateiname, wie der User ihn sieht, normalisiert: klein, Umlaute aufgelöst, nur a-z, 0-9, _ und -. Beim Anlegen aus dem Namen der hochgeladenen Datei, änderbar. Beim Tippen per Wortanfang findbar.",
								"The file name as the user sees it, normalised: lower case, umlauts resolved, only a-z, 0-9, _ and -. On create from the uploaded file's name, changeable. Findable by word prefix while typing.",
								"Името на файла, както го вижда потребителят, нормализирано: малки букви, умлаути разложени, само a-z, 0-9, _ и -. При създаване от името на качения файл, може да се променя. Откриваемо по префикс на дума при писане.")),

				field(CORE_KEY, "fileIndex", SolrFieldType.STRING, true, true, false, true, false, true, ALWAYS, false,
						CLIENT, 33,
						multilingual("Index", "Index", "Индекс"),
						multilingual(
								"Ein freier Index des Users, z. B. eine Belegnummer, exakt filterbar, mit Zwilling für die Vervollständigung. Beim Merge derselben Bytes muss er zum vorhandenen passen.",
								"A free index of the user, e.g. a document number, exactly filterable, with a twin for autocompletion. When merging the same bytes it must match the existing one.",
								"Свободен индекс на потребителя, напр. номер на документ, точно филтрируем, с близнак за автодовършване. При сливане на същите байтове трябва да съвпада със съществуващия.")),

				field(CORE_KEY, "fileDescription", SolrFieldType.TEXT_EDGE, true, true, false, false, false, false, ALWAYS,
						false, CLIENT, 34,
						multilingual("Beschreibung", "Description", "Описание"),
						multilingual(
								"Mehrsprachiges JSON, wortweise per Präfix suchbar (Edge-N-Gram im Feld selbst). Beim Merge derselben Bytes wird je Sprache hinten angehängt, wenn der Text nicht schon drinsteht.",
								"Multilingual JSON, searchable by word prefix (edge n-gram in the field itself). When merging the same bytes, each language is appended at the end unless the text is already there.",
								"Многоезичен JSON, търсим по префикс на дума (edge n-gram в самото поле). При сливане на същите байтове всеки език се добавя накрая, ако текстът вече не е там.")),

				field(CORE_KEY, "documentLanguage", SolrFieldType.STRING, true, true, false, true, false, false, ALWAYS,
						false, CLIENT, 35,
						multilingual("Sprache des Inhalts", "Content language", "Език на съдържанието"),
						multilingual(
								"Ein Code aus der festen Liste (de, en, bg, …) oder leer. Nur bei Endungsgruppen mit Sprache (Dokumente, Videos, Anwendungsdateien). Schickt der Client nichts, erkennt das Backend sie aus dem Text von Tika, nie geraten.",
								"A code from the fixed list (de, en, bg, …) or empty. Only for extension groups that carry a language (documents, videos, application files). If the client sends nothing, the backend detects it from Tika's text, never guessed.",
								"Код от фиксирания списък (de, en, bg, …) или празно. Само за групи разширения с език (документи, видеа, файлове на приложения). Ако клиентът не изпрати нищо, бекендът го разпознава от текста на Tika, никога не се гадае.")),

				field(CORE_KEY, "documentCreatedAt", SolrFieldType.DATE, true, true, false, true, false, false, ALWAYS,
						false, CLIENT, 36,
						multilingual("Erstellt am", "Created at", "Създаден на"),
						multilingual(
								"Wann das Dokument fachlich entstanden ist, z. B. Rechnungsdatum oder Aufnahmedatum. Standard aus Tika, sonst der Upload-Zeitpunkt. Sortierung der Suche ohne Suchwort, Recency-Boost mit Suchwort.",
								"When the document was created in business terms, e.g. invoice date or capture date. Default from Tika, else the upload time. Sort order of the search without a search word, recency boost with one.",
								"Кога документът е създаден в делови смисъл, напр. дата на фактура или на заснемане. По подразбиране от Tika, иначе времето на качване. Сортиране на търсенето без дума за търсене, бонус за актуалност с такава.")),

				// ===== die Datei, vom Backend =====

				field(CORE_KEY, "fileExtension", SolrFieldType.STRING, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 40,
						multilingual("Endung", "Extension", "Разширение"),
						multilingual(
								"Endung nach dem letzten Punkt, klein, geprüft gegen den Katalog und die Endungen der Dateiart. Beim Anlegen aus dem Dateinamen, danach nie.",
								"Extension after the last dot, lower case, checked against the catalogue and the extensions of the file sub class. On create from the file name, never after.",
								"Разширение след последната точка, малки букви, проверено спрямо каталога и разширенията на вида на файла. При създаване от името на файла, никога след това.")),

				field(CORE_KEY, "fileSizeBytes", SolrFieldType.LONG, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 41,
						multilingual("Größe in Bytes", "Size in bytes", "Размер в байтове"),
						multilingual(
								"Die Größe der Bytes, beim Anlegen aus der Datei, danach nie.",
								"The size of the bytes, on create from the file, never after.",
								"Размерът на байтовете, при създаване от файла, никога след това.")),

				field(CORE_KEY, "mimeType", SolrFieldType.STRING, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 42,
						multilingual("MIME-Typ", "MIME type", "MIME тип"),
						multilingual(
								"Erkannt an den Bytes (Tika), nicht an der Endung. Beim Anlegen, danach nie.",
								"Detected from the bytes (Tika), not from the extension. On create, never after.",
								"Разпознат от байтовете (Tika), не от разширението. При създаване, никога след това.")),

				field(CORE_KEY, "sftpPath", SolrFieldType.STRING, false, true, false, false, false, false, NOBODY, true,
						BACKEND, 43,
						multilingual("Ablagepfad", "Storage path", "Път за съхранение"),
						multilingual(
								"Wo die Bytes auf dem SFTP liegen, relativ zur Wurzel. Der Download liest ihn aus genau diesem Dokument. Geht nie zum Client, auch nicht mit Vollsicht.",
								"Where the bytes live on the SFTP, relative to the root. The download reads it from exactly this document. Never goes to the client, not even with full sight.",
								"Къде са байтовете на SFTP, спрямо корена. Изтеглянето го чете точно от този документ. Никога не отива при клиента, дори с пълен изглед.")),

				field(CORE_KEY, "searchableWords", SolrFieldType.TEXT_SUCHE, true, true, false, false, false, false,
						NOBODY, true, BACKEND, 44,
						multilingual("Volltext", "Full text", "Пълен текст"),
						multilingual(
								"Der Volltext aus Tika: jedes Wort einzeln, Stammform, Komposita zerlegt, mit Synonymen suchbar. Nur für die Suche: Der Treffergrund nennt das Feld, nie einen Ausschnitt. Geht nie zum Client. stored, weil Atomic Update das Dokument aus den stored fields neu zusammensetzt.",
								"The full text from Tika: every word on its own, stemmed, compounds split, searchable with synonyms. Only for the search: the hit reason names the field, never an excerpt. Never goes to the client. stored, because atomic update rebuilds the document from the stored fields.",
								"Пълният текст от Tika: всяка дума поотделно, в основна форма, сложните думи разделени, търсим със синоними. Само за търсенето: причината за попадение назовава полето, никога откъс. Никога не отива при клиента. stored, защото atomic update пресъздава документа от stored полетата.")),

				field(CORE_KEY, "bildVorschauBase64Png", SolrFieldType.STRING, false, true, false, false, false, false,
						ALWAYS, false, BACKEND, 45,
						multilingual("Vorschaubild", "Preview image", "Изображение за преглед"),
						multilingual(
								"Vorschaubild 200 x 200, PNG mit Transparenz, Base64 ohne Präfix, damit die Trefferliste es direkt aus der Solr-Antwort zeigt. Nur bei Endungen mit Vorschaubild im Katalog. Beim Anlegen, danach nie.",
								"Preview image 200 x 200, PNG with transparency, Base64 without prefix, so the hit list shows it straight from the Solr response. Only for extensions with a preview in the catalogue. On create, never after.",
								"Изображение за преглед 200 x 200, PNG с прозрачност, Base64 без префикс, за да го покаже списъкът с попадения директно от отговора на Solr. Само за разширения с преглед в каталога. При създаване, никога след това.")),

				// ===== Herkunft, Stempel, Zähler =====

				field(CORE_KEY, "uploader", SolrFieldType.STRING, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 50,
						multilingual("Hochgeladen von", "Uploaded by", "Качен от"),
						multilingual(
								"guid des Users, der die Datei hochgeladen hat. Beim Anlegen aus dem Token, danach nie.",
								"guid of the user who uploaded the file. On create from the token, never after.",
								"guid на потребителя, качил файла. При създаване от токена, никога след това.")),

				field(CORE_KEY, "documentUploadedAt", SolrFieldType.DATE, true, true, false, true, false, false, ALWAYS,
						false, BACKEND, 51,
						multilingual("Hochgeladen am", "Uploaded at", "Качен на"),
						multilingual(
								"Wann die Datei hochgeladen wurde. Beim Anlegen, danach nie.",
								"When the file was uploaded. On create, never after.",
								"Кога файлът е качен. При създаване, никога след това.")),

				field(CORE_KEY, "changingUsers", SolrFieldType.STRING, true, true, true, true, false, false, ALWAYS,
						false, BACKEND, 52,
						multilingual("Geändert von", "Changed by", "Променен от"),
						multilingual(
								"guids aller User, die je an den Metadaten geschrieben haben, ohne Doppelte. Stempel bei jedem Schreiben eines Menschen, nicht beim Download-Zähler.",
								"guids of all users who ever wrote to the metadata, without duplicates. Stamp on every write by a human, not on the download counter.",
								"guid-ите на всички потребители, писали някога в метаданните, без дубликати. Печат при всяко записване от човек, не при брояча на изтеглянията.")),

				field(CORE_KEY, "lastChangedAt", SolrFieldType.DATE, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 53,
						multilingual("Zuletzt geändert am", "Last changed at", "Последно променен на"),
						multilingual(
								"Wann zuletzt ein Mensch an den Metadaten geschrieben hat. Stempel bei jedem Schreiben eines Menschen, nicht beim Download-Zähler.",
								"When a human last wrote to the metadata. Stamp on every write by a human, not on the download counter.",
								"Кога човек за последно е писал в метаданните. Печат при всяко записване от човек, не при брояча на изтеглянията.")),

				field(CORE_KEY, "downloadCount", SolrFieldType.LONG, true, true, false, true, false, false, ALWAYS, false,
						BACKEND, 54,
						multilingual("Downloads", "Downloads", "Изтегляния"),
						multilingual(
								"Wie oft die Bytes heruntergeladen oder im Viewer geöffnet wurden. Buchhaltung des Backends: INC 1 je Download, kein Stempel, keine Revision, kein Schreibcheck.",
								"How often the bytes were downloaded or opened in the viewer. Backend bookkeeping: INC 1 per download, no stamp, no revision, no write check.",
								"Колко пъти байтовете са изтеглени или отворени в прегледа. Счетоводство на бекенда: INC 1 на изтегляне, без печат, без ревизия, без проверка за запис.")),

				field(CORE_KEY, "fileIdRevisionNo", SolrFieldType.LONG, true, true, false, true, false, false, ALWAYS,
						false, BACKEND, 55,
						multilingual("Revision", "Revision", "Ревизия"),
						multilingual(
								"Zähler der Revisionen: 0 beim Anlegen, plus eins bei jedem revisionierten Schreiben. Vor einem solchen Schreiben wird der ganze Stand als ZIP gesichert, so ist ein Zurückladen möglich und nachvollziehbar, wer wann was geändert hat.",
								"Revision counter: 0 on create, plus one for every revisioned write. Before such a write the whole state is saved as a ZIP, so a restore is possible and it is traceable who changed what and when.",
								"Брояч на ревизиите: 0 при създаване, плюс едно при всяко ревизирано записване. Преди такова записване цялото състояние се запазва като ZIP, така е възможно връщане и се проследява кой кога какво е променил.")),

				field(CORE_KEY, "ki", SolrFieldType.BOOLEAN, true, true, false, true, false, false, ALWAYS, false, BACKEND,
						56,
						multilingual("KI", "AI", "ИИ"),
						multilingual(
								"Vorgehalten für später: ob sich das Dokument als KI-Trainingsdatei eignet oder für eine KI-Plausibilisierung. Vorerst immer false.",
								"Reserved for later: whether the document is suitable as AI training data or for an AI plausibility check. Always false for now.",
								"Запазено за по-късно: дали документът е подходящ за обучение на ИИ или за проверка на правдоподобност от ИИ. Засега винаги false.")));
	}

	private static SolrFieldCodeDefinition field(String coreKey, String name, SolrFieldType type, boolean indexed,
			boolean stored, boolean multiValued, boolean docValues, boolean required, boolean suggest,
			List<String> readAccessRoleKeys, boolean backendOnly, SolrFieldClientWriteRule clientWriteRule,
			int listingPosition, String displayName, String description) {
		return new SolrFieldCodeDefinition(coreKey, name, type, indexed, stored, multiValued, docValues, required,
				suggest, readAccessRoleKeys, backendOnly, clientWriteRule, displayName, description, listingPosition);
	}

	/** Mehrsprachiges JSON, immer de, en und bg */
	private static String multilingual(String de, String en, String bg) {
		return "{\"de\":\"" + json(de) + "\",\"en\":\"" + json(en) + "\",\"bg\":\"" + json(bg) + "\"}";
	}

	private static String json(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}

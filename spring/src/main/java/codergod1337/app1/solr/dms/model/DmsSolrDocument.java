package codergod1337.app1.solr.dms.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DUMMY: So sieht ein Dokument im Kern DMS aus. Noch kein Code benutzt diese Klasse, sie ist das Bild, gegen das wir
 * die Vorgänge und danach die Rechte entwerfen. Ob das echte Dokument später eine Klasse oder eine Map ist, entscheiden
 * wir, wenn die Vorgänge stehen.
 *
 * Jedes Feld hier wird vom handgemachten Schema-Initialisierer als Zeile in Postgres (solr_field) angelegt und von dort
 * in das Solr-Schema des Kerns getragen. Nichts davon legt jemand im Kern-Tab von Hand an. Drei Gruppen:
 *
 * 1. Eingebaut, in jedem Kern gleich: id, cursorDate, userGrantedAccess.
 * 2. Hooks, in jedem Kern gleich: je Zeile in solr_hook ein Feld mit dem Hook-Key als Feldname, feste Einstellungen.
 *    Weil die Hooks in jedem Kern dieselben Felder sind, ist das Durchsuchen und Auflösen der Hooks in jedem
 *    Kern-Service identisch.
 * 3. Felder des DMS: alles andere unten.
 *
 * Je Feld stehen drei Zeilen, die der Initialisierer später nach Postgres schreibt:
 *
 * Solr:      Typ aus dem Basis-Configset (string, boolean, plong, pdate, text_edge, text_suche), indexed, stored, docValues,
 *            einwertig oder multiValued, required, suggest (dann gibt es den Zwilling nameSuggested für die
 *            Vervollständigung beim Tippen, nicht stored).
 * Lesen:     MINIMUM      jeder, der das Dokument sieht, bekommt das Feld
 *            ERWEITERT    nur mit Vollsicht (DMS_FULL, DMS_SUCHE) oder einer Freigabe für dieses Feld
 *            NUR_BACKEND  geht nie zum Client, das Backend liest es für sich
 * Schreiben: CLIENT       der Client setzt es beim Anlegen und ändert es beim Bearbeiten, mit der genannten Regel
 *            BACKEND      nur das Backend, ein Wert vom Client wird ignoriert
 *
 * Die Dokumentgrenze (wer das Dokument überhaupt sieht) steht nicht am Feld, sondern im Dokument selbst: readAcl oder
 * userGrantedAccess, dazu der Status. Die Feldzeilen oben gelten erst, wenn ein User durch diese Grenze ist.
 */
public class DmsSolrDocument {

	// sha256 hash in hex der binärdatei
	public String id;			// BESONDERHEIT nach id soll man Auflösen können unique!

	// Dateiversionierungskette
	public String versionChain;	// BESONDERHEIT: auch versionChain soll auflösbar sein, und man bekommt eine Liste an DMS-docs die zu einer Kette gehören (sofern man zu den dateien read rechte hat)
	// das wird nur vom backend geschrieben, bei jedem neuen element zu einer versionskette wird der counter um eins erhöht
	public Long version;

	public Instant cursorDate;	// Wird zum zurchiterieren aller Dokumente eines Kerns verwendet, da pagination ans limit kommt!

	//FREIGABEN an USERS direkt per GUID gleichwertig zu readACL Match
	public List<String> userGrantedAccess;	// BESONDERHEIT Ein Users muss seine eigenen Freigaben sehen können
	// Wird ein Match benötigt um Dokument überhaupt aus solr zu bekommen
	public List<String> readAcl;	// leitet sich direkt aus fsc ab. dieses feld wird nie "direkt" beschrieben, sondern immer aus einem fsc datenbank abruf und die dort hinterlegten treffer werden dann in die liste geschrieben
	// wird benötigt, um schreibvorgänge wie bearbeiten von hooks an einem Solr Dokument im jeweiligen Solr Kern zu tätigen
	public List<String> writeAcl;	// leitet sich direkt aus fsc ab. dieses feld wird nie "direkt" beschrieben, sondern immer aus einem fsc datenbank abruf und die dort hinterlegten treffer werden dann in die liste geschrieben
	// Solche Freigaben machen das dokument an einem public Controller auch für UNKNOWN user (=ohne jwt) anzeig- und downloadbar
	public Boolean isPublic;

	// Dieses Feld (kurz fsc) definiert in der Datenbank um welche dokumentklasse es sich handelt, daraus werden auch die werte für readacl und writeacl gesetzt!
	public String fileSubClassKey;

	// Dient der veranschaulichung, sprich aktiv = aktuell oder veraltet, wird auch zum ausblenden genutzt wenn der status != active oder achived ist. dann sind spezielle rechte UND auch spezielle controller/ services nötig
	public String status;



	// normale Metadaten der Binärdatei
	public String fileName;
	public String fileIndex;
	// extra mit edge n gram aufbereitet suchbar
	public String fileDescription;
	// für dokumente, also word, pdf etc... dateien soll aus tika automatisch bestimmt und gesetzt werden
	public String documentLanguage;
	public Instant documentCreatedAt;
	public String fileExtension;
	public Long fileSizeBytes;
	public String mimeType;
	public String sftpPath;
	// auch aus tika, ist optional soll suchwörter extrahieren samt stemming zumindest für deutsch BESONDERHEIT: teil davon ist indexed und für suche optimiert muss aus speichergründen nicht in der response mitgeliefert werden, der uster muss aber sehen dass der match ggf. aus searchableWords kommt
	public String searchableWords;
	// Für bilder erstellen wir eine extra bas64 codierte vorschau, dass wir in den suchtreffern ohne weiteren db call ein vorschaubild anzeigen können
	public String bildVorschauBase64Png;
	// guid des current users, der die datei uploadet
	public String uploader;
	// Zeitpunkt des hochladens der Datei
	public Instant documentUploadedAt;
	// Eine liste an User.guid die die Metadaten im Solr Dms Dokument geändert haben
	public List<String> changingUsers;
	// Zeitpunkt der letzten ÄNDERUNG
	public Instant lastChangedAt;
	// Bei jedem Download oder laden für den Viewer wird dieser counter um eins erhöht
	public Long downloadCount;
	// bei schreibvorgängen die wir als Revisionsnötig erachten wird eine abbild des dms dokuments erstellt so ist ggf. ein Rollback möchglich und nachverfolgbar wer wann was in den metadaten geändert hat
	public Long fileIdRevisionNo;
	// Optional für später, sollte sich dieses dokument als KI trainingsdatei eignen ODER für eine spätere KI Plausibilisierung halten wir dieses Flag vor
	public Boolean ki;



	// HOOKS: Referenzen/ Suchadressen für ein Dokument eines Solr Kerns wie artikelNummer, kundenNummer
	public Map<String, List<String>> hookValuesByHookKey;

}

package codergod1337.app1.file.filedata.service;

import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.model.FileExtensionCollection;
import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import java.util.List;
import java.util.Locale;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.stereotype.Service;

/**
 * Die Sprache eines Dokuments. Ob eine Datei überhaupt eine hat, sagt ihre Endungsgruppe
 * (FileExtensionCollection.documentLanguageApplies): Dokumente ja, Bilder, Archive und CAD nein. Erlaubt ist null oder
 * ein Code aus CODES, dieselbe Liste wie DmsDocumentLanguage.ts im Frontend. Wählt der User keine, erkennt dieser
 * Service sie aus dem Text von Tika (Optimaize, offline, Profile einmal beim Start geladen). Die Wahl des Users
 * schlägt die Erkennung, es gibt nie einen geratenen Standard.
 */
@Service
public class DocumentLanguageService {

	/** Die 18 gängigsten Sprachen als ISO-639-1-Code */
	public static final List<String> CODES = List.of("de", "en", "bg", "ar", "cs", "el", "es", "fr", "hu", "it", "ja",
			"nl", "pl", "pt", "ro", "ru", "tr", "zh");

	/** Mehr Text macht die Erkennung nicht sicherer, nur langsamer: Ein Handbuch hat Millionen Zeichen. */
	private static final int MAX_TEXT_LENGTH = 20_000;

	/** Darunter rät die Erkennung: Ein paar Wörter aus einer Tabelle sagen nichts über die Sprache. */
	private static final int MIN_TEXT_LENGTH = 40;

	private final FileExtensionService fileExtensionService;
	private final FileExtensionCollectionService fileExtensionCollectionService;
	private final LanguageDetector languageDetector = new OptimaizeLangDetector().loadModels();

	public DocumentLanguageService(FileExtensionService fileExtensionService,
			FileExtensionCollectionService fileExtensionCollectionService) {
		this.fileExtensionService = fileExtensionService;
		this.fileExtensionCollectionService = fileExtensionCollectionService;
	}

	public static String normalizeCode(String code) {
		// klein und ohne Leerraum, so steht der Code in Solr
		return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
	}

	public boolean documentLanguageAppliesToFileExtension(String fileExtension) {
		// Hat eine Datei mit dieser Endung eine Sprache? Ohne Endung, ohne Katalogeintrag, ohne Gruppe oder in einer
		// Gruppe ohne Sprache: nein
		if (fileExtension == null || fileExtension.isEmpty()) {
			return false;
		}
		for (FileExtension candidate : fileExtensionService.getAllFileExtensions()) {
			if (!candidate.getExtension().equals(fileExtension)) {
				continue;
			}
			String collectionKey = candidate.getFileExtensionCollectionKey();
			if (collectionKey == null) {
				return false;
			}
			for (FileExtensionCollection collection : fileExtensionCollectionService.getAllFileExtensionCollections()) {
				if (collection.getKey().equals(collectionKey)) {
					return collection.isDocumentLanguageApplies();
				}
			}
			return false;
		}
		return false;
	}

	public String problemWithDocumentLanguage(String code, String fileExtension) {
		// Der Upload ruft das mit dem Code des Clients: null, wenn alles passt, sonst die Beanstandung für die 400
		String normalized = normalizeCode(code);
		if (normalized == null || normalized.isEmpty()) {
			return null;
		}
		if (!documentLanguageAppliesToFileExtension(fileExtension)) {
			return "documentLanguage: Dateien mit der Endung " + (fileExtension == null || fileExtension.isEmpty() ? "(keine)"
					: fileExtension) + " haben keine Sprache";
		}
		if (!CODES.contains(normalized)) {
			return "documentLanguage " + code + " ist kein Code aus der Liste";
		}
		return null;
	}

	public String detectDocumentLanguageCode(String text) {
		// Die Sprache aus dem Text von Tika, nur wenn die Erkennung sicher ist und der Code in unserer Liste steht; sonst
		// null, nie ein geratener Standard
		if (text == null || text.length() < MIN_TEXT_LENGTH) {
			return null;
		}
		String sample = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
		LanguageResult result = languageDetector.detect(sample);
		if (result == null || !result.isReasonablyCertain()) {
			return null;
		}
		String code = normalizeCode(result.getLanguage());
		return CODES.contains(code) ? code : null;
	}

}

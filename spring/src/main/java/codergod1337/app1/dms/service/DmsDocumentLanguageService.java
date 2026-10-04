package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsDocumentLanguages;
import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.model.FileExtensionCollection;
import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import java.util.Objects;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Die Sprache eines DMS-Dokuments (Vorgabe 2026-10-03). Ob eine Datei überhaupt eine hat, sagt ihre Endungsgruppe
 * (FileExtensionCollection.documentLanguageApplies): Dokumente, Videos und Anwendungsdateien ja, Bilder, Archive und CAD
 * nein. Erlaubt ist null oder ein Code aus DmsDocumentLanguages. Wählt der User keine, erkennt dieser Service sie aus
 * dem Text, den Tika geliefert hat (Optimaize, offline, Profile einmal beim Start geladen). Die Wahl des Users schlägt
 * die Erkennung, und es gibt nie einen geratenen Standard.
 */
@Service
public class DmsDocumentLanguageService {

	private static final Logger log = LoggerFactory.getLogger(DmsDocumentLanguageService.class);

	/** Mehr Text macht die Erkennung nicht sicherer, nur langsamer: Ein Handbuch hat Millionen Zeichen. */
	private static final int MAX_TEXT_LENGTH = 20_000;
	/** Darunter rät die Erkennung: Ein paar Wörter aus einer Tabelle sagen nichts über die Sprache. */
	private static final int MIN_TEXT_LENGTH = 40;

	private final FileExtensionService fileExtensionService;
	private final FileExtensionCollectionService fileExtensionCollectionService;
	private final LanguageDetector languageDetector = new OptimaizeLangDetector().loadModels();

	public DmsDocumentLanguageService(FileExtensionService fileExtensionService,
			FileExtensionCollectionService fileExtensionCollectionService) {
		this.fileExtensionService = fileExtensionService;
		this.fileExtensionCollectionService = fileExtensionCollectionService;
	}

	/** Hat eine Datei mit dieser Endung eine Sprache? Ohne Endung, ohne Gruppe oder in einer Gruppe ohne Sprache nein. */
	public boolean documentLanguageAppliesToFileExtension(String fileExtension) {
		if (fileExtension == null || fileExtension.isEmpty()) {
			return false;
		}
		String collectionKey = fileExtensionService.getAllFileExtensions().stream()
				.filter(candidate -> fileExtension.equals(candidate.getExtension()))
				.map(FileExtension::getFileExtensionCollectionKey)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
		if (collectionKey == null) {
			return false;
		}
		return fileExtensionCollectionService.getAllFileExtensionCollections().stream()
				.filter(candidate -> collectionKey.equals(candidate.getKey()))
				.findFirst()
				.map(FileExtensionCollection::isDocumentLanguageApplies)
				.orElse(false);
	}

	/**
	 * Was an einem Code vom Client nicht stimmt, als Satz für die 400, oder null, wenn er in Ordnung ist. null als Code
	 * ist immer in Ordnung (keine Sprache). In einer Gruppe ohne Sprache ist jeder Code ein Fehler, in einer mit Sprache
	 * muss er aus der Liste sein.
	 */
	public String problemWithDocumentLanguage(String code, String fileExtension) {
		if (code == null) {
			return null;
		}
		if (!documentLanguageAppliesToFileExtension(fileExtension)) {
			return "documentLanguage: Dateien mit der Endung " + (fileExtension == null || fileExtension.isEmpty() ? "(keine)" : fileExtension)
					+ " haben keine Sprache, ihre Endungsgruppe sieht keine vor";
		}
		if (!DmsDocumentLanguages.isKnownCode(DmsDocumentLanguages.normalizeCode(code))) {
			return "documentLanguage: " + code + " ist keine Sprache aus der Liste " + DmsDocumentLanguages.CODES;
		}
		return null;
	}

	/**
	 * Der Code aus DmsDocumentLanguages, den die Erkennung für sicher hält, sonst null: zu wenig Text, unsicher oder eine
	 * Sprache außerhalb der Liste.
	 */
	public String detectDocumentLanguageCode(String text) {
		if (text == null) {
			return null;
		}
		String sample = text.strip();
		if (sample.length() < MIN_TEXT_LENGTH) {
			return null;
		}
		if (sample.length() > MAX_TEXT_LENGTH) {
			sample = sample.substring(0, MAX_TEXT_LENGTH);
		}
		LanguageResult result = languageDetector.detect(sample);
		if (result == null || !result.isReasonablyCertain()) {
			return null;
		}
		String code = DmsDocumentLanguages.normalizeCode(result.getLanguage());
		if (!DmsDocumentLanguages.isKnownCode(code)) {
			log.debug("Spracherkennung: {} liegt außerhalb der Liste, das Dokument bleibt ohne Sprache", result.getLanguage());
			return null;
		}
		return code;
	}

}

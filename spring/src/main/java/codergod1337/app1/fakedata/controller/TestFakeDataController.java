package codergod1337.app1.fakedata.controller;

import codergod1337.app1.fakedata.model.TestFakeDataRequest;
import codergod1337.app1.fakedata.model.TestFakeDataStatus;
import codergod1337.app1.fakedata.service.TestFakeDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Das Modul testfakedata (User 2026-10-14): Testdaten für die Solr-Kerne erzeugen, um früh die Performance
 * einzuschätzen. Nur ADMIN, das prüft der Service. Ein Lauf zur Zeit im Hintergrund, die Maske fragt den Stand ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/testfakedata")
public class TestFakeDataController {

	private final TestFakeDataService testFakeDataService;

	public TestFakeDataController(TestFakeDataService testFakeDataService) {
		this.testFakeDataService = testFakeDataService;
	}

	/**
	 * Startet einen Lauf: {articleCount, customerCount, supplierCount, documentCount, documentsPerArticleMean,
	 * documentsPerArticleDeviation}. 400 bei unbrauchbaren Zahlen, 409, wenn schon einer läuft.
	 */
	@PostMapping("/start")
	public TestFakeDataStatus start(@RequestBody TestFakeDataRequest request) {
		return testFakeDataService.start(request);
	}

	/** Der Stand des letzten Laufs je Phase, mit Solr-Dokumenten je Sekunde */
	@GetMapping("/status")
	public TestFakeDataStatus getStatus() {
		return testFakeDataService.getStatus();
	}

	/** Bricht den laufenden Lauf nach dem aktuellen Eintrag ab */
	@PostMapping("/cancel")
	public TestFakeDataStatus cancel() {
		return testFakeDataService.cancel();
	}

}

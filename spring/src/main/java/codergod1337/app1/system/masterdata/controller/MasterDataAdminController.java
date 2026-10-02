package codergod1337.app1.system.masterdata.controller;

import codergod1337.app1.system.masterdata.model.MasterDataExport;
import codergod1337.app1.system.masterdata.model.MasterDataExportRequest;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck;
import codergod1337.app1.system.masterdata.model.MasterDataImportRequest;
import codergod1337.app1.system.masterdata.service.MasterDataExportService;
import codergod1337.app1.system.masterdata.service.MasterDataImportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Export und Import der Stammdaten. Alles POST: Die Auswahl steht im Body, nicht in der URL.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize in den Services sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/masterdata")
public class MasterDataAdminController {

	private final MasterDataExportService masterDataExportService;
	private final MasterDataImportService masterDataImportService;

	public MasterDataAdminController(MasterDataExportService masterDataExportService,
			MasterDataImportService masterDataImportService) {
		this.masterDataExportService = masterDataExportService;
		this.masterDataImportService = masterDataImportService;
	}

	/** {"sections": ["ACCESS_ROLES", "USERS", …]}: diese Bereiche als ZIP (Base64). 400 ohne Bereiche. */
	@PostMapping("/export")
	public MasterDataExport exportMasterData(@RequestBody MasterDataExportRequest exportData) {
		if (exportData.sections() == null || exportData.sections().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Bereiche gewählt");
		}
		return masterDataExportService.exportMasterData(exportData.sections());
	}

	/** Probelauf: was der Import mit dieser Auswahl täte. Schreibt nichts. 400 ohne ZIP. */
	@PostMapping("/import/check")
	public MasterDataImportCheck checkMasterDataImport(@RequestBody MasterDataImportRequest importData) {
		if (importData.zip() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip fehlt");
		}
		return masterDataImportService.checkMasterDataImport(importData.zip(), importData.sections(),
				importData.usersConflictDecisions());
	}

	/** Der Import: schreibt alles Neue der angehakten Bereiche. 400 ohne ZIP, ohne Bereiche oder wenn etwas blockiert. */
	@PostMapping("/import")
	public MasterDataImportCheck importMasterData(@RequestBody MasterDataImportRequest importData) {
		if (importData.zip() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip fehlt");
		}
		if (importData.sections() == null || importData.sections().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Bereiche gewählt");
		}
		return masterDataImportService.importMasterData(importData.zip(), importData.sections(),
				importData.usersConflictDecisions());
	}

}

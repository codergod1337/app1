package codergod1337.app1.system.masterdata.controller;

import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Stammdaten in einem Request, auch ohne Anmeldung: Das Frontend lädt sie sofort beim Start. Später kommen MSC
 * dazu.
 */
@RestController
@RequestMapping("/api/rest/v1/public/masterdata")
public class MasterDataPublicController {

	private final AccessRoleService accessRoleService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final FileSubClassService fileSubClassService;
	private final FileExtensionService fileExtensionService;
	private final FileExtensionCollectionService fileExtensionCollectionService;

	public MasterDataPublicController(AccessRoleService accessRoleService,
			AccessRoleCollectionService accessRoleCollectionService, FileSubClassService fileSubClassService,
			FileExtensionService fileExtensionService, FileExtensionCollectionService fileExtensionCollectionService) {
		this.accessRoleService = accessRoleService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.fileSubClassService = fileSubClassService;
		this.fileExtensionService = fileExtensionService;
		this.fileExtensionCollectionService = fileExtensionCollectionService;
	}

	/**
	 * {accessRoles, accessRoleCollections, fileSubClasses, fileExtensions, fileExtensionCollections}, alle sortiert
	 * nach listingPosition
	 */
	@GetMapping
	public Map<String, Object> getMasterData() {
		return Map.of(
				"accessRoles", accessRoleService.getAllAccessRoles(),
				"accessRoleCollections", accessRoleCollectionService.getAllAccessRoleCollections(),
				"fileSubClasses", fileSubClassService.getAllFileSubClasses(),
				"fileExtensions", fileExtensionService.getAllFileExtensions(),
				"fileExtensionCollections", fileExtensionCollectionService.getAllFileExtensionCollections());
	}

}

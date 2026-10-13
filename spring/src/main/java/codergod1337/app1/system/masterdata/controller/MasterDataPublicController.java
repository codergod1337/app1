package codergod1337.app1.system.masterdata.controller;

import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.service.FileSubClassDestinationService;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.hook.service.SolrHookGroupService;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleService;
import codergod1337.app1.system.state.service.EntityStateService;
import java.util.LinkedHashMap;
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
	private final FileSubClassDestinationService fileSubClassDestinationService;
	private final FileExtensionService fileExtensionService;
	private final FileExtensionCollectionService fileExtensionCollectionService;
	private final SolrHookGroupService solrHookGroupService;
	private final SolrHookService solrHookService;
	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;
	private final EntityStateService entityStateService;

	public MasterDataPublicController(AccessRoleService accessRoleService,
			AccessRoleCollectionService accessRoleCollectionService, FileSubClassService fileSubClassService,
			FileSubClassDestinationService fileSubClassDestinationService, FileExtensionService fileExtensionService,
			FileExtensionCollectionService fileExtensionCollectionService, SolrHookGroupService solrHookGroupService,
			SolrHookService solrHookService, SolrCoreService solrCoreService, SolrFieldService solrFieldService,
			EntityStateService entityStateService) {
		this.accessRoleService = accessRoleService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.fileSubClassService = fileSubClassService;
		this.fileSubClassDestinationService = fileSubClassDestinationService;
		this.fileExtensionService = fileExtensionService;
		this.fileExtensionCollectionService = fileExtensionCollectionService;
		this.solrHookGroupService = solrHookGroupService;
		this.solrHookService = solrHookService;
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
		this.entityStateService = entityStateService;
	}

	/**
	 * {accessRoles, accessRoleCollections, fileSubClasses, fileExtensions, fileExtensionCollections, solrHookGroups,
	 * solrHooks, solrCores, solrFields, entityStates}, alle sortiert nach listingPosition, die Felder zuerst nach Kern
	 */
	@GetMapping
	public Map<String, Object> getMasterData() {
		Map<String, Object> masterData = new LinkedHashMap<>();
		masterData.put("accessRoles", accessRoleService.getAllAccessRoles());
		masterData.put("accessRoleCollections", accessRoleCollectionService.getAllAccessRoleCollections());
		masterData.put("fileSubClassDestinations", fileSubClassDestinationService.getAllFileSubClassDestinations());
		masterData.put("fileSubClasses", fileSubClassService.getAllFileSubClasses());
		masterData.put("fileExtensions", fileExtensionService.getAllFileExtensions());
		masterData.put("fileExtensionCollections", fileExtensionCollectionService.getAllFileExtensionCollections());
		masterData.put("solrHookGroups", solrHookGroupService.getAllSolrHookGroups());
		masterData.put("solrHooks", solrHookService.getAllSolrHooks());
		masterData.put("solrCores", solrCoreService.getAllSolrCores());
		masterData.put("solrFields", solrFieldService.getAllSolrFields());
		masterData.put("entityStates", entityStateService.getAllEntityStates());
		return masterData;
	}

}

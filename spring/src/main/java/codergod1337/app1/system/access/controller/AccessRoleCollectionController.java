package codergod1337.app1.system.access.controller;

import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lesen der AccessRoleCollections, für alle, auch ohne Login: Das Frontend braucht die Liste immer.
 * Anlegen, Ändern und Löschen: AccessRoleCollectionAdminController.
 */
@RestController
@RequestMapping("/api/rest/v1/accessrolecollection")
public class AccessRoleCollectionController {

	private final AccessRoleCollectionService accessRoleCollectionService;

	public AccessRoleCollectionController(AccessRoleCollectionService accessRoleCollectionService) {
		this.accessRoleCollectionService = accessRoleCollectionService;
	}

	/** Alle ARCs, sortiert nach listingPosition und key. */
	@GetMapping
	public List<AccessRoleCollection> getAllAccessRoleCollections() {
		return accessRoleCollectionService.getAllAccessRoleCollections();
	}

}

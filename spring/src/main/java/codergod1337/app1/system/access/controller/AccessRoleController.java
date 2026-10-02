package codergod1337.app1.system.access.controller;

import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.service.AccessRoleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lesen der AccessRoles, für alle, auch ohne Login: Das Frontend braucht die Liste immer.
 * Anlegen, Ändern und Löschen: AccessRoleAdminController.
 */
@RestController
@RequestMapping("/api/rest/v1/accessrole")
public class AccessRoleController {

	private final AccessRoleService accessRoleService;

	public AccessRoleController(AccessRoleService accessRoleService) {
		this.accessRoleService = accessRoleService;
	}

	/** Alle Rollen, sortiert nach listingPosition und key. */
	@GetMapping
	public List<AccessRole> getAllAccessRoles() {
		return accessRoleService.getAllAccessRoles();
	}

}

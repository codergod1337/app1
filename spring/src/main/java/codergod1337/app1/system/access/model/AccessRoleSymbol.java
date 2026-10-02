package codergod1337.app1.system.access.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Symbol einer AccessRole-Badge: aus welchem Paket und welches Symbol darin.
 * Gespeichert als JSON, z. B. {"pack":"tabler","id":"shield-lock"}.
 *
 * @param pack bootstrap (Bootstrap Icons), tabler (Tabler Icons) oder lucide (Lucide)
 * @param id Name des Symbols im Paket, z. B. shield-lock
 */
public record AccessRoleSymbol(

		@NotBlank
		@Pattern(regexp = "bootstrap|tabler|lucide")
		String pack,

		@NotBlank
		@Size(max = 100)
		@Pattern(regexp = "[a-z0-9-]+")
		String id) {
}

package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.UsersDetails;
import codergod1337.app1.system.user.service.UsersDetailsService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der User-Weg zu {@link UsersDetails}: jeder nur bei sich selbst. Fremde Details gibt es erst mit den fremden
 * Profilen, kundenNummer und lieferantenNummer setzt später der Admin-Weg.
 */
@RestController
@RequestMapping("/api/rest/v1/usersdetails")
public class UsersDetailsController {

	private final UsersDetailsService usersDetailsService;
	private final CurrentUsersProvider currentUsersProvider;

	public UsersDetailsController(UsersDetailsService usersDetailsService, CurrentUsersProvider currentUsersProvider) {
		this.usersDetailsService = usersDetailsService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Die eigenen Details, ohne Zeile leere. 200, 400 bei ungültiger guid, 403 bei fremder guid. */
	@GetMapping("/{usersGuid}")
	public UsersDetails getUsersDetails(@PathVariable("usersGuid") String usersGuidText) {
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid ist keine gültige guid");
		}
		UUID usersGuid = UUID.fromString(usersGuidText);

		if (!usersGuid.equals(currentUsersProvider.getCurrentUsersGuid())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur die eigenen Details dürfen gelesen werden");
		}

		return usersDetailsService.getUsersDetailsByUsersGuid(usersGuid);
	}

	/**
	 * Der User ändert seine eigenen Details: info, profilText (beide mehrsprachiges JSON), webseite, mobile, steam,
	 * discord, insta. Vollständiges Objekt, was fehlt, wird geleert. 200, 400 oder 403.
	 */
	@PutMapping
	public UsersDetails updateUsersDetails(@RequestBody Map<String, Object> changedUsersDetailsData) {
		String usersGuidText = changedUsersDetailsData.get("usersGuid") instanceof String usersGuidValue ? usersGuidValue : null;
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid fehlt oder ist keine gültige guid");
		}
		UUID usersGuid = UUID.fromString(usersGuidText);

		if (!usersGuid.equals(currentUsersProvider.getCurrentUsersGuid())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur die eigenen Details dürfen geändert werden");
		}

		String info = changedUsersDetailsData.get("info") instanceof String infoValue ? infoValue : null;
		String profilText = changedUsersDetailsData.get("profilText") instanceof String profilTextValue ? profilTextValue : null;
		String webseite = changedUsersDetailsData.get("webseite") instanceof String webseiteValue ? webseiteValue : null;
		String mobile = changedUsersDetailsData.get("mobile") instanceof String mobileValue ? mobileValue : null;
		String steam = changedUsersDetailsData.get("steam") instanceof String steamValue ? steamValue : null;
		String discord = changedUsersDetailsData.get("discord") instanceof String discordValue ? discordValue : null;
		String insta = changedUsersDetailsData.get("insta") instanceof String instaValue ? instaValue : null;

		// Die Längen wie in UsersDetails, sonst scheiterte erst das Speichern
		if (webseite != null && webseite.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "webseite ist länger als 255 Zeichen");
		}
		if (mobile != null && mobile.length() > 50) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mobile ist länger als 50 Zeichen");
		}
		if (steam != null && steam.length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "steam ist länger als 100 Zeichen");
		}
		if (discord != null && discord.length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discord ist länger als 100 Zeichen");
		}
		if (insta != null && insta.length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "insta ist länger als 100 Zeichen");
		}

		return usersDetailsService.updateUsersDetails(usersGuid, info, profilText, webseite, mobile, steam, discord,
				insta);
	}

}

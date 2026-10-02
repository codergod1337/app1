package codergod1337.app1.system.user.service;

import codergod1337.app1.system.user.model.UsersDetails;
import codergod1337.app1.system.user.repository.UsersDetailsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Info, Profiltext und Kontaktdaten eines Users. Die Zeile ist optional: Ein User ohne diese Angaben hat keine, sie
 * entsteht beim ersten Speichern. Lesen gibt dann leere Details zurück statt eines Fehlers.
 */
@Service
public class UsersDetailsService {

	private final UsersDetailsRepository usersDetailsRepository;

	public UsersDetailsService(UsersDetailsRepository usersDetailsRepository) {
		this.usersDetailsRepository = usersDetailsRepository;
	}

	/** Die Details eines Users. Gibt es keine Zeile, kommen leere Details zurück (nicht gespeichert). */
	@Transactional(readOnly = true)
	public UsersDetails getUsersDetailsByUsersGuid(UUID usersGuid) {
		return usersDetailsRepository.findById(usersGuid).orElseGet(() -> emptyUsersDetails(usersGuid));
	}

	/** Alle gespeicherten Details, z. B. für den Export der Stammdaten. User ohne Zeile fehlen. */
	@Transactional(readOnly = true)
	public List<UsersDetails> getAllUsersDetails() {
		return usersDetailsRepository.findAll();
	}

	/**
	 * Legt die Details eines Users mit allen Feldern an, z. B. beim Import. Der Zähler der Fehlversuche beginnt bei 0.
	 * 409, wenn der User schon Details hat: Vorhandenes wird nie überschrieben.
	 */
	@Transactional
	public UsersDetails createUsersDetails(UUID usersGuid, String info, String profilText, String webseite,
			String mobile, String steam, String discord, String insta, String kundenNummer, String lieferantenNummer) {
		if (usersDetailsRepository.existsById(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "der User hat schon Details");
		}
		return usersDetailsRepository.save(new UsersDetails(usersGuid, blankToNull(info), blankToNull(profilText),
				blankToNull(webseite), blankToNull(mobile), blankToNull(steam), blankToNull(discord), blankToNull(insta),
				blankToNull(kundenNummer), blankToNull(lieferantenNummer), 0L));
	}

	/**
	 * PUT: ersetzt info, profilText und die Kontaktfelder, was fehlt, wird geleert. kundenNummer, lieferantenNummer und
	 * pwUnsuccessfull bleiben unverändert, die setzt nur der Admin oder das System. Gibt es noch keine Zeile, wird sie
	 * angelegt.
	 */
	@Transactional
	public UsersDetails updateUsersDetails(UUID usersGuid, String info, String profilText, String webseite,
			String mobile, String steam, String discord, String insta) {
		UsersDetails usersDetails = usersDetailsRepository.findById(usersGuid)
				.orElseGet(() -> emptyUsersDetails(usersGuid));
		usersDetails.setInfo(blankToNull(info));
		usersDetails.setProfilText(blankToNull(profilText));
		usersDetails.setWebseite(blankToNull(webseite));
		usersDetails.setMobile(blankToNull(mobile));
		usersDetails.setSteam(blankToNull(steam));
		usersDetails.setDiscord(blankToNull(discord));
		usersDetails.setInsta(blankToNull(insta));
		return usersDetailsRepository.save(usersDetails);
	}

	/**
	 * Zieht die Details auf die neue guid um, wenn der Admin die guid eines Users ändert. Die guid ist hier der PK und
	 * lässt sich in JPA nicht ändern: neue Zeile mit denselben Werten, alte löschen. Gibt es keine, passiert nichts.
	 */
	@Transactional
	public void changeUsersDetailsGuid(UUID usersGuidCurrent, UUID usersGuidNew) {
		usersDetailsRepository.findById(usersGuidCurrent).ifPresent(oldUsersDetails -> {
			UsersDetails newUsersDetails = new UsersDetails(usersGuidNew, oldUsersDetails.getInfo(),
					oldUsersDetails.getProfilText(), oldUsersDetails.getWebseite(), oldUsersDetails.getMobile(),
					oldUsersDetails.getSteam(), oldUsersDetails.getDiscord(), oldUsersDetails.getInsta(),
					oldUsersDetails.getKundenNummer(), oldUsersDetails.getLieferantenNummer(),
					oldUsersDetails.getPwUnsuccessfull());
			usersDetailsRepository.delete(oldUsersDetails);
			usersDetailsRepository.save(newUsersDetails);
		});
	}

	/** Löscht die Details eines Users. Gibt es keine, passiert nichts. */
	@Transactional
	public void deleteUsersDetails(UUID usersGuid) {
		usersDetailsRepository.deleteById(usersGuid);
	}

	/** Leere Details, so sieht ein User ohne Zeile aus */
	private static UsersDetails emptyUsersDetails(UUID usersGuid) {
		return new UsersDetails(usersGuid, null, null, null, null, null, null, null, null, null, 0L);
	}

	/** Leere Texte werden zu {@code null}, damit ein geleertes Feld wirklich leer ist. */
	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}

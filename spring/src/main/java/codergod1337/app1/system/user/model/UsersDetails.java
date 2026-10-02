package codergod1337.app1.system.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;

/**
 * Alles am User, was nicht jeder sehen darf: Info, Profiltext, Kontaktdaten und die Bindung an Kunde oder
 * Lieferant. Getrennt von {@link Users}, damit das Lesen an eine AccessRole gebunden werden kann.
 *
 * Die Zeile ist optional: Ein User ohne diese Angaben hat hier keinen Eintrag. Verweist nur über {@code usersGuid}
 * auf {@link Users}: keine JPA-Beziehung.
 */
@Entity
@Table(name = "users_details")
public class UsersDetails {

	/** PK und zugleich Verweis auf {@code Users.guid}, also höchstens eine Zeile pro User. */
	@Id
	@Column(name = "users_guid", nullable = false, updatable = false)
	private UUID usersGuid;

	@Column(columnDefinition = "text")
	private String info;

	@Column(columnDefinition = "text")
	private String profilText;

	@Size(max = 255)
	@Column(length = 255)
	private String webseite;

	@Size(max = 50)
	@Column(length = 50)
	private String mobile;

	@Size(max = 100)
	@Column(length = 100)
	private String steam;

	@Size(max = 100)
	@Column(length = 100)
	private String discord;

	@Size(max = 100)
	@Column(length = 100)
	private String insta;

	/** Bindung an einen Kunden. {@code null} heißt keine Bindung. Nur der Admin darf sie ändern. */
	@Size(max = 50)
	@Column(length = 50)
	private String kundenNummer;

	/** Wie {@code kundenNummer}, für Lieferanten. Nur der Admin darf sie ändern. */
	@Size(max = 50)
	@Column(length = 50)
	private String lieferantenNummer;

	/**
	 * Zählt jede gescheiterte Passwortprüfung. Hochgezählt nur per Query, deshalb {@code updatable = false}:
	 * Das Speichern der ganzen Zeile überschreibt so nie einen parallelen Zählschritt.
	 */
	@Column(nullable = false, updatable = false)
	@ColumnDefault("0")
	private Long pwUnsuccessfull = 0L;

	/** Für JPA. */
	protected UsersDetails() {
	}

	/** Admin und Import: jedes Feld. Fehlt pwUnsuccessfull, beginnt der Zähler bei 0. */
	public UsersDetails(UUID usersGuid, String info, String profilText, String webseite, String mobile, String steam,
			String discord, String insta, String kundenNummer, String lieferantenNummer, Long pwUnsuccessfull) {
		this.usersGuid = usersGuid;
		this.info = info;
		this.profilText = profilText;
		this.webseite = webseite;
		this.mobile = mobile;
		this.steam = steam;
		this.discord = discord;
		this.insta = insta;
		this.kundenNummer = kundenNummer;
		this.lieferantenNummer = lieferantenNummer;
		this.pwUnsuccessfull = pwUnsuccessfull != null ? pwUnsuccessfull : 0L;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	public String getProfilText() {
		return profilText;
	}

	public void setProfilText(String profilText) {
		this.profilText = profilText;
	}

	public String getWebseite() {
		return webseite;
	}

	public void setWebseite(String webseite) {
		this.webseite = webseite;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getSteam() {
		return steam;
	}

	public void setSteam(String steam) {
		this.steam = steam;
	}

	public String getDiscord() {
		return discord;
	}

	public void setDiscord(String discord) {
		this.discord = discord;
	}

	public String getInsta() {
		return insta;
	}

	public void setInsta(String insta) {
		this.insta = insta;
	}

	public String getKundenNummer() {
		return kundenNummer;
	}

	public void setKundenNummer(String kundenNummer) {
		this.kundenNummer = kundenNummer;
	}

	public String getLieferantenNummer() {
		return lieferantenNummer;
	}

	public void setLieferantenNummer(String lieferantenNummer) {
		this.lieferantenNummer = lieferantenNummer;
	}

	public Long getPwUnsuccessfull() {
		return pwUnsuccessfull;
	}

}

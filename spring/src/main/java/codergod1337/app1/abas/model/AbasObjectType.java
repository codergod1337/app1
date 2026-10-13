package codergod1337.app1.abas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ein ABAS-Objekttyp, roh wie ABAS ihn in {@code content.data.meta.objectType} liefert, z. B. {@code obj:data:2:1} für
 * Artikel oder {@code obj:data:19:0} für Kontakte. Die Endziffern sind nicht ratbar, deshalb steht der String hier, wie
 * er kommt.
 *
 * Eine winzige Nachschlagetabelle, damit {@link AbasSternzeit} je Zeile nur ein smallint trägt statt zehn bis fünfzehn
 * Zeichen: bei 10^9 Sternzeiten sind das 12 GB Unterschied. Aufgelöst wird von Hand über den Service, kein JPA-FK.
 */
@Entity
@Table(name = "abas_object_type")
public class AbasObjectType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Short id;

	/** Der Typ-String roh wie von ABAS, eindeutig */
	@Column(name = "object_type", nullable = false, updatable = false, unique = true, length = 50)
	private String objectType;

	/** Für JPA und Jackson. */
	protected AbasObjectType() {
	}

	/** Ein neuer Typ, sobald er zum ersten Mal in einer Antwort auftaucht. Die id vergibt die Datenbank. */
	public AbasObjectType(String objectType) {
		this.objectType = objectType;
	}

	public Short getId() {
		return id;
	}

	public String getObjectType() {
		return objectType;
	}

}

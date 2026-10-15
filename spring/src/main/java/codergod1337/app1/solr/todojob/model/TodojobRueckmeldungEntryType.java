package codergod1337.app1.solr.todojob.model;

/**
 * Die Art eines Eintrags in der Liste rueckmeldung (User 2026-10-16): die acht Rückmeldungsarten, die ein Job verlangen
 * kann (rueckmeldungsArt), dazu COMMENT für einen Kommentar in der Rückmeldung und RETRACTED, das einen früheren Eintrag
 * per nr zurücknimmt, denn gelöscht wird nie. Fertigmelden geht, sobald ein nicht zurückgenommener Eintrag die Art des
 * Jobs trägt; LESEN verlangt keinen Eintrag.
 */
public enum TodojobRueckmeldungEntryType {

	LESEN(true, false),
	BOOL(true, false),
	TEXT(true, false),
	ZAHL(true, false),
	DATUM(true, false),
	DMS_DATEI(true, true),
	ARTIKELNUMMER(true, true),
	STERNZEIT(true, true),
	COMMENT(false, false),
	RETRACTED(false, false);

	private final boolean rueckmeldungsArt;
	private final boolean valueIsReference;

	TodojobRueckmeldungEntryType(boolean rueckmeldungsArt, boolean valueIsReference) {
		this.rueckmeldungsArt = rueckmeldungsArt;
		this.valueIsReference = valueIsReference;
	}

	/** Kann ein Job diese Art als Rückmeldung verlangen? */
	public boolean isRueckmeldungsArt() {
		return rueckmeldungsArt;
	}

	/** Trägt ein Eintrag dieser Art im value eine id eines anderen Kerns (DMS-id, Artikelnummer, Sternzeit)? */
	public boolean isValueReference() {
		return valueIsReference;
	}

}

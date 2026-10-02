package codergod1337.app1.system.access.model;

/** Verlaufsart einer Badge. Wie jede Art genau aussieht, bestimmt das Theme. */
public enum BadgeGradient {

	/** einfarbig, nur badgeGradientStart */
	FLAT,

	/** von oben nach unten */
	LINEAR,

	/** schräg über 45° */
	DIAGONAL,

	/** 3D gewölbt: Wende knapp über der Mitte, Lichtkante oben, dunkle Kante unten */
	BEVEL,

	/** von der Mitte nach außen */
	RADIAL,

	/** mehrere Stufen mit Glanzstreifen, wie gebürstetes Metall */
	METAL

}

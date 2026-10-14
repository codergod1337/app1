package codergod1337.app1.fakedata.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Die Wörter der Testdaten (User 2026-10-14): Artikel aus Produkt- und Werkzeugfamilien, die zusammenpassen, damit die
 * Namen ansatzweise einen Sinn ergeben. Jede Familie legt fest, welche Norm, welches Maß, welcher Werkstoff und welche
 * Ausführung zu ihr gehören, und schreibt ihre Beschreibung in eigenen Sätzen; ein Kugellager bekommt so nie
 * „DIN 933“. Personen-, Straßen- und Ortsnamen kommen aus Datafaker, hier stehen nur, was dessen deutscher Katalog
 * nicht kennt: Rechtsform und Branche einer Firma, deutsche Vorwahlen und die Funktionen der Ansprechpartner.
 *
 * Alles hängt allein am übergebenen Random: Derselbe Startwert ergibt denselben Artikel.
 */
public final class TestFakeDataWordLists {

	/** Artikelnummern mit dieser Vorsilbe sind Werkzeuge (User 2026-10-14) */
	public static final String TOOL_NUMBER_PREFIX = "997";

	private TestFakeDataWordLists() {
	}

	/**
	 * Eine Familie: die Produktart vorn im Namen, das Suchwort (Feld such), die Kurzform für die Verkaufsbezeichnung,
	 * ob ein Teil nach Zeichnung gefertigt wird, die Merkmale und die Sätze der Beschreibung. Die Sätze nehmen die
	 * gewählten Merkmale über {norm}, {size}, {material}, {finish} und {use} auf; ein Satz nennt nur Merkmale, die
	 * seine Familie hat. Eine leere Liste fehlt im Namen.
	 */
	public record Family(String productType, String searchWord, String shortName, boolean drawingPart,
			List<String> norms, List<String> sizes, List<String> materials, List<String> finishes, List<String> uses,
			List<String> properties, List<String> sentences) {
	}

	/** Ein erzeugter Artikel: die Werte der durchsuchten Felder und die Sätze, die ein Datenblatt über ihn schreibt */
	public record FakeArticle(String number, boolean tool, String name, String searchWord, String salesName,
			String drawingNumber, String remark, String description, List<String> dataSheetSentences) {
	}

	/** Ein erzeugter Firmenname und die Domain für die E-Mail-Adressen ihrer Leute */
	public record FakeCompanyName(String name, String domain) {
	}

	public static final List<Family> PRODUCT_FAMILIES = List.of(
			new Family("Sechskantschraube", "SCHRAUBE", "SKS", false,
					List.of("DIN 933", "DIN 931", "ISO 4017", "ISO 4014"),
					List.of("M5x16", "M6x20", "M6x30", "M8x25", "M8x40", "M10x30", "M10x45", "M12x50", "M12x80", "M16x60", "M20x80"),
					List.of("8.8", "10.9", "A2-70", "A4-80"),
					List.of("verzinkt", "blank", "feuerverzinkt", "zinklamellenbeschichtet", "schwarz brüniert"),
					List.of("Stahlbau", "Maschinenrahmen", "Fördertechnik", "Anlagenbau", "Vorrichtungsbau"),
					List.of("Gewindetoleranz 6g nach ISO 965", "Anzugsmoment nach VDI 2230 ermitteln", "Lieferung im Karton zu 100 Stück",
							"Werkstoffzeugnis 3.1 auf Anfrage"),
					List.of("Sechskantschraube nach {norm}, Festigkeitsklasse {material}, Oberfläche {finish}.",
							"Abmessung {size}, geeignet für {use}.")),
			new Family("Zylinderschraube", "SCHRAUBE", "ZYS", false,
					List.of("DIN 912", "ISO 4762", "DIN 7984"),
					List.of("M4x10", "M5x12", "M6x16", "M8x20", "M8x30", "M10x25", "M12x40"),
					List.of("8.8", "12.9", "A2-70"),
					List.of("verzinkt", "schwarz brüniert", "blank"),
					List.of("Vorrichtungsbau", "Werkzeugbau", "Maschinenbau", "Sondermaschinenbau"),
					List.of("Innensechskant, Kopf mit Rändel", "Lieferung im Karton zu 200 Stück"),
					List.of("Zylinderschraube mit Innensechskant nach {norm}, Festigkeitsklasse {material}, {finish}.",
							"Abmessung {size} für {use}.")),
			new Family("Sechskantmutter", "MUTTER", "SKM", false,
					List.of("DIN 934", "ISO 4032", "DIN 985", "ISO 7040"),
					List.of("M5", "M6", "M8", "M10", "M12", "M16", "M20", "M24"),
					List.of("8", "10", "A2-70", "A4-80"),
					List.of("verzinkt", "blank", "feuerverzinkt"),
					List.of("Stahlbau", "Schraubverbindungen im Maschinenbau", "Montage"),
					List.of("Bei DIN 985 selbstsichernd mit Polyamidring", "Lieferung lose im Karton"),
					List.of("Sechskantmutter {size} nach {norm}, Festigkeitsklasse {material}, {finish}.", "Für {use}.")),
			new Family("Scheibe", "SCHEIBE", "SCH", false,
					List.of("DIN 125-A", "ISO 7089", "DIN 9021", "DIN 433"),
					List.of("5,3", "6,4", "8,4", "10,5", "13", "17", "21"),
					List.of("St 140 HV", "A2", "A4"),
					List.of("verzinkt", "blank"),
					List.of("Schraubverbindungen", "Stahlbau", "Montage"),
					List.of("Härte 140 HV", "Lieferung im Beutel zu 500 Stück"),
					List.of("Flache Scheibe nach {norm}, Innendurchmesser {size} mm, Werkstoff {material}, {finish}.", "Für {use}.")),
			new Family("Gewindestift", "GEWINDESTIFT", "GWS", false,
					List.of("DIN 913", "DIN 914", "DIN 916", "ISO 4026"),
					List.of("M4x6", "M5x8", "M6x10", "M8x12", "M10x16"),
					List.of("45H", "A2"),
					List.of("brüniert", "blank"),
					List.of("Stellringe", "Naben", "Vorrichtungen"),
					List.of("Mit Kegelkuppe oder Ringschneide je nach Norm"),
					List.of("Gewindestift mit Innensechskant nach {norm}, {size}, Härteklasse {material}, {finish}.", "Zum Fixieren von {use}.")),
			new Family("Rillenkugellager", "LAGER", "RKL", false,
					List.of(),
					List.of("6201-2RS", "6202-2Z", "6204-2RS", "6205-2RS", "6206-2Z", "6305-2RS", "6008-2Z", "6010-2RS"),
					List.of("C3", "CN"),
					List.of(),
					List.of("Elektromotoren", "Förderrollen", "Getriebe", "Ventilatoren", "Pumpen"),
					List.of("Dauergeschmiert und wartungsfrei", "Betriebstemperatur bis 110 °C", "Abmessungen nach DIN 625"),
					List.of("Einreihiges Rillenkugellager {size} aus Wälzlagerstahl 100Cr6, Lagerluft {material}.", "Für {use}.")),
			new Family("Pendelrollenlager", "LAGER", "PRL", false,
					List.of(),
					List.of("22208 E", "22210 E", "22212 E", "22216 E", "22220 E"),
					List.of("C3", "CN"),
					List.of("zylindrische Bohrung", "kegelige Bohrung K"),
					List.of("Brechern", "Siebmaschinen", "Getrieben", "Walzwerken"),
					List.of("Gleicht Fluchtungsfehler der Welle aus", "Mit Schmiernut und Schmierbohrungen im Außenring"),
					List.of("Pendelrollenlager {size} mit {finish}, Lagerluft {material}.", "Für hohe Radiallasten in {use}.")),
			new Family("O-Ring", "DICHTUNG", "OR", false,
					List.of(),
					List.of("10x2", "16x2", "20x2,5", "25x3", "35x3", "50x3,5", "80x4", "120x5"),
					List.of("NBR 70 Shore A", "FKM 80 Shore A", "EPDM 70 Shore A", "VMQ 60 Shore A"),
					List.of(),
					List.of("Hydraulik", "Pneumatik", "Lebensmitteltechnik", "Pumpenbau"),
					List.of("Toleranzen nach ISO 3601", "Temperaturbereich je nach Werkstoff von -30 °C bis +200 °C"),
					List.of("O-Ring {size} mm aus {material}.", "Für statische und dynamische Abdichtung in der {use}.")),
			new Family("Radialwellendichtring", "DICHTUNG", "RWDR", false,
					List.of("DIN 3760 Form A", "DIN 3760 Form AS"),
					List.of("20x35x7", "25x40x7", "30x47x7", "40x62x8", "50x72x8", "60x80x8"),
					List.of("NBR", "FKM"),
					List.of(),
					List.of("Getriebe", "Elektromotoren", "Pumpen"),
					List.of("Form AS mit zusätzlicher Staublippe", "Umfangsgeschwindigkeit bis 12 m/s"),
					List.of("Radialwellendichtring {size} nach {norm} aus {material}.", "Zur Abdichtung rotierender Wellen in {use}.")),
			new Family("Hydraulikschlauch", "SCHLAUCH", "HS", false,
					List.of("EN 853 1SN", "EN 853 2SN", "EN 856 4SP"),
					List.of("DN6 x 500 mm", "DN10 x 1000 mm", "DN12 x 1500 mm", "DN16 x 2000 mm", "DN20 x 1000 mm"),
					List.of(),
					List.of("mit Pressarmaturen DKOL", "mit Pressarmaturen CEL", "beidseitig mit 24°-Dichtkegel"),
					List.of("mobile Hydraulik", "Pressen", "Werkzeugmaschinen"),
					List.of("Prüfdruck nach Norm, Prüfprotokoll liegt bei", "Herstelldatum auf der Armatur eingeprägt"),
					List.of("Hydraulikschlauch {size} nach {norm}, {finish}.", "Für {use}.")),
			new Family("Pneumatikzylinder", "ZYLINDER", "PZ", false,
					List.of("ISO 15552", "ISO 6432"),
					List.of("Ø32x50", "Ø40x100", "Ø50x160", "Ø63x200", "Ø80x320"),
					List.of(),
					List.of("doppeltwirkend", "doppeltwirkend mit Magnetkolben", "einfachwirkend"),
					List.of("Handhabungstechnik", "Spannvorrichtungen", "Verpackungsmaschinen"),
					List.of("Betriebsdruck 1 bis 10 bar", "Einstellbare Endlagendämpfung"),
					List.of("Pneumatikzylinder nach {norm}, Kolben und Hub {size} mm, {finish}.", "Für {use}.")),
			new Family("Magnetventil", "VENTIL", "MV", false,
					List.of(),
					List.of("3/2-Wege G1/8", "5/2-Wege G1/8", "5/2-Wege G1/4", "5/3-Wege G1/4"),
					List.of("24 V DC", "230 V AC"),
					List.of("monostabil", "bistabil"),
					List.of("Montageanlagen", "Verpackungsmaschinen", "Prüfständen"),
					List.of("Handhilfsbetätigung rastend", "Spule um 90° drehbar"),
					List.of("Magnetventil {size}, Spannung {material}, {finish}.", "Zum Ansteuern von Pneumatikzylindern in {use}.")),
			new Family("Kugelhahn", "ARMATUR", "KH", false,
					List.of("PN40", "PN63"),
					List.of("DN15", "DN20", "DN25", "DN32", "DN40", "DN50"),
					List.of("Edelstahl 1.4408", "Messing vernickelt", "Stahl 1.0619"),
					List.of("mit Innengewinde", "mit Flanschanschluss", "mit ISO-5211-Kopfflansch"),
					List.of("Wasser", "Druckluft", "Hydrauliköl", "Prozessmedien"),
					List.of("Voller Durchgang", "Antistatische Ausführung"),
					List.of("Kugelhahn {size} {norm} aus {material}, {finish}.", "Für {use}.")),
			new Family("Vorschweißflansch", "FLANSCH", "VSF", false,
					List.of("EN 1092-1 Typ 11"),
					List.of("DN25", "DN50", "DN80", "DN100", "DN150"),
					List.of("P250GH", "1.4571", "1.4404"),
					List.of("PN16", "PN40"),
					List.of("Rohrleitungsbau", "Behälterbau", "Anlagenbau"),
					List.of("Dichtfläche Form B1", "Werkstoffzeugnis 3.1 nach EN 10204"),
					List.of("Vorschweißflansch {size} {finish} nach {norm} aus {material}.", "Für {use}.")),
			new Family("Welle", "WELLE", "WL", true,
					List.of(),
					List.of("Ø20h6x300", "Ø25h6x450", "Ø30h6x600", "Ø40h6x800", "Ø50h6x1000"),
					List.of("42CrMo4", "C45", "1.4057"),
					List.of("induktionsgehärtet und geschliffen", "vergütet", "hartverchromt"),
					List.of("Antriebsstränge", "Förderanlagen", "Sondermaschinen"),
					List.of("Passfedernut nach DIN 6885", "Zentrierbohrung beidseitig"),
					List.of("Welle {size} mm aus {material}, {finish}.", "Fertigung nach Zeichnung, für {use}.")),
			new Family("Stirnrad", "ZAHNRAD", "STR", true,
					List.of(),
					List.of("m2 z20", "m2 z40", "m3 z25", "m3 z60", "m4 z30", "m5 z45"),
					List.of("C45", "16MnCr5", "42CrMo4"),
					List.of("einsatzgehärtet", "vergütet", "induktionsgehärtet"),
					List.of("Getriebe", "Hubwerke", "Drehtische"),
					List.of("Verzahnungsqualität 8 nach DIN 3962", "Bohrung mit Passfedernut"),
					List.of("Geradverzahntes Stirnrad {size} aus {material}, {finish}.", "Für {use}.")),
			new Family("Kettenrad", "KETTENRAD", "KR", false,
					List.of("08B-1", "10B-1", "12B-1", "16B-1"),
					List.of("z15", "z19", "z25", "z38"),
					List.of("C45", "S355"),
					List.of("Zähne gehärtet", "ungehärtet mit Vorbohrung"),
					List.of("Fördertechnik", "Antriebstechnik"),
					List.of("Nabe einseitig", "Fertigbohrung auf Anfrage"),
					List.of("Kettenrad für Rollenkette {norm}, {size}, aus {material}, {finish}.", "Für {use}.")),
			new Family("Zahnriemen", "RIEMEN", "ZR", false,
					List.of("HTD 8M", "HTD 5M", "T10", "AT10"),
					List.of("1200 mm x 30 mm", "1600 mm x 30 mm", "2000 mm x 50 mm", "800 mm x 20 mm"),
					List.of("Polyurethan mit Stahlcord", "Chloropren mit Glasfaser"),
					List.of(),
					List.of("Linearachsen", "Verpackungsmaschinen", "Fördertechnik"),
					List.of("Wartungsfrei, keine Nachspannung im Betrieb"),
					List.of("Zahnriemen Profil {norm}, {size}, aus {material}.", "Für synchrone Kraftübertragung in {use}.")),
			new Family("Stirnradgetriebemotor", "MOTOR", "GM", false,
					List.of("i=10", "i=20", "i=31,5", "i=40"),
					List.of("0,37 kW", "0,75 kW", "1,5 kW", "3,0 kW", "5,5 kW"),
					List.of("400 V 50 Hz", "230/400 V"),
					List.of("Effizienzklasse IE3", "mit Federdruckbremse", "mit Hohlwelle"),
					List.of("Förderbänder", "Rührwerke", "Hubtische"),
					List.of("Schutzart IP55", "Bauform B3 oder B5 auf Anfrage"),
					List.of("Stirnradgetriebemotor {size}, Übersetzung {norm}, {material}, {finish}.", "Für {use}.")),
			new Family("Frequenzumrichter", "UMRICHTER", "FU", false,
					List.of(),
					List.of("0,75 kW", "2,2 kW", "4,0 kW", "7,5 kW", "15 kW"),
					List.of("3x400 V"),
					List.of("IP20", "IP66", "mit EMV-Filter"),
					List.of("Pumpen", "Lüftern", "Förderanlagen"),
					List.of("Safe Torque Off integriert", "Feldbus über Optionskarte"),
					List.of("Frequenzumrichter {size}, Netz {material}, {finish}.", "Zur Drehzahlregelung von Asynchronmotoren in {use}.")),
			new Family("Induktiver Näherungsschalter", "SENSOR", "INS", false,
					List.of(),
					List.of("M8", "M12", "M18", "M30"),
					List.of("PNP Schließer", "NPN Öffner", "PNP Öffner"),
					List.of("Sn 2 mm", "Sn 4 mm", "Sn 8 mm", "Sn 15 mm"),
					List.of("Positionsabfrage", "Endlagenüberwachung", "Teileerkennung"),
					List.of("Anschluss über Steckverbinder M12", "Schutzart IP67"),
					List.of("Induktiver Näherungsschalter Bauform {size}, Ausgang {material}, Schaltabstand {finish}.", "Für {use}.")),
			new Family("Reflexionslichtschranke", "SENSOR", "RLS", false,
					List.of(),
					List.of("Reichweite 5 m", "Reichweite 10 m", "Reichweite 15 m"),
					List.of("PNP", "NPN"),
					List.of("Rotlicht", "Laser Klasse 1", "Infrarot"),
					List.of("Teileerkennung", "Lagertechnik", "Verpackungsmaschinen"),
					List.of("Mit Polarisationsfilter", "Reflektor separat bestellen"),
					List.of("Reflexionslichtschranke {size}, Ausgang {material}, {finish}.", "Für {use}.")),
			new Family("Steuerleitung", "KABEL", "STL", false,
					List.of(),
					List.of("3G0,75", "7G0,75", "12G1,0", "4G1,5", "5G2,5", "18G1,0"),
					List.of("PVC", "PUR", "PUR schleppkettentauglich"),
					List.of("geschirmt", "ungeschirmt"),
					List.of("Maschinenverdrahtung", "Schleppketten", "Schaltschrankbau"),
					List.of("Meterware, Abgabe in Ringen zu 100 m", "Halogenfrei"),
					List.of("Steuerleitung {size} mm², Mantel {material}, {finish}.", "Für {use}.")),
			new Family("Sensorleitung", "KABEL", "SNL", false,
					List.of("M12 4-polig A-codiert", "M12 8-polig A-codiert", "M8 3-polig"),
					List.of("2 m", "5 m", "10 m"),
					List.of("PUR"),
					List.of("gerade", "gewinkelt"),
					List.of("Sensoren", "Ventilinseln"),
					List.of("Schleppkettentauglich", "Schutzart IP68 gesteckt"),
					List.of("Sensorleitung {norm}, Länge {size}, Mantel {material}, Stecker {finish}.", "Für {use}.")),
			new Family("Blech", "BLECH", "BL", false,
					List.of(),
					List.of("1,5 x 1250 x 2500", "2,0 x 1250 x 2500", "3,0 x 1500 x 3000", "5,0 x 1500 x 3000"),
					List.of("DC01", "S235JR", "S355MC", "1.4301", "AlMg3"),
					List.of("geölt", "gebeizt", "2B", "walzblank"),
					List.of("Laserschneiden", "Kantteile", "Gehäusebau"),
					List.of("Tafelware, Lieferung auf Paletten", "Ebenheit nach EN 10051"),
					List.of("Blech {size} mm aus {material}, Oberfläche {finish}.", "Für {use}.")),
			new Family("Rundstahl", "STAHL", "RST", false,
					List.of(),
					List.of("Ø20", "Ø30", "Ø40", "Ø60", "Ø80"),
					List.of("S235JR", "C45", "42CrMo4", "1.4305"),
					List.of("blank gezogen h9", "gewalzt", "geschält"),
					List.of("Drehteile", "Wellen", "Bolzen"),
					List.of("Stangenware 6 m, Zuschnitt auf Anfrage", "Werkstoffzeugnis 3.1 nach EN 10204"),
					List.of("Rundstahl {size} mm aus {material}, {finish}.", "Für {use}.")),
			new Family("Getriebegehäuse", "GEHAEUSE", "GG", true,
					List.of(),
					List.of("Baugröße 1", "Baugröße 2", "Baugröße 3", "Baugröße 4"),
					List.of("EN-GJL-250", "EN-GJS-400-15", "AlSi10Mg"),
					List.of("bearbeitet", "roh", "bearbeitet und grundiert"),
					List.of("Stirnradgetriebe", "Kegelradgetriebe"),
					List.of("Gussteil nach Zeichnung, Modell kundeneigen", "Dichtheitsprüfung mit 0,5 bar"),
					List.of("Getriebegehäuse {size} aus {material}, {finish}.", "Für {use}.")),
			new Family("Lagerbuchse", "BUCHSE", "LB", true,
					List.of(),
					List.of("Ø20/15x20", "Ø30/25x30", "Ø40/32x40", "Ø50/40x50"),
					List.of("CuSn8", "CuSn7Zn4Pb7", "PA6 GF30"),
					List.of("mit Schmiernut", "ohne Schmiernut"),
					List.of("Gelenke", "Hebel", "Schwenklager"),
					List.of("Drehteil nach Zeichnung", "Passung Bohrung H7"),
					List.of("Lagerbuchse {size} mm aus {material}, {finish}.", "Für {use}.")),
			new Family("Grundrahmen", "RAHMEN", "GR", true,
					List.of(),
					List.of("800 x 600", "1200 x 800", "1600 x 1000", "2000 x 1200"),
					List.of("S235JR", "S355J2"),
					List.of("feuerverzinkt", "grundiert RAL 7035", "pulverbeschichtet RAL 5010"),
					List.of("Sondermaschinen", "Förderanlagen", "Prüfstände"),
					List.of("Schweißbaugruppe nach Zeichnung", "Schweißnähte nach EN ISO 5817 Klasse C"),
					List.of("Grundrahmen {size} mm aus {material}, {finish}.", "Schweißbaugruppe für {use}.")),
			new Family("Druckfeder", "FEDER", "DF", false,
					List.of("DIN EN 10270-1"),
					List.of("1,0x10x30", "2,0x20x60", "3,0x30x80", "4,0x40x100"),
					List.of("Federstahldraht SH", "Edelstahl 1.4310"),
					List.of(),
					List.of("Ventile", "Spannvorrichtungen", "Rastmechanismen"),
					List.of("Enden angelegt und geschliffen", "Maße Drahtstärke x Außendurchmesser x Länge"),
					List.of("Druckfeder {size} mm aus {material} nach {norm}.", "Für {use}.")),
			new Family("Sicherungsring", "SICHERUNG", "SR", false,
					List.of("DIN 471", "DIN 472"),
					List.of("10", "15", "20", "25", "30", "40", "50"),
					List.of("Federstahl"),
					List.of("phosphatiert", "blank"),
					List.of("Wellen", "Bohrungen"),
					List.of("Montage mit Sicherungsringzange"),
					List.of("Sicherungsring {size} nach {norm} aus {material}, {finish}.", "Zur axialen Sicherung auf {use}.")),
			new Family("Passfeder", "PASSFEDER", "PF", false,
					List.of("DIN 6885 Form A", "DIN 6885 Form B"),
					List.of("6x6x20", "8x7x40", "10x8x50", "12x8x63", "14x9x80"),
					List.of("C45+C"),
					List.of(),
					List.of("Welle-Nabe-Verbindungen"),
					List.of("Toleranz h9"),
					List.of("Passfeder {size} nach {norm} aus {material}.", "Für {use}.")),
			new Family("Hydrauliköl", "OEL", "HOEL", false,
					List.of("HLP 32", "HLP 46", "HLP 68"),
					List.of("20 l Kanister", "208 l Fass"),
					List.of("Mineralöl"),
					List.of(),
					List.of("Hydraulikaggregate", "Werkzeugmaschinen"),
					List.of("Sicherheitsdatenblatt beachten", "Nach DIN 51524-2"),
					List.of("Hydrauliköl {norm} auf {material}basis, Gebinde {size}.", "Für {use}.")),
			new Family("Filterelement", "FILTER", "FE", false,
					List.of(),
					List.of("3 µm", "10 µm", "25 µm"),
					List.of("Glasfaser", "Papier", "Edelstahlgewebe"),
					List.of("Durchfluss 60 l/min", "Durchfluss 150 l/min", "Durchfluss 300 l/min"),
					List.of("Hydraulikaggregate", "Schmieranlagen"),
					List.of("Wechsel nach Verschmutzungsanzeige", "Differenzdruck bis 20 bar"),
					List.of("Filterelement {size} aus {material}, {finish}.", "Für {use}.")),
			new Family("Faltkarton", "KARTON", "FK", false,
					List.of(),
					List.of("300x200x150", "400x300x200", "600x400x300", "800x600x400"),
					List.of("1-wellig", "2-wellig"),
					List.of("braun", "weiß"),
					List.of("Kleinteilen", "Ersatzteilen", "Baugruppen"),
					List.of("Abgabe im Bündel zu 25 Stück"),
					List.of("Faltkarton {size} mm, {material}, {finish}.", "Für den Versand von {use}.")),
			new Family("Typenschild", "SCHILD", "TS", false,
					List.of(),
					List.of("60x30", "80x40", "100x50"),
					List.of("Aluminium eloxiert", "Edelstahl 1.4301", "Polyesterfolie"),
					List.of("lasergraviert", "bedruckt"),
					List.of("Maschinen", "Schaltschränke", "Baugruppen"),
					List.of("Beschriftung nach Vorlage", "Mit Klebeschicht oder Befestigungslöchern"),
					List.of("Typenschild {size} mm aus {material}, {finish}.", "Zur Kennzeichnung von {use}.")));

	public static final List<Family> TOOL_FAMILIES = List.of(
			new Family("Spiralbohrer", "BOHRER", "SPB", false,
					List.of("DIN 338", "DIN 340", "DIN 6537 K"),
					List.of("Ø3,3", "Ø4,2", "Ø5,0", "Ø6,8", "Ø8,5", "Ø10,2", "Ø12,0"),
					List.of("HSS-G", "HSS-E", "VHM"),
					List.of("blank", "TiN-beschichtet", "TiAlN-beschichtet"),
					List.of("Stahl bis 1000 N/mm²", "rostfreie Stähle", "Grauguss", "Aluminium"),
					List.of("Spitzenwinkel 135°, Kreuzanschliff", "Zylinderschaft h8"),
					List.of("Spiralbohrer {size} mm nach {norm} aus {material}, {finish}.", "Für {use}.")),
			new Family("Maschinengewindebohrer", "GEWINDEBOHRER", "MGB", false,
					List.of("DIN 371", "DIN 376"),
					List.of("M4", "M5", "M6", "M8", "M10", "M12", "M16"),
					List.of("HSS-E", "HSS-E-PM"),
					List.of("Form B", "Form C", "Form C TiN"),
					List.of("Stahl", "Edelstahl", "Aluminium"),
					List.of("Toleranz 6H", "Form C für Grundlöcher"),
					List.of("Maschinengewindebohrer {size} nach {norm}, {material}, {finish}.", "Für Gewinde in {use}.")),
			new Family("Schaftfräser", "FRAESER", "SF", false,
					List.of("Schaft DIN 6535 HA", "Schaft DIN 6535 HB"),
					List.of("Ø4", "Ø6", "Ø8", "Ø10", "Ø12", "Ø16", "Ø20"),
					List.of("VHM"),
					List.of("Z3 TiAlN", "Z4 TiAlN", "Z4 AlCrN", "Z2 unbeschichtet"),
					List.of("Stahl", "Edelstahl", "Aluminium", "gehärtetem Stahl bis 54 HRC"),
					List.of("Ungleiche Teilung gegen Rattern", "Eckenschutzfase"),
					List.of("Schaftfräser {size} mm aus {material}, {finish}, {norm}.", "Zum Schruppen und Schlichten in {use}.")),
			new Family("Wendeschneidplatte", "WSP", "WSP", false,
					List.of("CNMG 120408", "WNMG 080408", "DCMT 11T304", "VBMT 160404", "SNMG 120408"),
					List.of(),
					List.of("P25", "M15", "K10", "P15"),
					List.of("CVD-beschichtet", "PVD-beschichtet", "unbeschichtet"),
					List.of("Stahl", "rostfreiem Stahl", "Grauguss"),
					List.of("Packungseinheit 10 Stück", "Spanbrecher für mittlere Bearbeitung"),
					List.of("Wendeschneidplatte {norm}, Sorte {material}, {finish}.", "Zum Drehen von {use}.")),
			new Family("Klemmhalter", "HALTER", "KLH", false,
					List.of("PCLNR 2525M12", "PDJNR 2020K11", "SVJBR 2525M16", "PWLNR 2020K08"),
					List.of(),
					List.of("Vergütungsstahl"),
					List.of(),
					List.of("CNC-Drehmaschinen"),
					List.of("Mit innerer Kühlmittelzufuhr", "Ersatzteile: Spannpratze und Unterlage"),
					List.of("Klemmhalter {norm} für das Außendrehen, Grundkörper aus {material}.", "Für {use}.")),
			new Family("Kegelsenker", "SENKER", "KS", false,
					List.of("DIN 335 Form C"),
					List.of("Ø6,3", "Ø10,4", "Ø12,4", "Ø16,5", "Ø20,5"),
					List.of("HSS", "HSS-E"),
					List.of("90°", "60°", "90° TiAlN"),
					List.of("Stahl", "Aluminium", "Kunststoff"),
					List.of("Drei Schneiden, ratterarm"),
					List.of("Kegelsenker {finish} {size} mm nach {norm} aus {material}.", "Zum Entgraten und Senken in {use}.")),
			new Family("Maschinenreibahle", "REIBAHLE", "MRA", false,
					List.of("DIN 212"),
					List.of("Ø6 H7", "Ø8 H7", "Ø10 H7", "Ø12 H7", "Ø16 H7", "Ø20 H7"),
					List.of("HSS-E", "VHM"),
					List.of(),
					List.of("Stahl", "Grauguss", "Messing"),
					List.of("Linksdrall für Durchgangsbohrungen"),
					List.of("Maschinenreibahle {size} nach {norm} aus {material}.", "Für Passbohrungen in {use}.")),
			new Family("Bandsägeblatt", "SAEGEBLATT", "BSB", false,
					List.of(),
					List.of("27 x 0,9 x 3660 mm", "34 x 1,1 x 4570 mm", "41 x 1,3 x 5000 mm"),
					List.of("Bi-Metall M42", "Hartmetall"),
					List.of("3/4 ZpZ", "4/6 ZpZ", "5/8 ZpZ"),
					List.of("Baustahl", "Edelstahl", "Aluminium"),
					List.of("Geschweißt auf Länge", "Variable Zahnung"),
					List.of("Bandsägeblatt {size}, {material}, Zahnung {finish}.", "Für Vollmaterial und Profile aus {use}.")),
			new Family("Spannzange", "SPANNZANGE", "SPZ", false,
					List.of("ER16", "ER25", "ER32", "ER40"),
					List.of("Ø3", "Ø6", "Ø8", "Ø10", "Ø12", "Ø16", "Ø20"),
					List.of("Federstahl"),
					List.of("Rundlauf 5 µm", "Rundlauf 10 µm"),
					List.of("Fräs- und Bohrwerkzeuge mit Zylinderschaft"),
					List.of("Spannbereich 1 mm"),
					List.of("Spannzange {norm} {size} mm aus {material}, {finish}.", "Für {use}.")),
			new Family("Grenzlehrdorn", "LEHRE", "GLD", false,
					List.of("DIN 2245"),
					List.of("Ø6 H7", "Ø8 H7", "Ø10 H7", "Ø12 H7", "Ø16 H7", "Ø20 H7"),
					List.of("Werkzeugstahl gehärtet", "Hartmetall"),
					List.of(),
					List.of("die Prüfung von Passbohrungen"),
					List.of("Prüfmittel mit Kalibrierschein", "Gut- und Ausschussseite farbig gekennzeichnet"),
					List.of("Grenzlehrdorn {size} nach {norm} aus {material}.", "Für {use}.")),
			new Family("Schleifscheibe", "SCHLEIFSCHEIBE", "SLS", false,
					List.of(),
					List.of("125 x 6 x 22,23", "180 x 6 x 22,23", "230 x 6 x 22,23", "300 x 40 x 76,2"),
					List.of("Korund A46", "Korund A60", "Siliziumkarbid C60", "CBN B126"),
					List.of(),
					List.of("Schruppschliff", "Werkzeugschliff", "Flachschliff"),
					List.of("Höchstumfangsgeschwindigkeit 80 m/s", "Vor dem Einsatz Probelauf durchführen"),
					List.of("Schleifscheibe {size} mm, {material}.", "Für den {use}.")));

	/** Bemerkungen, die an rund jedem dritten Artikel stehen */
	public static final List<String> REMARKS = List.of("Mindestbestellmenge 100 Stück", "Nur über Rahmenvertrag beziehen",
			"Ersatzteil, Mindestbestand halten", "Auslaufartikel, Nachfolger beachten", "Werkstoffzeugnis 3.1 erforderlich",
			"Kundenspezifisch, nicht ohne Freigabe ändern", "Lieferzeit ca. 6 Wochen", "Muster beim Wareneingang prüfen",
			"Verpackungsvorschrift des Kunden beachten");

	/** Die Funktionen der Ansprechpartner */
	public static final List<String> FUNCTIONS = List.of("Einkauf", "Technischer Einkauf", "Vertrieb Innendienst",
			"Vertrieb Außendienst", "Geschäftsführung", "Qualitätssicherung", "Konstruktion", "Arbeitsvorbereitung",
			"Buchhaltung", "Logistik", "Projektleitung", "Kundendienst", "Produktionsleitung", "Disposition", "Entwicklung",
			"Lager", "Assistenz der Geschäftsführung", "Reklamationsmanagement");

	/** Die Rechtsformen, GmbH am häufigsten */
	private static final List<String> LEGAL_FORMS = List.of("GmbH", "GmbH", "GmbH", "GmbH", "GmbH & Co. KG",
			"GmbH & Co. KG", "AG", "KG", "e.K.", "OHG");

	/** Die Branchen im Firmennamen */
	private static final List<String> BRANCHES = List.of("Maschinenbau", "Antriebstechnik", "Metallbearbeitung",
			"Stahlhandel", "Hydraulik", "Pneumatik", "Elektrotechnik", "Fördertechnik", "Werkzeugbau", "Industriebedarf",
			"Präzisionstechnik", "Kunststofftechnik", "Automatisierung", "Anlagenbau", "Schraubenhandel", "Gießerei");

	/** Deutsche Vorwahlen ohne die 0 */
	private static final List<String> AREA_CODES = List.of("30", "40", "89", "221", "69", "711", "211", "231", "201",
			"421", "351", "341", "511", "911", "228", "621", "721", "6281", "7151", "7031", "2161", "5251", "9131", "7121");

	/**
	 * Ein Artikel zu dieser Nummer: mit 997 vorn ein Werkzeug, sonst ein Produkt. Name aus Produktart, Norm, Maß,
	 * Werkstoff und Ausführung; Suchwort der Familie; Verkaufsbezeichnung als Kurzform; Zeichnungsnummer nur bei Teilen
	 * nach Zeichnung; an rund jedem dritten eine Bemerkung; Beschreibung aus den Sätzen der Familie.
	 */
	public static FakeArticle articleOf(int number, Random random) {
		String articleNumber = String.valueOf(number);
		boolean tool = articleNumber.startsWith(TOOL_NUMBER_PREFIX);
		List<Family> families = tool ? TOOL_FAMILIES : PRODUCT_FAMILIES;
		Family family = families.get(random.nextInt(families.size()));
		String norm = pick(family.norms(), random);
		String size = pick(family.sizes(), random);
		String material = pick(family.materials(), random);
		String finish = pick(family.finishes(), random);
		String use = pick(family.uses(), random);
		String name = joined(family.productType(), norm, size, material, finish);
		String salesName = joined(family.shortName(), size, material);
		String drawingNumber = family.drawingPart() ? articleNumber + "_ZN" + (100_000 + random.nextInt(900_000)) : null;
		String remark = random.nextInt(3) == 0 ? pick(REMARKS, random) : null;
		List<String> sentences = new ArrayList<>();
		for (String sentence : family.sentences()) {
			sentences.add(sentence.replace("{norm}", orEmpty(norm)).replace("{size}", orEmpty(size))
					.replace("{material}", orEmpty(material)).replace("{finish}", orEmpty(finish))
					.replace("{use}", orEmpty(use)));
		}
		String property = pick(family.properties(), random);
		String description = String.join(" ", sentences) + (property != null && random.nextBoolean() ? " " + property + "." : "");
		List<String> dataSheetSentences = new ArrayList<>();
		dataSheetSentences.add("Technisches Datenblatt zu Artikel " + articleNumber + ", " + name + ".");
		dataSheetSentences.addAll(sentences);
		for (String candidate : family.properties()) {
			dataSheetSentences.add(candidate + ".");
		}
		if (drawingNumber != null) {
			dataSheetSentences.add("Gefertigt nach Zeichnung " + drawingNumber + ".");
		}
		return new FakeArticle(articleNumber, tool, name, family.searchWord(), salesName, drawingNumber, remark,
				description, dataSheetSentences);
	}

	/**
	 * Ein Firmenname aus zwei Nachnamen von Datafaker, einer Branche und einer Rechtsform, z. B. „Hartmann
	 * Antriebstechnik GmbH“ oder „Brandt & Seidel KG“; die Domain aus dem Namen ohne Rechtsform, z. B.
	 * hartmann-antriebstechnik.de.
	 */
	public static FakeCompanyName companyNameOf(String lastName, String secondLastName, Random random) {
		String branch = pick(BRANCHES, random);
		String legalForm = pick(LEGAL_FORMS, random);
		String stem = switch (random.nextInt(4)) {
		case 0 -> lastName + " " + branch;
		case 1 -> lastName + " & " + secondLastName;
		case 2 -> lastName + "-" + secondLastName + " " + branch;
		default -> lastName;
		};
		return new FakeCompanyName(stem + " " + legalForm, slugOf(stem.replace("&", " ")) + ".de");
	}

	/** Eine deutsche Telefonnummer, z. B. 0711/4823910 */
	public static String phoneNumberOf(Random random) {
		String areaCode = pick(AREA_CODES, random);
		int digits = Math.max(5, 10 - areaCode.length());
		StringBuilder number = new StringBuilder("0").append(areaCode).append('/').append(1 + random.nextInt(9));
		for (int digit = 1; digit < digits; digit++) {
			number.append(random.nextInt(10));
		}
		return number.toString();
	}

	/** Das Suchwort wie in ABAS: Großbuchstaben, Umlaute aufgelöst, nur A–Z und 0–9, höchstens maxLength Zeichen */
	public static String searchWordOf(String text, int maxLength) {
		String letters = transliterated(text).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
		return letters.length() <= maxLength ? letters : letters.substring(0, maxLength);
	}

	/** Klein, Umlaute aufgelöst, nur a–z, 0–9 und Bindestrich, z. B. für E-Mail und Domain */
	public static String slugOf(String text) {
		return transliterated(text).toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
	}

	private static String transliterated(String text) {
		return text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae").replace("Ö", "Oe")
				.replace("Ü", "Ue").replace("ß", "ss").replace("é", "e").replace("è", "e").replace("á", "a")
				.replace("ó", "o").replace("ú", "u");
	}

	/** Ein zufälliger Eintrag, null bei einer leeren Liste */
	public static String pick(List<String> values, Random random) {
		return values.isEmpty() ? null : values.get(random.nextInt(values.size()));
	}

	private static String joined(String... parts) {
		List<String> present = new ArrayList<>();
		for (String part : parts) {
			if (part != null && !part.isBlank()) {
				present.add(part);
			}
		}
		return String.join(" ", present);
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

}

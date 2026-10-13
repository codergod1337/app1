package codergod1337.app1.abas.model;

/**
 * Eine Gruppe von Sternzeiten in einer Import-Datei (User 2026-10-13): entweder alle mit demselben Objekttyp, wenn die
 * Datei ihn nennt (das Inventar, {"sternzeit":"(87448,19,0)","objectType":"obj:data:19:0"}), oder ohne Typ alle mit
 * derselben ABAS-Datenbank, der mittleren Zahl der Sternzeit. Am Typ lässt sich die Gruppe nicht ablesen: (380,0,0) ist
 * obj:data:0:1, (9930,0,0) obj:data:0:2.
 *
 * @param objectType          der Typ, wie die Datei ihn nennt; null bei einer Gruppe ohne Typ
 * @param abasDatabaseNumber  die Datenbank einer Gruppe ohne Typ; null bei einer Gruppe mit Typ
 * @param sternzeitCount      wie oft die Datei eine Sternzeit dieser Gruppe nennt, Doppelte mitgezählt
 * @param exampleSternzeit    die erste Sternzeit der Gruppe in der Datei, zum Nachsehen
 */
public record AbasSternzeitImportGroup(String objectType, Integer abasDatabaseNumber, long sternzeitCount,
		String exampleSternzeit) {
}

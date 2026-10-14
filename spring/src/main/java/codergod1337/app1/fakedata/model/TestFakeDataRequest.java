package codergod1337.app1.fakedata.model;

/**
 * Was die Maske testfakedata zum Erzeugen schickt (User 2026-10-14): wie viele Artikel, Kunden, Lieferanten und
 * Datenblätter es insgesamt werden und wie viele Datenblätter ein Artikel im Mittel hat, mit Streuung der
 * Normalverteilung. Daraus folgt, an wie vielen Artikeln ein Datenblatt im Mittel hängt: articleCount mal
 * documentsPerArticleMean geteilt durch documentCount.
 *
 * @param articleCount                 Artikel, Nummern fortlaufend ab 10000000; 997… sind Werkzeuge
 * @param customerCount                Kunden, je eine Firma mit 1 bis 15 Ansprechpartnern
 * @param supplierCount                Lieferanten, wie die Kunden
 * @param documentCount                Datenblätter (TXT), Inhalt 0 bis documentCount − 1 und 1 bis 5 Sätze
 * @param documentsPerArticleMean      Datenblätter je Artikel im Mittel
 * @param documentsPerArticleDeviation Streuung der Normalverteilung um das Mittel
 */
public record TestFakeDataRequest(int articleCount, int customerCount, int supplierCount, int documentCount,
		double documentsPerArticleMean, double documentsPerArticleDeviation) {
}

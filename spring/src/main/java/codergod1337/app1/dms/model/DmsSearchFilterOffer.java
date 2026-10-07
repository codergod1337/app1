package codergod1337.app1.dms.model;

/**
 * Ein Filterangebot der Vervollständigung: der ganze Wert eines filterbaren Felds aus einem Treffer, dessen Anfang ein
 * Suchwort trifft, zum Einloggen als Chip. Die Suggested-Zwillinge sind nicht stored, die vollen Werte kommen aus den
 * gespeicherten Originalfeldern der Treffer.
 *
 * @param fieldName             das Feld, Hook oder festes Feld der Filterliste
 * @param value                 der ganze Wert, wie er im Dokument steht
 * @param label                 ein Zusatz zur Anzeige, beim Dateinamen die Dateiart, sonst null
 * @param imagePreviewBase64Png das Vorschaubild des Treffers, beim Dateinamen, sonst null
 */
public record DmsSearchFilterOffer(String fieldName, String value, String label, String imagePreviewBase64Png) {
}

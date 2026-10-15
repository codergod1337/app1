package codergod1337.app1.solr.todojob.model;

/** Der Auszug einer Mappe: ihr Anzeigeort, der Hook, über den sie lädt, dessen Wert, Seite ab 0 (null heißt 0) */
public record TodojobExcerptRequest(String displayContextKey, String hookKey, String hookValue, Integer page) {
}

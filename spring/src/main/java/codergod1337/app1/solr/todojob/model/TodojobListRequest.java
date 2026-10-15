package codergod1337.app1.solr.todojob.model;

/** Einer der drei Stapel des Schreibtischs, Seite ab 0 (null heißt 0) */
public record TodojobListRequest(TodojobListKind kind, Integer page) {
}

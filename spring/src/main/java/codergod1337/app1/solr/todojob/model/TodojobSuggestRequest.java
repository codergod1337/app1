package codergod1337.app1.solr.todojob.model;

/** Ähnliche Jobs beim Erteilen: dasselbe Verb, ein ähnliches Objekt; beides optional, aber eines muss da sein */
public record TodojobSuggestRequest(String verb, String objekt) {
}

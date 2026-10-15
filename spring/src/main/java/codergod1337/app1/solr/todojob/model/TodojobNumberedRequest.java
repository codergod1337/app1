package codergod1337.app1.solr.todojob.model;

/** Ein Vorgang auf einen Eintrag mit nr: einen Eintrag der Rückmeldung zurücknehmen, eine Rückfrage beantworten (mit text) */
public record TodojobNumberedRequest(String id, Integer nr, String text) {
}

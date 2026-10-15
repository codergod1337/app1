package codergod1337.app1.solr.todojob.model;

import java.util.List;

/**
 * Zurückgeben: die Begründung (Pflicht) und, wenn der Owner dabei Aufgabe oder Kontext nachbessert, deren neuer Stand;
 * null heißt unverändert
 */
public record TodojobReturnRequest(String id, String text, String aufgabe, List<String> kontext) {
}

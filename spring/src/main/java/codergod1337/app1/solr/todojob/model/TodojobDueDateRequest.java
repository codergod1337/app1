package codergod1337.app1.solr.todojob.model;

import java.time.Instant;

/** Die Frist ändern; fristAm null löscht sie */
public record TodojobDueDateRequest(String id, Instant fristAm) {
}

package codergod1337.app1.graph.mail.model;

/**
 * Das Muster im Editor: Betreff und Text einer Vorlage, in einer Sprache gerendert mit den Beispielwerten aller
 * Platzhaltergruppen des Typs. Nichts wird verschickt.
 */
public record GraphMailTemplatePreview(String subject, String htmlBody) {
}

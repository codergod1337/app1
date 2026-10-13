package codergod1337.app1.solr.share.model;

/**
 * Die Antwort auf das Anlegen eines Abholscheins: die Zeile und der Klartext des Tokens, den es nur dieses eine Mal
 * gibt. Das Frontend baut daraus den Link der Station, gespeichert wird nur der Hash.
 */
public record CreatedShareToken(ShareToken shareToken, String token) {
}

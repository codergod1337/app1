package codergod1337.app1.solr.grant.model;

/**
 * Was der Ablauf der Freigaben in Postgres getan hat: wie viele Hook- und Feldfreigaben auf EXPIRED gingen. Die
 * Dateifreigaben laufen je Kern ab, im DMS über DmsExpiredDocumentGrantsCount.
 */
public record ExpiredGrantsCount(int expiredHookGrants, int expiredFieldGrants) {
}

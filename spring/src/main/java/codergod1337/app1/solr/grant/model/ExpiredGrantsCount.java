package codergod1337.app1.solr.grant.model;

/** Was der Ablauf getan hat: wie viele Feld- und Dateifreigaben auf EXPIRED gingen */
public record ExpiredGrantsCount(int expiredFieldGrants, int expiredDocumentGrants) {
}

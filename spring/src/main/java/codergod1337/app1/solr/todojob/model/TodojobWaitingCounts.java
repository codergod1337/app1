package codergod1337.app1.solr.todojob.model;

/**
 * Bei wie vielen Jobs der Ball beim User liegt, für die Zahl im Benutzermenü (User 2026-10-16): als Servant die offenen
 * Jobs ohne fremden Claim und ohne offene Rückfrage, als Owner die Jobs in FERTIG und die mit offener Rückfrage.
 */
public record TodojobWaitingCounts(long servantCount, long ownerCount) {
}

package codergod1337.app1.solr.sync;

/**
 * Die Definition eines Kerns in Postgres hat sich geändert: ein Feld, ein Hook, ein Kern. Die Services schicken es nach
 * dem Commit, der SolrSchemaSyncService gleicht daraufhin Solr an. coreKey null: alle Kerne, z. B. nach einem Hook,
 * den jeder Kern hat.
 */
public record SolrDefinitionChangedEvent(String coreKey) {

}

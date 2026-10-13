package codergod1337.app1.abas.model;

/**
 * Die Zahlen der Warteschlange: gesamt (ab einer halben Million geschätzt), in Arbeit und mit Fehlversuchen.
 */
public record AbasSternzeitQueueCounts(long queueCount, boolean queueCountEstimated, long claimedCount,
		long failedCount) {
}

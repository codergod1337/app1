package codergod1337.app1.abas.model;

/**
 * Die Kopfzahlen der Übersicht: ein Blick, alle Bestände. Die beiden großen Tabellen werden ab einer halben Million
 * Zeilen geschätzt (Flag estimated), die kleinen Mengen kommen immer genau über ihre Teilindizes.
 */
public record AbasSternzeitOverviewCounts(long sternzeitCount, boolean sternzeitCountEstimated,
		long sternzeitWithoutObjectTypeCount, long queueCount, boolean queueCountEstimated, long queueClaimedCount,
		long queueFailedCount, long ignoredCount) {
}

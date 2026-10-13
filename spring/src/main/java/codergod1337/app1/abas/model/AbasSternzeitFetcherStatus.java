package codergod1337.app1.abas.model;

/**
 * Was die Übersicht über den Worker-Pool zeigt. processedCount und failedCount zählen seit dem Start des Backends
 * und nur im Speicher; die Oberfläche kann daraus Raten bilden.
 *
 * @param parallel    so viele Worker dürfen gleichzeitig holen
 * @param busyWorkers so viele davon arbeiten gerade
 */
public record AbasSternzeitFetcherStatus(AbasSternzeitFetcherState state, int parallel, int busyWorkers,
		long queueCount, boolean queueCountEstimated, long processedCount, long failedCount) {
}

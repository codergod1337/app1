package codergod1337.app1.abas.model;

/** Der Zustand des Worker-Pools, die große Badge der Übersicht (User 2026-10-10). */
public enum AbasSternzeitFetcherState {

	/** der Pool arbeitet: freie Einträge der Queue werden geholt und bearbeitet */
	RUNNING,

	/** der Schalter ist aus: die Queue füllt sich, verarbeitet wird nichts, bis jemand startet */
	PAUSED,

	/** abas_host oder abas_mandant fehlen in der .env: holen ist unmöglich, der Pool wartet */
	NOT_CONFIGURED

}

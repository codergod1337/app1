package codergod1337.app1.abas.model;

/**
 * Der Body von {@code POST /api/rest/v1/admin/abas/infosys/meta}: welches Infosystem beschrieben werden soll, alles im
 * Body, nichts in der URL.
 *
 * @param infosysGroup die ABAS-Gruppe, z. B. {@code fe}
 * @param infosysName  der Name, z. B. {@code PRODLIST}
 */
public record AbasInfosysMetaRequest(String infosysGroup, String infosysName) {
}

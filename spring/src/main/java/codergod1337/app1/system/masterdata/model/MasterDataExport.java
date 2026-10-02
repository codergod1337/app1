package codergod1337.app1.system.masterdata.model;

import java.util.Map;

/**
 * Ein fertiger Export. Das ZIP geht als Base64 im JSON, so läuft auch der Download über den einen Request-Client
 * des Frontends. Bei Stammdaten im KB-Bereich spielt das Drittel mehr keine Rolle.
 *
 * @param fileName z. B. stammdaten-2026-10-02-1430.zip
 * @param zip      das ZIP, von Jackson als Base64 geschrieben
 * @param counts   wie viele Datensätze je Bereich drinstehen
 */
public record MasterDataExport(String fileName, byte[] zip, Map<MasterDataSection, Integer> counts) {
}

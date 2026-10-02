package codergod1337.app1.system.masterdata.model;

import java.util.Set;

/** Was exportiert werden soll: die angehakten Bereiche. */
public record MasterDataExportRequest(Set<MasterDataSection> sections) {
}

package codergod1337.app1.dms.model;

/** Seite und Größe für die Dokumente, die dem current user freigegeben sind. null nimmt die Standards. */
public record DmsGrantedDocumentsPageRequest(Integer page, Integer size) {
}

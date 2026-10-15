package codergod1337.app1.solr.todojob.model;

import java.util.List;
import java.util.Map;

/** Die Suche über die eigenen Jobs: Suchtext und Chips, eines von beiden muss da sein, Seite ab 0 (null heißt 0) */
public record TodojobSearchRequest(String searchText, Map<String, List<String>> exactFilterValuesByFieldName, Integer page) {
}

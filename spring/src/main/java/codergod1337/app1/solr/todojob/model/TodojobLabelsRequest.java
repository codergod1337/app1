package codergod1337.app1.solr.todojob.model;

import java.util.List;

/** Labels (Anzeigeorte) und dauerhaftRelevant ändern, nur der Owner */
public record TodojobLabelsRequest(String id, List<String> displayContextKeys, boolean dauerhaftRelevant) {
}

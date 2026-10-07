package codergod1337.app1.graph.mail.model;

import java.util.List;

/** Ein Vorlagentyp mit seinen Platzhaltergruppen, für die Auswahl und die Hinweise im Editor. */
public record GraphMailTemplateTypeForClient(GraphMailTemplateType type,
		List<GraphMailPlaceholderGroupForClient> placeholderGroups) {
}

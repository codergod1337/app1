package codergod1337.app1.graph.mail.model;

import java.util.List;

/**
 * Eine Platzhaltergruppe, wie der Editor sie anzeigt: ihr Key und die Namen, die sich als {gruppe.name} einsetzen
 * lassen.
 */
public record GraphMailPlaceholderGroupForClient(String groupKey, List<String> placeholderNames) {
}

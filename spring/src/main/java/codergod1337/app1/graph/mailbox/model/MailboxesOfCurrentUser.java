package codergod1337.app1.graph.mailbox.model;

import java.util.List;

/**
 * Aus welchen Postfächern der angemeldete User archivieren darf (User 2026-10-13). Nur mit der Rolle des Archivierens.
 *
 * @param assignedMailboxes die Dienstpostfächer, an denen seine ARC oder eine ihrer Slave-ARCs steht, beim Admin alle
 * @param ownEmailAddress   seine eigene E-Mail-Adresse, klein geschrieben, zusätzlich wählbar; null, wenn sie nicht auf
 *                          die Domäne der Postfächer endet (app1.graph.mail.domain)
 */
public record MailboxesOfCurrentUser(List<Mailbox> assignedMailboxes, String ownEmailAddress) {
}

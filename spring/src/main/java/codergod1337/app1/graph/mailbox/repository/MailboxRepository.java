package codergod1337.app1.graph.mailbox.repository;

import codergod1337.app1.graph.mailbox.model.Mailbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxRepository extends JpaRepository<Mailbox, String> {

}

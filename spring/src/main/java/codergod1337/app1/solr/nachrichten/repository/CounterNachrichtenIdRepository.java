package codergod1337.app1.solr.nachrichten.repository;

import codergod1337.app1.solr.nachrichten.model.CounterNachrichtenId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterNachrichtenIdRepository extends JpaRepository<CounterNachrichtenId, Long> {
}

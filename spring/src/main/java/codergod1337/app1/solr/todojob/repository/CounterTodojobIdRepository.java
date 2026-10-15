package codergod1337.app1.solr.todojob.repository;

import codergod1337.app1.solr.todojob.model.CounterTodojobId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Nur save: jede gespeicherte Zeile ist die nächste Job-Nummer */
public interface CounterTodojobIdRepository extends JpaRepository<CounterTodojobId, Long> {
}

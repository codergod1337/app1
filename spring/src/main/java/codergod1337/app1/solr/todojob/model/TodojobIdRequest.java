package codergod1337.app1.solr.todojob.model;

/** Ein Vorgang, der nur die Nummer des Jobs braucht: Claim, Claim lösen, fertig melden, bestätigen, zurückziehen */
public record TodojobIdRequest(String id) {
}

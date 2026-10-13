package codergod1337.app1.abas.model;

/**
 * Eine Sternzeit ist in die Queue gekommen oder wurde dort umgestellt. Der Queue-Service schickt es nach dem Commit,
 * der Worker-Pool wacht daraufhin auf und holt sie in Millisekunden statt erst beim nächsten Takt des Watchers. So
 * zeigt der Queue-Service nie auf den Pool, und es entsteht kein Bean-Kreis.
 */
public record AbasSternzeitQueuedEvent(String sternzeit, AbasSternzeitQueueAction action) {

}

package codergod1337.app1.abas.model;

/**
 * Die Antwort auf ein Einreihen: welche Kennung mit welcher Aktion, und ob sie schon genau so in der Queue stand.
 * alreadyQueued false heißt neu eingereiht oder von der anderen Aktion umgestellt.
 */
public record AbasSternzeitQueueEnqueueResult(String sternzeit, AbasSternzeitQueueAction action,
		boolean alreadyQueued) {
}

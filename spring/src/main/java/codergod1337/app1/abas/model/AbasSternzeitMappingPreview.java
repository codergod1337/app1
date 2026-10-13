package codergod1337.app1.abas.model;

import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import java.util.Map;

/**
 * Was der Admin sieht, wenn er eine Sternzeit mappt: die Werte je Key aus dem Mapping und das Dokument, wie es in den
 * Kern geschrieben wird oder wurde. {@code document} ist null, wenn das Mapping kein Ziel mit Schreibweg hat.
 */
public record AbasSternzeitMappingPreview(ResponseMappingExecutionResult result, Map<String, Object> document,
		boolean written) {
}

package codergod1337.app1.mapping.controller;

import codergod1337.app1.mapping.model.ResponseMappingWithLines;
import codergod1337.app1.mapping.service.ResponseMappingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin legt Gesamt-Mappings an und pflegt sie, immer samt Einträgen. Keine Admin-Prüfung hier: Der Pfad
 * /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab. In der URL steht höchstens die Long-ID.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/mapping/response")
public class ResponseMappingAdminController {

	private final ResponseMappingService responseMappingService;

	public ResponseMappingAdminController(ResponseMappingService responseMappingService) {
		this.responseMappingService = responseMappingService;
	}

	/** Alle nach Key, je Mapping seine Einträge */
	@GetMapping
	public List<ResponseMappingWithLines> getAllResponseMappings() {
		return responseMappingService.getAllResponseMappings();
	}

	/** Eines per id samt Einträgen, 404 ohne */
	@GetMapping("/{id}")
	public ResponseMappingWithLines getResponseMapping(@PathVariable long id) {
		return responseMappingService.getResponseMappingById(id);
	}

	/** Alle Mappings zu einem Typ, auch leer. Body {"responseObjectType": "obj:data:19:0"}, 400 ohne Typ. */
	@PostMapping("/by-object-type")
	public List<ResponseMappingWithLines> getResponseMappingsByObjectType(@RequestBody Map<String, Object> typeRequest) {
		if (!(typeRequest.get("responseObjectType") instanceof String responseObjectType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "responseObjectType fehlt");
		}
		return responseMappingService.getResponseMappingsByObjectType(responseObjectType);
	}

	/** Mapping und Einträge, ohne ids. Key, Typ, Ziel, Bedingungen und jeden Eintrag prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseMappingWithLines createResponseMapping(@RequestBody ResponseMappingWithLines newResponseMappingData) {
		return responseMappingService.createResponseMapping(newResponseMappingData);
	}

	/** Mapping und Einträge. Die id im Mapping bestimmt, welches geändert wird, der Key ändert sich nie, die Einträge werden ersetzt. */
	@PutMapping
	public ResponseMappingWithLines updateResponseMapping(
			@RequestBody ResponseMappingWithLines changedResponseMappingData) {
		if (changedResponseMappingData.responseMapping() == null
				|| changedResponseMappingData.responseMapping().getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		return responseMappingService.updateResponseMapping(changedResponseMappingData.responseMapping().getId(),
				changedResponseMappingData);
	}

	/**
	 * Neue Positionen der Einträge nach Drag and Drop: jede Eintrags-id bekommt ihre Position, {"7": 1, "9": 2}. Das
	 * Frontend zählt durch. Zurück kommt das Mapping mit den Einträgen in neuer Reihenfolge.
	 */
	@PostMapping("/line/position")
	public ResponseMappingWithLines changeResponseMappingLinePositions(@RequestBody Map<String, Object> newPositionById) {
		Map<Long, Integer> checkedPositionById = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionById.entrySet()) {
			Long id;
			try {
				id = Long.valueOf(newPosition.getKey());
			} catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id " + newPosition.getKey() + " ist keine Zahl");
			}
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionById.put(id, position);
		}
		return responseMappingService.changeResponseMappingLinePositions(checkedPositionById);
	}

	/** Body {"id": 7}. 200 mit dem gelöschten Mapping, 404 wenn es die id nicht gibt. */
	@DeleteMapping
	public ResponseMappingWithLines deleteResponseMapping(@RequestBody Map<String, Object> mappingToDelete) {
		if (!(mappingToDelete.get("id") instanceof Number id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt oder ist keine Zahl");
		}
		return responseMappingService.deleteResponseMapping(id.longValue());
	}

}

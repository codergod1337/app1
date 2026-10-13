package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasObjectType;
import codergod1337.app1.abas.repository.AbasObjectTypeRepository;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Die ABAS-Objekttypen, ein paar Dutzend Zeilen, als Kopie im Speicher: Die Übersicht löst für jede Zeile die
 * object_type_id zum Text auf, und jeder Worker braucht zum Text die id; beides darf keine Datenbankabfrage je Zeile
 * kosten. Unveränderliche Maps, nur ersetzt, neu geladen nach jedem Anlegen. Angelegt wird ein Typ, wenn er zum ersten
 * Mal in einer Antwort steht.
 */
@Service
public class AbasObjectTypeService {

	private static final Logger log = LoggerFactory.getLogger(AbasObjectTypeService.class);

	private static final Sort BY_ID = Sort.by("id");

	private final AbasObjectTypeRepository abasObjectTypeRepository;

	/** Die ganze Tabelle nach id. Wird nur ersetzt, nie verändert. */
	private volatile Map<Short, AbasObjectType> abasObjectTypesById = Map.of();

	/** Dieselbe Tabelle nach Text, für die Worker */
	private volatile Map<String, AbasObjectType> abasObjectTypesByText = Map.of();

	public AbasObjectTypeService(AbasObjectTypeRepository abasObjectTypeRepository) {
		this.abasObjectTypeRepository = abasObjectTypeRepository;
	}

	@PostConstruct
	void loadAbasObjectTypes() {
		reloadAbasObjectTypes();
	}

	/** Alle Typen aus dem Speicher, nach id */
	public List<AbasObjectType> getAllAbasObjectTypes() {
		return List.copyOf(abasObjectTypesById.values());
	}

	/** Der Typ-String zu einer object_type_id aus dem Speicher; null bei null oder unbekannter id */
	public String getObjectTypeTextById(Short objectTypeId) {
		if (objectTypeId == null) {
			return null;
		}
		AbasObjectType abasObjectType = abasObjectTypesById.get(objectTypeId);
		return abasObjectType == null ? null : abasObjectType.getObjectType();
	}

	/**
	 * Der Typ zu einem Text aus dem Speicher, sonst aus der Datenbank, sonst neu angelegt. Für die Worker: Ein Typ, der
	 * zum ersten Mal in einer Antwort steht, bekommt hier seine id. synchronized, damit zwanzig Worker, die gleichzeitig
	 * denselben neuen Typ sehen, ihn nur einmal anlegen; ab dem zweiten Aufruf trifft der Speicher.
	 */
	public synchronized AbasObjectType getOrCreateAbasObjectType(String objectType) {
		AbasObjectType known = abasObjectTypesByText.get(objectType);
		if (known != null) {
			return known;
		}
		AbasObjectType created = abasObjectTypeRepository.findByObjectType(objectType)
				.orElseGet(() -> abasObjectTypeRepository.save(new AbasObjectType(objectType)));
		reloadAbasObjectTypes();
		log.info("ABAS-Objekttyp {} angelegt (id {})", objectType, created.getId());
		return created;
	}

	/** Die ganze Tabelle neu lesen und beide Maps ersetzen. */
	private void reloadAbasObjectTypes() {
		Map<Short, AbasObjectType> byId = new LinkedHashMap<>();
		Map<String, AbasObjectType> byText = new LinkedHashMap<>();
		for (AbasObjectType abasObjectType : abasObjectTypeRepository.findAll(BY_ID)) {
			byId.put(abasObjectType.getId(), abasObjectType);
			byText.put(abasObjectType.getObjectType(), abasObjectType);
		}
		abasObjectTypesById = Collections.unmodifiableMap(byId);
		abasObjectTypesByText = Collections.unmodifiableMap(byText);
	}

}

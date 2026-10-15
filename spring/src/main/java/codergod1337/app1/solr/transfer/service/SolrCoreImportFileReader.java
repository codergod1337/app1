package codergod1337.app1.solr.transfer.service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;

/**
 * Liest eine Import-Datei Dokument für Dokument als Strom (User 2026-10-14), nie mehr als ein Dokument im Speicher.
 * Gepackt (gzip, an den ersten zwei Bytes erkannt) oder ungepackt, NDJSON (eine Zeile je Dokument; leere Zeilen
 * zählen mit, damit die Zeilennummer die der Datei ist) oder eine JSON-Liste [{…},{…}] (die Zeile ist dann die Stelle in
 * der Liste). Eine Zeile, die kein JSON-Objekt ist, kommt mit ihrem Grund statt mit Feldern; in einer Liste endet das
 * Lesen danach, denn ohne Zeilen findet der Parser keinen Wiedereinstieg.
 */
public class SolrCoreImportFileReader implements Closeable {

	/** Ein Dokument der Datei: seine Zeile und seine Felder, oder warum es nicht lesbar ist */
	public record Entry(long line, Map<String, Object> fields, String unreadableReason) {
	}

	private static final TypeReference<LinkedHashMap<String, Object>> DOCUMENT = new TypeReference<>() {
	};

	/** So weit schaut der Leser vorab, ob eine Liste beginnt */
	private static final int LOOKAHEAD_CHARS = 1 << 12;

	private static final char BYTE_ORDER_MARK = '﻿';

	private final ObjectMapper objectMapper;
	private final BufferedReader reader;
	private final MappingIterator<LinkedHashMap<String, Object>> listIterator;
	private long line;
	private boolean listBroken;

	public SolrCoreImportFileReader(Path file, ObjectMapper objectMapper) throws IOException {
		this.objectMapper = objectMapper;
		InputStream raw = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
		raw.mark(2);
		int first = raw.read();
		int second = raw.read();
		raw.reset();
		InputStream input = first == 0x1f && second == 0x8b ? new GZIPInputStream(raw, 1 << 16) : raw;
		BufferedReader buffered = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 1 << 16);
		buffered.mark(LOOKAHEAD_CHARS);
		int character;
		do {
			character = buffered.read();
		} while (character != -1 && (Character.isWhitespace(character) || character == BYTE_ORDER_MARK));
		buffered.reset();
		if (character == '[') {
			this.listIterator = objectMapper.readerFor(DOCUMENT).readValues(buffered);
			this.reader = buffered;
		} else {
			this.listIterator = null;
			this.reader = buffered;
		}
	}

	/** Das nächste Dokument, null am Ende der Datei */
	public Entry next() throws IOException {
		if (listIterator != null) {
			return nextOfList();
		}
		String text;
		while ((text = reader.readLine()) != null) {
			line++;
			if (line == 1 && !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) {
				text = text.substring(1);
			}
			if (text.isBlank()) {
				continue;
			}
			try {
				return new Entry(line, objectMapper.readValue(text, DOCUMENT), null);
			} catch (JacksonException e) {
				return new Entry(line, null, e.getOriginalMessage());
			}
		}
		return null;
	}

	private Entry nextOfList() {
		if (listBroken) {
			return null;
		}
		try {
			if (!listIterator.hasNextValue()) {
				return null;
			}
			line++;
			return new Entry(line, listIterator.nextValue(), null);
		} catch (JacksonException e) {
			listBroken = true;
			line++;
			return new Entry(line, null, e.getOriginalMessage() + " (danach liest der Import diese Liste nicht weiter)");
		}
	}

	@Override
	public void close() throws IOException {
		if (listIterator != null) {
			listIterator.close();
		}
		reader.close();
	}

}

package codergod1337.app1.system.tika;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

/**
 * Liest mit Apache Tika, was in den Bytes einer Datei steckt: den Text und die Metadaten. Das Format erkennt Tika an
 * den Bytes, nicht an der Endung oder am Content-Type des Browsers, die zählen nur als Hinweis. Kein OCR, keine
 * Spracherkennung, kein Stemming: Das sind eigene Schritte, die später auf dem Text aufsetzen.
 *
 * Der Parser ist einer für alle Aufrufe. Tika erlaubt das, und das Nachschlagen der Parser je Format ist beim Anlegen
 * teuer. Eingebettete Dokumente (Anhänge einer Mail, Dateien in einem Archiv) liest er mit, ihr Text hängt am Ende an.
 */
@Service
public class TikaService {

	private final AutoDetectParser parser = new AutoDetectParser();

	/**
	 * Alles, was Tika gefunden hat. mimeType, createdAt, modifiedAt und orientation (EXIF-Ausrichtung 1 bis 8, null
	 * ohne Angabe) sind die Angaben, die das DMS immer braucht, herausgegriffen aus metadata, das jeden Eintrag trägt
	 * (Tika-Name, Werte). text ist der Volltext ohne Markup, leer bei einem Format ohne Text (Bild) oder einem, das
	 * Tika nicht kennt.
	 */
	public record TikaResult(String mimeType, Instant createdAt, Instant modifiedAt, Integer orientation, String text,
			Map<String, List<String>> metadata, long durationMs) {
	}

	/** Tika konnte die Bytes nicht lesen, z. B. ein verschlüsseltes PDF oder eine kaputte Datei. Der Grund steht in der Meldung. */
	public static class TikaReadException extends RuntimeException {

		public TikaReadException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/**
	 * Liest den Strom, so weit Tika ihn braucht. fileName und contentTypeHint helfen der Formaterkennung (Endung,
	 * Angabe des Browsers), entscheiden sie aber nicht. Der Strom wird nicht geschlossen, das macht der Aufrufer.
	 *
	 * @throws TikaReadException wenn Tika die Datei nicht lesen kann
	 */
	public TikaResult read(InputStream in, String fileName, String contentTypeHint) {
		long start = System.nanoTime();
		Metadata metadata = new Metadata();
		if (fileName != null && !fileName.isBlank()) {
			metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
		}
		if (contentTypeHint != null && !contentTypeHint.isBlank()) {
			metadata.set(TikaCoreProperties.CONTENT_TYPE_HINT, contentTypeHint);
		}
		// -1: keine Grenze für den Text, die Datei ist so groß, wie sie ist
		BodyContentHandler handler = new BodyContentHandler(-1);
		ParseContext context = new ParseContext();
		context.set(Parser.class, parser);
		try {
			parser.parse(new UnclosableInputStream(in), handler, metadata, context);
		} catch (Exception e) {
			throw new TikaReadException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
		}
		Map<String, List<String>> allMetadata = new TreeMap<>();
		for (String name : metadata.names()) {
			allMetadata.put(name, List.of(metadata.getValues(name)));
		}
		return new TikaResult(metadata.get(Metadata.CONTENT_TYPE), toInstant(metadata.getDate(TikaCoreProperties.CREATED)),
				toInstant(metadata.getDate(TikaCoreProperties.MODIFIED)), metadata.getInt(TIFF.ORIENTATION),
				handler.toString(), allMetadata, (System.nanoTime() - start) / 1_000_000);
	}

	private static Instant toInstant(Date date) {
		return date == null ? null : date.toInstant();
	}

	/**
	 * Schirmt den Strom des Aufrufers ab: Tika soll ihn laut Vertrag nicht schließen, manche Parser tun es trotzdem.
	 * Der Aufrufer liest danach weiter, z. B. den Rest für den Hash, und schließt selbst.
	 */
	private static class UnclosableInputStream extends FilterInputStream {

		UnclosableInputStream(InputStream in) {
			super(in);
		}

		@Override
		public void close() {
			// bewusst nichts
		}

	}

}

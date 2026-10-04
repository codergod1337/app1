package codergod1337.app1.dms.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;

/**
 * Rechnet aus den Bytes eines Bildes das Vorschaubild für das Feld bildVorschauBase64Png: immer 200 x 200 Pixel, PNG
 * mit Transparenz, als Base64 ohne Präfix. Die Vorschau liegt im Solr-Dokument, eine Trefferliste zeigt sie direkt aus
 * der Suchantwort, ohne eine Datei vom SFTP zu holen. Nach dem BildVorschauService der Vorlage, mit drei Zugaben:
 *
 * 1. Lesen mit Unterabtastung: Ein Foto mit 24 Megapixeln wäre dekodiert rund 96 MB groß. Der Leser lässt beim
 *    Dekodieren Zeilen und Spalten aus, so dass die kurze Seite noch mindestens das Doppelte der Vorschau hat, mehr
 *    braucht die Verkleinerung nicht. Das Raster bleibt klein, egal wie groß die Datei ist.
 * 2. Ausrichten nach EXIF (tiff:Orientation, kommt aus Tika): Ein Handyfoto liegt oft gedreht in der Datei, die
 *    Vorschau steht trotzdem richtig.
 * 3. Verkleinern in Stufen: halbieren, bis die nächste Halbierung zu klein wäre, dann der letzte Schritt bikubisch.
 *    Ein einziger Schritt von groß auf 200 Pixel lässt Treppen und Flimmern zurück.
 *
 * Eingepasst, nicht zugeschnitten (Entscheid der Vorlage 2026-08-27): Das ganze Bild wird so verkleinert, dass es in
 * die Fläche passt, der Rest bleibt transparent, symmetrisch verteilt. Nichts wird abgeschnitten, bei einer Zeichnung
 * wären das ausgerechnet die Ränder. PNG, weil nur PNG die Transparenz trägt.
 *
 * Formate: Das JDK liest jpg, png, gif, bmp, tiff und wbmp. TwelveMonkeys (pom) ersetzt den JPEG-Leser des JDK und
 * liest WebP. HEIC bräuchte native Bibliotheken, das gibt es hier nicht.
 */
@Service
public class PreviewImageService {

	/** Kantenlänge der Vorschau in Pixeln, Breite und Höhe. Der String hängt an jedem Bild-Dokument, deshalb klein. */
	public static final int SIZE = 200;

	/** Das fertige Vorschaubild und was die Quelle war */
	public record PreviewImage(String base64Png, int sourceWidth, int sourceHeight, int orientation, int bytes) {
	}

	/** Aus diesen Bytes ist kein Bild zu machen: falsches Format, kein Leser, kaputte Datei */
	public static class NoImageDataException extends RuntimeException {

		public NoImageDataException(String message) {
			super(message);
		}

		public NoImageDataException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/**
	 * Liest das Bild aus dem Strom und rechnet die Vorschau. exifOrientation 1 bis 8 nach EXIF, null oder 1: wie
	 * gespeichert. Der Strom wird nicht geschlossen.
	 *
	 * @throws NoImageDataException wenn kein Bild darin ist
	 */
	public PreviewImage render(InputStream in, Integer exifOrientation) {
		int orientation = exifOrientation == null ? 1 : exifOrientation;
		Decoded decoded = decode(in);
		BufferedImage oriented = orient(decoded.image(), orientation);
		byte[] png = encodePng(fit(oriented));
		return new PreviewImage(Base64.getEncoder().encodeToString(png), decoded.sourceWidth(), decoded.sourceHeight(),
				orientation, png.length);
	}

	private record Decoded(BufferedImage image, int sourceWidth, int sourceHeight) {
	}

	/** Schritt 1: dekodieren, bei großen Bildern mit Unterabtastung, die kurze Seite bleibt mindestens 2 x SIZE */
	private static Decoded decode(InputStream in) {
		try (ImageInputStream imageIn = ImageIO.createImageInputStream(in)) {
			if (imageIn == null) {
				throw new NoImageDataException("kein Bildstrom");
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageIn);
			if (!readers.hasNext()) {
				throw new NoImageDataException("kein Leser für dieses Format");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(imageIn, true, true);
				int sourceWidth = reader.getWidth(0);
				int sourceHeight = reader.getHeight(0);
				int subsampling = Math.max(1, Math.min(sourceWidth, sourceHeight) / (2 * SIZE));
				ImageReadParam param = reader.getDefaultReadParam();
				param.setSourceSubsampling(subsampling, subsampling, 0, 0);
				BufferedImage image = reader.read(0, param);
				if (image == null) {
					throw new NoImageDataException("der Leser lieferte kein Bild");
				}
				return new Decoded(image, sourceWidth, sourceHeight);
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof NoImageDataException noImage) {
				throw noImage;
			}
			throw new NoImageDataException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
		}
	}

	/** Schritt 2: nach EXIF ausrichten. 1 normal, 2 gespiegelt, 3 um 180 gedreht, 4 gestürzt, 5 bis 8 um 90 gedreht, teils gespiegelt. */
	private static BufferedImage orient(BufferedImage image, int orientation) {
		return switch (orientation) {
			case 2 -> flip(image, true);
			case 3 -> rotate(image, 180);
			case 4 -> flip(image, false);
			case 5 -> flip(rotate(image, 90), true);
			case 6 -> rotate(image, 90);
			case 7 -> flip(rotate(image, 90), false);
			case 8 -> rotate(image, 270);
			default -> image;
		};
	}

	/** im Uhrzeigersinn um 90, 180 oder 270 Grad, die Fläche dreht mit */
	private static BufferedImage rotate(BufferedImage image, int degrees) {
		boolean quarter = degrees == 90 || degrees == 270;
		int width = quarter ? image.getHeight() : image.getWidth();
		int height = quarter ? image.getWidth() : image.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.translate((width - image.getWidth()) / 2.0, (height - image.getHeight()) / 2.0);
			graphics.rotate(Math.toRadians(degrees), image.getWidth() / 2.0, image.getHeight() / 2.0);
			graphics.drawImage(image, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	/** horizontal (links nach rechts) oder vertikal (oben nach unten) spiegeln */
	private static BufferedImage flip(BufferedImage image, boolean horizontal) {
		AffineTransform transform = horizontal
				? new AffineTransform(-1, 0, 0, 1, image.getWidth(), 0)
				: new AffineTransform(1, 0, 0, -1, 0, image.getHeight());
		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.drawImage(image, transform, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	/**
	 * Schritt 3: in die Fläche einpassen. Der kleinere Faktor entscheidet, das Verhältnis bleibt, der Rest der Fläche
	 * bleibt transparent. Erst halbieren, solange beide Seiten danach noch mindestens Zielgröße haben, zuletzt bikubisch
	 * auf das Ziel und mittig auf die leere ARGB-Fläche.
	 */
	private static BufferedImage fit(BufferedImage image) {
		double factor = Math.min((double) SIZE / image.getWidth(), (double) SIZE / image.getHeight());
		int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * factor));
		int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * factor));

		BufferedImage current = image;
		while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
			current = scale(current, current.getWidth() / 2, current.getHeight() / 2,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}

		BufferedImage canvas = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(current, (SIZE - targetWidth) / 2, (SIZE - targetHeight) / 2, targetWidth, targetHeight,
					null);
		} finally {
			graphics.dispose();
		}
		return canvas;
	}

	private static BufferedImage scale(BufferedImage image, int width, int height, Object interpolation) {
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			graphics.drawImage(image, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	/** Schritt 4: PNG kodieren. Scheitert nur ohne PNG-Writer in der JVM, das ist ein Umgebungsfehler. */
	private static byte[] encodePng(BufferedImage image) {
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, "png", png)) {
				throw new IllegalStateException("kein PNG-Writer in dieser JVM");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Vorschaubild konnte nicht als PNG kodiert werden", e);
		}
		return png.toByteArray();
	}

}

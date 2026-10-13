package codergod1337.app1.file.filedata.service;

import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Das Vorschaubild einer Bilddatei für die Trefferbox: die Bytes mit ImageIO lesen, nach der EXIF-Ausrichtung drehen,
 * auf höchstens SIZE Pixel Kantenlänge verkleinern, als PNG in Base64. Welche Endungen ein Vorschaubild bekommen, sagt
 * der Katalog der Endungen (imagePreview), nicht dieser Service.
 */
@Service
public class PreviewImageService {

	/** die längere Kante des Vorschaubilds in Pixeln */
	public static final int SIZE = 200;

	/** Das fertige Vorschaubild: Base64 ohne Präfix, die Maße des Originals, die angewandte Ausrichtung, die PNG-Größe */
	public record PreviewImage(String base64Png, int sourceWidth, int sourceHeight, int orientation, int bytes) {
	}

	/** Die Bytes sind kein Bild, das ImageIO lesen kann (unbekanntes Format, kaputt, leer) */
	public static class NoImageDataException extends RuntimeException {

		public NoImageDataException(String message) {
			super(message);
		}

	}

	public PreviewImage render(InputStream in, Integer exifOrientation) {
		// Der Upload ruft das für Endungen mit Vorschaubild: liest, dreht, verkleinert, verpackt; der Strom wird nicht
		// geschlossen, das macht der Aufrufer
		BufferedImage source;
		try {
			source = ImageIO.read(in);
		} catch (IOException e) {
			throw new NoImageDataException("Bild nicht lesbar: " + e.getMessage());
		}
		if (source == null) {
			throw new NoImageDataException("kein lesbares Bild");
		}
		int orientation = exifOrientation == null ? 1 : exifOrientation;
		BufferedImage oriented = withOrientation(source, orientation);
		// verkleinern, nie vergrößern
		double scale = Math.min(1.0, (double) SIZE / Math.max(oriented.getWidth(), oriented.getHeight()));
		int targetWidth = Math.max(1, (int) Math.round(oriented.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(oriented.getHeight() * scale));
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		var graphics = target.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(oriented, 0, 0, targetWidth, targetHeight, null);
		graphics.dispose();
		try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
			ImageIO.write(target, "png", buffer);
			byte[] png = buffer.toByteArray();
			return new PreviewImage(Base64.getEncoder().encodeToString(png), source.getWidth(), source.getHeight(),
					orientation, png.length);
		} catch (IOException e) {
			throw new NoImageDataException("PNG ließ sich nicht schreiben: " + e.getMessage());
		}
	}

	private static BufferedImage withOrientation(BufferedImage image, int orientation) {
		// EXIF-Ausrichtung 1 bis 8: 1 ist normal, 2/4 gespiegelt, 3 um 180 Grad, 5 bis 8 um 90 Grad (mit Spiegelung)
		int width = image.getWidth();
		int height = image.getHeight();
		AffineTransform transform = new AffineTransform();
		boolean swapSides = false;
		switch (orientation) {
			case 2 -> {
				transform.scale(-1, 1);
				transform.translate(-width, 0);
			}
			case 3 -> {
				transform.translate(width, height);
				transform.rotate(Math.PI);
			}
			case 4 -> {
				transform.scale(1, -1);
				transform.translate(0, -height);
			}
			case 5 -> {
				transform.rotate(-Math.PI / 2);
				transform.scale(-1, 1);
				swapSides = true;
			}
			case 6 -> {
				transform.translate(height, 0);
				transform.rotate(Math.PI / 2);
				swapSides = true;
			}
			case 7 -> {
				transform.scale(-1, 1);
				transform.translate(-height, 0);
				transform.translate(0, width);
				transform.rotate(3 * Math.PI / 2);
				swapSides = true;
			}
			case 8 -> {
				transform.translate(0, width);
				transform.rotate(3 * Math.PI / 2);
				swapSides = true;
			}
			default -> {
				return image;
			}
		}
		BufferedImage oriented = new BufferedImage(swapSides ? height : width, swapSides ? width : height,
				BufferedImage.TYPE_INT_ARGB);
		var graphics = oriented.createGraphics();
		graphics.drawImage(image, transform, null);
		graphics.dispose();
		return oriented;
	}

}

package codergod1337.app1.message.archive.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/**
 * Eine Datei aus einer Mail als MultipartFile (User 2026-10-14): So geht ein Mail-Anhang durch denselben Upload wie jede
 * Datei aus der Uploadliste (FileDataService.upload), mit denselben Prüfungen, nur kommen die Bytes von Graph statt aus
 * dem Browser. Alles im Speicher. Die Testdaten (TestFakeDataService) laden ihre Datenblätter auf demselben Weg hoch.
 */
public class MailAttachmentMultipartFile implements MultipartFile {

	private final String fileName;
	private final String contentType;
	private final byte[] bytes;

	public MailAttachmentMultipartFile(String fileName, String contentType, byte[] bytes) {
		this.fileName = fileName;
		this.contentType = contentType;
		this.bytes = bytes;
	}

	/** Der Name des Formularteils, wie ihn der Upload-Endpunkt erwartet */
	@Override
	public String getName() {
		return "file";
	}

	@Override
	public String getOriginalFilename() {
		return fileName;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return bytes.length == 0;
	}

	@Override
	public long getSize() {
		return bytes.length;
	}

	@Override
	public byte[] getBytes() {
		return bytes.clone();
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(bytes);
	}

	@Override
	public void transferTo(File destination) throws IOException {
		Files.write(destination.toPath(), bytes);
	}

}

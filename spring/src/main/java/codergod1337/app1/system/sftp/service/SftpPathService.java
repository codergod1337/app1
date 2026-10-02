package codergod1337.app1.system.sftp.service;

import codergod1337.app1.system.sftp.model.SftpProperties;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Wohin eine Datei auf dem Dateispeicher gehört: JJJJ/MM/TT[_n]/<sha256>.bin, relativ zum root-dir.
 *
 * Nach Tagen aufgeteilt, weil Ordner mit sehr vielen Dateien langsam werden und der Pfad so für Menschen lesbar bleibt.
 * Ab max-files-per-directory Dateien am Tag kommt ein weiterer Ordner TT_1, TT_2 und so weiter. Der Zähler lebt nur im
 * Arbeitsspeicher und beginnt nach einem Neustart bei 0: Dann hat der erste Tagesordner mal mehr Dateien, das ist
 * harmlos. Der Dateiname ist der Hash des Inhalts, zwei verschiedene Dateien bekommen also nie denselben Pfad.
 */
@Service
public class SftpPathService {

	public static final String FILE_SUFFIX = ".bin";

	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

	private final int maxFilesPerDirectory;
	private LocalDate counterDate = LocalDate.now();
	private int storedOnCounterDate;

	public SftpPathService(SftpProperties properties) {
		this.maxFilesPerDirectory = Math.max(1, properties.maxFilesPerDirectory());
	}

	/** Der Tagesordner für die nächste Datei, z. B. 2026/10/02 oder 2026/10/02_1. Zählt die Datei mit. */
	public synchronized String nextRelativeDirectory() {
		LocalDate today = LocalDate.now();
		if (!today.equals(counterDate)) {
			counterDate = today;
			storedOnCounterDate = 0;
		}
		int directoryNumber = storedOnCounterDate++ / maxFilesPerDirectory;
		String day = String.format("%02d", today.getDayOfMonth());
		if (directoryNumber > 0) {
			day += "_" + directoryNumber;
		}
		return String.format("%d/%02d/%s", today.getYear(), today.getMonthValue(), day);
	}

	/** Der volle relative Pfad für eine Datei, deren Hash schon bekannt ist: Tagesordner plus <sha256>.bin */
	public String relativePath(String sha256Hex) {
		String normalized = sha256Hex == null ? "" : sha256Hex.trim().toLowerCase(Locale.ROOT);
		if (!SHA256_HEX.matcher(normalized).matches()) {
			throw new IllegalArgumentException("sha256Hex ist kein SHA-256 als Hex");
		}
		return nextRelativeDirectory() + "/" + normalized + FILE_SUFFIX;
	}

}

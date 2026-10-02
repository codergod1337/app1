package codergod1337.app1.system.sftp.model;

/**
 * Eine Datei, die der SftpService abgelegt und dabei selbst gehasht hat.
 *
 * @param relativePath Pfad relativ zum root-dir, z. B. 2026/10/02/<sha256>.bin. Den merkt sich der Aufrufer.
 * @param sha256Hex    SHA-256 des Inhalts als Hex, zugleich der Dateiname
 * @param sizeBytes    Größe, wie sie auf dem Server angekommen ist
 */
public record SftpStoredFile(String relativePath, String sha256Hex, long sizeBytes) {
}

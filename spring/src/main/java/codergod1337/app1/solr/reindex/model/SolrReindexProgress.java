package codergod1337.app1.solr.reindex.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Der Stand eines Laufs beim Nachziehen, geschrieben vom Lauf, gelesen vom Status: wie viele Dokumente der Kern hat,
 * wie viele geprüft, geändert und mit Fehler, ob abgebrochen werden soll. Die geänderten ids gehen in eine Datei, eine
 * je Zeile, nie in den Speicher: Bei 10^6 geänderten Dokumenten wären das rund 100 MB (User 2026-10-14). Nur die
 * ersten hundert hält der Stand zum Anzeigen. Ohne Datei (der Neuaufbau der Suche schreibt jedes Dokument) wird nur
 * gezählt.
 */
public class SolrReindexProgress {

	/** So viele geänderte ids zeigt der Stand, alle stehen in der Datei */
	public static final int SHOWN_CHANGED_IDS = 100;

	private final BufferedWriter changedIdsWriter;
	private final AtomicLong checked = new AtomicLong();
	private final AtomicLong changed = new AtomicLong();
	private final AtomicLong failed = new AtomicLong();
	private final List<String> firstChangedIds = new CopyOnWriteArrayList<>();

	/** Dokumente im Kern beim Start, -1 bis zur ersten Seite */
	private volatile long total = -1;
	private volatile boolean cancelRequested;
	private volatile String lastErrorMessage;

	/** @param changedIdsWriter die Datei der geänderten ids, null: nur zählen */
	public SolrReindexProgress(BufferedWriter changedIdsWriter) {
		this.changedIdsWriter = changedIdsWriter;
	}

	/** Führt der Lauf eine Liste der geänderten ids? */
	public boolean isChangedIdsRecorded() {
		return changedIdsWriter != null;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	/** Ein Dokument ist geprüft, geändert oder nicht */
	public void checked() {
		checked.incrementAndGet();
	}

	/** Ein Dokument ist geschrieben: seine id in die Datei, die ersten hundert auch in den Stand */
	public void changed(String id) {
		changed.incrementAndGet();
		if (changedIdsWriter == null) {
			return;
		}
		if (firstChangedIds.size() < SHOWN_CHANGED_IDS) {
			firstChangedIds.add(id);
		}
		try {
			changedIdsWriter.write(id);
			changedIdsWriter.newLine();
		} catch (IOException e) {
			throw new UncheckedIOException("id " + id + " nicht in die Liste geschrieben", e);
		}
	}

	/** Ein Dokument ist gescheitert, der Lauf geht weiter */
	public void failed(String id, String message) {
		failed.incrementAndGet();
		lastErrorMessage = id + ": " + message;
	}

	public void requestCancel() {
		cancelRequested = true;
	}

	public boolean isCancelRequested() {
		return cancelRequested;
	}

	public long getTotal() {
		return total;
	}

	public long getChecked() {
		return checked.get();
	}

	public long getChanged() {
		return changed.get();
	}

	public long getFailed() {
		return failed.get();
	}

	public List<String> getFirstChangedIds() {
		return List.copyOf(firstChangedIds);
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}

}

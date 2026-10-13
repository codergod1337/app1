package codergod1337.app1.abas.model;

import java.util.List;

/**
 * Was in einer Import-Datei steckt, der erste Schritt des Imports (User 2026-10-13): gelesen, gezählt, nichts
 * gespeichert. Während des Lesens kommt derselbe Record als Zwischenstand.
 *
 * @param sternzeitCount  alle Nennungen einer Sternzeit, Doppelte mitgezählt
 * @param invalidCount    Werte unter dem Key sternzeit, die keine Sternzeit in Klammerform sind
 * @param groups          je Objekttyp eine Gruppe, ohne Typ je Datenbank eine, die größten zuerst
 */
public record AbasSternzeitImportCheck(long sternzeitCount, long invalidCount, List<AbasSternzeitImportGroup> groups) {
}

package codergod1337.app1.message.archive.model;

import codergod1337.app1.abas.model.AbasBelegnummerLookupResult;

/**
 * Eine Nummer aus Betreff oder Mailtext, die ABAS kennt (User 2026-10-14).
 *
 * @param lookup           was die Belegnummer über sie weiß: Typ, Sternzeit, Mapping
 * @param suggestedHookKey der Hook, dessen abasObjectTypes diesen Typ nennen, dort steht sie vorbelegt; null ohne
 *                         Zuordnung, dann wählt der User den Hook
 */
public record MessageArchiveNumber(AbasBelegnummerLookupResult lookup, String suggestedHookKey) {
}

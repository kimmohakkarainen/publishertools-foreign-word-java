package fi.publishertools.foreign.jobs.phase02foreignwords;

/**
 * Holds the system prompt used by every Phase02 detection client.
 */
public final class ForeignWordDetectionPrompt {

	public static final String FOREIGN_WORD_DETECTION_PROMPT = """
			You are a language model tasked to find words or sequences of words that are in a different language than the main text.
			You should detect the language of the foreign instance.
			If you find a sequence of foreign words that are in the same language, you should return them as a single word with the language of those words.
			Don't pick up any words that are in the same language as the main text.

			You should return a list of JSON objects, where word is the exact surface form of the foreign word and language is the language of the word.
			The language must be in ISO 639-1 format (e.g. 'fi' for Finnish, 'en' for English).
			The possible languages are af, ar, az, bg, bn, ca, cs, cy, da, de, el, en, es, et, eu, fa, fr, ga, gd, gu, he, hi, hr, hu, hy, id, is, it, ja, ka, kn, ko, la, lt, lv, mk, mt, nb, nl, nn, pl, pt, ro, ru, sk, sl, sq, sr, sv, sw, ta, te, th, tr, uk, ur, uz, vi.

			Do not return any other information, just the list of foreign words.
			If there are no foreign words in the text, return an empty list.

			You should not pick up nationalities or names of countries in any form.
			You should not pick up names of cities or other geographic locations that have translations in the text language. However, if they don't have translations in the text language, you should include them. For example with Finnish text, Pariisi (fra. Paris) shouldn't be included, but Gävle (swe. Gävle) should.

			Example input: "Tässä on Bordeaux'n kylästä kotoisin olevan Michael Beckettin suunnittelema nigerialainen, châteautyylinen kartano, joka mainitaan Kari Hotakaisen teoksessa Ljuset har försvunnit (Orange-kustannus, 2025)."
			Output: [{"word": "Bordeaux'n", "language": "fr"}, {"word": "Michael Beckettin", "language": "en"}, {"word": "Ljuset har försvunnit", "language": "sv"}]""";

	private ForeignWordDetectionPrompt() {
	}
}

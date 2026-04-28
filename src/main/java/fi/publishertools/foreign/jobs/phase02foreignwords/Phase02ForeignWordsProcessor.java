package fi.publishertools.foreign.jobs.phase02foreignwords;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import fi.publishertools.foreign.jobs.PageText;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

/**
 * Placeholder foreign-word detection: picks one random token per page.
 */
public class Phase02ForeignWordsProcessor {

	public List<Words4TranscriptionItem> detectForeignWords(List<PageText> pages, String language) {
		List<Words4TranscriptionItem> out = new ArrayList<>();
		for (PageText p : pages) {
			List<String> tokens = tokenize(p.text());
			if (tokens.isEmpty()) {
				continue;
			}
			String word = tokens.get(ThreadLocalRandom.current().nextInt(tokens.size()));
			out.add(new Words4TranscriptionItem(List.of(), "", language, List.of(p.page()), "", word));
		}
		return out;
	}

	private static List<String> tokenize(String text) {
		List<String> tokens = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return tokens;
		}
		for (String part : text.split("\\s+")) {
			if (!part.isEmpty()) {
				tokens.add(part);
			}
		}
		return tokens;
	}
}

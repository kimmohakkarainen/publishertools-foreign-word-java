package fi.publishertools.foreign.phase3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

/**
 * Placeholder cross-page linking: merges {@link Words4TranscriptionItem#pages()} for duplicate {@code word} values.
 */
public class Phase03CrossPageProcessor {

	public List<Words4TranscriptionItem> mergeCrossPage(List<Words4TranscriptionItem> items) {
		Map<String, Words4TranscriptionItem> merged = new LinkedHashMap<>();
		for (Words4TranscriptionItem item : items) {
			merged.merge(item.word(), item, (a, b) -> {
				TreeSet<Integer> pages = new TreeSet<>(a.pages());
				pages.addAll(b.pages());
				return new Words4TranscriptionItem(
						a.inflections(),
						a.ipa(),
						a.language(),
						new ArrayList<>(pages),
						a.rawIpa(),
						a.word());
			});
		}
		return new ArrayList<>(merged.values());
	}
}

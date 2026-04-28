package fi.publishertools.foreign.phase3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;

/**
 * Placeholder cross-page linking: merges page references for duplicate words.
 */
public class Phase03CrossPageProcessor {

	public List<Words4PhaseItem> mergeCrossPage(List<Words4PhaseItem> items) {
		Map<String, Words4PhaseItem> merged = new LinkedHashMap<>();
		for (Words4PhaseItem item : items) {
			if (item == null || !item.hasWord()) {
				continue;
			}
			merged.merge(item.word(), item, (a, b) -> {
				TreeSet<Integer> pages = new TreeSet<>(a.pages());
				pages.addAll(b.pages());
				return a.withMergedPages(new ArrayList<>(pages));
			});
		}
		return new ArrayList<>(merged.values());
	}
}

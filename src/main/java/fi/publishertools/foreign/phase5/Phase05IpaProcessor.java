package fi.publishertools.foreign.phase5;

import java.util.List;

import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;

/**
 * Placeholder IPA step: uses {@code word} as both IPA fields until a real implementation exists.
 */
public class Phase05IpaProcessor {

	public List<Words4PhaseItem> addIpa(List<Words4PhaseItem> items) {
		return items.stream()
				.filter(Words4PhaseItem::hasWord)
				.map(i -> i.withIpa(i.word(), i.word()))
				.toList();
	}
}

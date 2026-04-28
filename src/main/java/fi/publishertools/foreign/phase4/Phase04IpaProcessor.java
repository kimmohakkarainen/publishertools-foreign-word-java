package fi.publishertools.foreign.phase4;

import java.util.List;

import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

/**
 * Placeholder IPA step: uses {@code word} as both IPA fields until a real implementation exists.
 */
public class Phase04IpaProcessor {

	public List<Words4TranscriptionItem> addIpa(List<Words4TranscriptionItem> items) {
		return items.stream()
				.map(i -> new Words4TranscriptionItem(
						i.inflections(),
						i.word(),
						i.language(),
						i.pages(),
						i.word(),
						i.word()))
				.toList();
	}
}

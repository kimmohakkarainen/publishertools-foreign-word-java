package fi.publishertools.foreign.jobs.dto;

import java.util.List;

public record Words4TranscriptionItem(
		List<String> inflections,
		String ipa,
		String language,
		List<Integer> pages,
		String rawIpa,
		String word) {
}

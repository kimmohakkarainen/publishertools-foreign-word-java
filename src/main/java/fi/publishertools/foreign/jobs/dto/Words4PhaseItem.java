package fi.publishertools.foreign.jobs.dto;

import java.util.List;

import fi.publishertools.foreign.jobs.PageText;

/**
 * Unified internal payload item used across words4 phases.
 */
public record Words4PhaseItem(
		Integer page,
		String sourceText,
		List<String> inflections,
		String ipa,
		String language,
		List<Integer> pages,
		String rawIpa,
		String word) {

	public static Words4PhaseItem fromPageText(PageText pageText) {
		return new Words4PhaseItem(
				pageText.page(),
				pageText.text(),
				List.of(),
				"",
				"",
				List.of(),
				"",
				"");
	}

	public static Words4PhaseItem fromDetectedWord(int pageNumber, String defaultLanguage, String detectedWord, String detectedLanguage) {
		String languageValue = (detectedLanguage == null || detectedLanguage.isBlank()) ? defaultLanguage : detectedLanguage;
		return new Words4PhaseItem(
				null,
				null,
				List.of(),
				"",
				languageValue,
				List.of(pageNumber),
				"",
				detectedWord);
	}

	public Words4PhaseItem withMergedPages(List<Integer> mergedPages) {
		return new Words4PhaseItem(page, sourceText, inflections, ipa, language, mergedPages, rawIpa, word);
	}

	public Words4PhaseItem withIpa(String ipaValue, String rawIpaValue) {
		return new Words4PhaseItem(page, sourceText, inflections, ipaValue, language, pages, rawIpaValue, word);
	}

	public Words4PhaseItem withWordInflectionsAndPages(String mergedWord, List<String> mergedInflections, List<Integer> mergedPages) {
		return new Words4PhaseItem(page, sourceText, mergedInflections, ipa, language, mergedPages, rawIpa, mergedWord);
	}

	public boolean hasSourceText() {
		return sourceText != null && !sourceText.isBlank() && page != null && page > 0;
	}

	public boolean hasWord() {
		return word != null && !word.isBlank();
	}

	public Words4TranscriptionItem toTranscriptionItem() {
		return new Words4TranscriptionItem(
				inflections == null ? List.of() : List.copyOf(inflections),
				ipa == null ? "" : ipa,
				language == null ? "" : language,
				pages == null ? List.of() : List.copyOf(pages),
				rawIpa == null ? "" : rawIpa,
				word == null ? "" : word);
	}
}

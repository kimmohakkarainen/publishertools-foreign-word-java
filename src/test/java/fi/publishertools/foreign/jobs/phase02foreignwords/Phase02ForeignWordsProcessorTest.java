package fi.publishertools.foreign.jobs.phase02foreignwords;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fi.publishertools.foreign.jobs.PageText;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

class Phase02ForeignWordsProcessorTest {

	@Test
	void mapsDetectedWordsToTranscriptionsPerPage() {
		Map<String, List<DetectedForeignWord>> canned = new HashMap<>();
		canned.put("page-one", List.of(
				new DetectedForeignWord("Bordeaux'n", "fr"),
				new DetectedForeignWord("Michael Beckettin", "en")));
		canned.put("page-two", List.of(
				new DetectedForeignWord("Ljuset har försvunnit", "sv")));
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(canned::get);

		List<Words4TranscriptionItem> out = processor.detectForeignWords(List.of(
				new PageText(1, "page-one"),
				new PageText(2, "page-two")), "fi");

		assertThat(out).hasSize(3);
		assertThat(out.get(0).word()).isEqualTo("Bordeaux'n");
		assertThat(out.get(0).language()).isEqualTo("fr");
		assertThat(out.get(0).pages()).containsExactly(1);
		assertThat(out.get(0).inflections()).isEmpty();
		assertThat(out.get(0).ipa()).isEmpty();
		assertThat(out.get(0).rawIpa()).isEmpty();
		assertThat(out.get(1).word()).isEqualTo("Michael Beckettin");
		assertThat(out.get(1).pages()).containsExactly(1);
		assertThat(out.get(2).word()).isEqualTo("Ljuset har försvunnit");
		assertThat(out.get(2).language()).isEqualTo("sv");
		assertThat(out.get(2).pages()).containsExactly(2);
	}

	@Test
	void skipsBlankPagesAndEmptyResults() {
		ForeignWordDetectionClient client = text -> List.of();
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(client);

		List<Words4TranscriptionItem> out = processor.detectForeignWords(List.of(
				new PageText(1, ""),
				new PageText(2, "  "),
				new PageText(3, "real text")), "en");

		assertThat(out).isEmpty();
	}

	@Test
	void fallsBackToDefaultLanguageWhenDetectorReturnsNoLanguage() {
		ForeignWordDetectionClient client = text -> List.of(new DetectedForeignWord("foo", null));
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(client);

		List<Words4TranscriptionItem> out = processor.detectForeignWords(
				List.of(new PageText(1, "anything")), "fi");

		assertThat(out).hasSize(1);
		assertThat(out.get(0).language()).isEqualTo("fi");
	}
}

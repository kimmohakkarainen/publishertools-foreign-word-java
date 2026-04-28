package fi.publishertools.foreign.jobs.phase03crosspage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

class Phase03CrossPageProcessorTest {

	private final Phase03CrossPageProcessor processor = new Phase03CrossPageProcessor();

	@Test
	void mergesDuplicateWordsIntoSortedPagesList() {
		List<Words4TranscriptionItem> input = List.of(
				new Words4TranscriptionItem(List.of(), "", "en", List.of(1), "", "hello"),
				new Words4TranscriptionItem(List.of(), "", "en", List.of(2), "", "hello"));
		List<Words4TranscriptionItem> out = processor.mergeCrossPage(input);
		assertThat(out).hasSize(1);
		assertThat(out.get(0).pages()).containsExactly(1, 2);
		assertThat(out.get(0).word()).isEqualTo("hello");
	}

	@Test
	void distinctWordsRemainSeparate() {
		List<Words4TranscriptionItem> input = List.of(
				new Words4TranscriptionItem(List.of(), "", "fi", List.of(1), "", "a"),
				new Words4TranscriptionItem(List.of(), "", "fi", List.of(2), "", "b"));
		List<Words4TranscriptionItem> out = processor.mergeCrossPage(input);
		assertThat(out).hasSize(2);
	}
}

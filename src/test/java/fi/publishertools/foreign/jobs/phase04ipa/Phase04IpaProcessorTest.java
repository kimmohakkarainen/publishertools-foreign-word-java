package fi.publishertools.foreign.jobs.phase04ipa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;
import fi.publishertools.foreign.phase4.Phase04IpaProcessor;

class Phase04IpaProcessorTest {

	private final Phase04IpaProcessor processor = new Phase04IpaProcessor();

	@Test
	void setsIpaAndRawIpaToWord() {
		List<Words4PhaseItem> input = List.of(
				new Words4PhaseItem(null, null, List.of(), "", "en", List.of(1), "", "Hello"));
		List<Words4PhaseItem> out = processor.addIpa(input);
		assertThat(out.get(0).ipa()).isEqualTo("Hello");
		assertThat(out.get(0).rawIpa()).isEqualTo("Hello");
		assertThat(out.get(0).word()).isEqualTo("Hello");
	}
}

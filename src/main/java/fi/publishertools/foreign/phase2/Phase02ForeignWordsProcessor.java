package fi.publishertools.foreign.phase2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.PageText;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

/**
 * Detects foreign words on each page by delegating to the configured
 * {@link ForeignWordDetectionClient} (Ollama or MS AI Foundry).
 * <p>
 * The {@code language} argument is the document's main language; it is currently
 * not forwarded to the LLM (the model determines each foreign word's language
 * itself), but is kept on the signature for caller compatibility.
 */
@Component
public class Phase02ForeignWordsProcessor {

	private final ForeignWordDetectionClient client;

	public Phase02ForeignWordsProcessor(ForeignWordDetectionClient client) {
		this.client = client;
	}

	public List<Words4TranscriptionItem> detectForeignWords(List<PageText> pages, String language) {
		List<Words4TranscriptionItem> out = new ArrayList<>();
		if (pages == null) {
			return out;
		}
		for (PageText page : pages) {
			if (page == null || page.text() == null || page.text().isBlank()) {
				continue;
			}
			List<DetectedForeignWord> detected = client.detect(page.text());
			if (detected == null || detected.isEmpty()) {
				continue;
			}
			for (DetectedForeignWord word : detected) {
				if (word == null || word.word() == null || word.word().isBlank()) {
					continue;
				}
				String wordLanguage = (word.language() == null || word.language().isBlank())
						? language
						: word.language();
				out.add(new Words4TranscriptionItem(
						List.of(),
						"",
						wordLanguage,
						List.of(page.page()),
						"",
						word.word()));
			}
		}
		return out;
	}
}

package fi.publishertools.foreign.phase2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;

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

	public List<Words4PhaseItem> detectForeignWords(List<Words4PhaseItem> items, String language) {
		List<Words4PhaseItem> out = new ArrayList<>();
		if (items == null) {
			return out;
		}
		for (Words4PhaseItem item : items) {
			if (item == null || !item.hasSourceText()) {
				continue;
			}
			List<DetectedForeignWord> detected = client.detect(item.sourceText());
			if (detected == null || detected.isEmpty()) {
				continue;
			}
			for (DetectedForeignWord word : detected) {
				if (word == null || word.word() == null || word.word().isBlank()) {
					continue;
				}
				out.add(Words4PhaseItem.fromDetectedWord(item.page(), language, word.word(), word.language()));
			}
		}
		return out;
	}
}

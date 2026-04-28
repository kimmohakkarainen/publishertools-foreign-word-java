package fi.publishertools.foreign.phase2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(Phase02ForeignWordsProcessor.class);
	private final Phase02PageDetectionDispatcher dispatcher;

	public Phase02ForeignWordsProcessor(Phase02PageDetectionDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	public List<Words4PhaseItem> detectForeignWords(List<Words4PhaseItem> items, String language) {
		List<Words4PhaseItem> out = new ArrayList<>();
		if (items == null) {
			return out;
		}
		List<PageDetectionResult> detectionResults = new ArrayList<>();
		for (Words4PhaseItem item : items.stream()
				.filter(item -> item != null && item.hasSourceText())
				.sorted((left, right) -> Integer.compare(left.page(), right.page()))
				.toList()) {
			CompletableFuture<List<DetectedForeignWord>> future = dispatcher.submit(item.sourceText());
			detectionResults.add(new PageDetectionResult(item, future));
		}
		for (PageDetectionResult detectionResult : detectionResults) {
			List<DetectedForeignWord> detected;
			try {
				detected = detectionResult.future().join();
			} catch (RuntimeException e) {
				log.info(
						"Page detection future failed; using empty result page={} reason={}",
						detectionResult.item().page(),
						e.getMessage());
				detected = List.of();
			}
			if (detected == null || detected.isEmpty()) {
				continue;
			}
			for (DetectedForeignWord word : detected) {
				if (word == null || word.word() == null || word.word().isBlank()) {
					continue;
				}
				out.add(Words4PhaseItem.fromDetectedWord(
						detectionResult.item().page(),
						language,
						word.word(),
						word.language()));
			}
		}
		return out;
	}

	private record PageDetectionResult(
			Words4PhaseItem item,
			CompletableFuture<List<DetectedForeignWord>> future) {
	}
}

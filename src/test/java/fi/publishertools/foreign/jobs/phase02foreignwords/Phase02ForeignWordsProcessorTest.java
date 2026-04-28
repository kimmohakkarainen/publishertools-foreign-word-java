package fi.publishertools.foreign.jobs.phase02foreignwords;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import fi.publishertools.foreign.jobs.PageText;
import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;
import fi.publishertools.foreign.phase2.DetectedForeignWord;
import fi.publishertools.foreign.phase2.ForeignWordDetectionClient;
import fi.publishertools.foreign.phase2.Phase02ForeignWordsProcessor;
import fi.publishertools.foreign.phase2.Phase02PageDetectionDispatcher;

class Phase02ForeignWordsProcessorTest {

	@Test
	void mapsDetectedWordsToTranscriptionsPerPage() {
		Map<String, List<DetectedForeignWord>> canned = new HashMap<>();
		canned.put("page-one", List.of(
				new DetectedForeignWord("Bordeaux'n", "fr"),
				new DetectedForeignWord("Michael Beckettin", "en")));
		canned.put("page-two", List.of(
				new DetectedForeignWord("Ljuset har försvunnit", "sv")));
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(
				text -> CompletableFuture.completedFuture(canned.get(text)));

		List<Words4PhaseItem> out = processor.detectForeignWords(List.of(
				Words4PhaseItem.fromPageText(new PageText(1, "page-one")),
				Words4PhaseItem.fromPageText(new PageText(2, "page-two"))), "fi");

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
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(
				text -> CompletableFuture.completedFuture(client.detect(text)));

		List<Words4PhaseItem> out = processor.detectForeignWords(List.of(
				Words4PhaseItem.fromPageText(new PageText(1, "")),
				Words4PhaseItem.fromPageText(new PageText(2, "  ")),
				Words4PhaseItem.fromPageText(new PageText(3, "real text"))), "en");

		assertThat(out).isEmpty();
	}

	@Test
	void fallsBackToDefaultLanguageWhenDetectorReturnsNoLanguage() {
		ForeignWordDetectionClient client = text -> List.of(new DetectedForeignWord("foo", null));
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(
				text -> CompletableFuture.completedFuture(client.detect(text)));

		List<Words4PhaseItem> out = processor.detectForeignWords(
				List.of(Words4PhaseItem.fromPageText(new PageText(1, "anything"))), "fi");

		assertThat(out).hasSize(1);
		assertThat(out.get(0).language()).isEqualTo("fi");
	}

	@Test
	void returnsDeterministicPageOrderingEvenWhenFuturesCompleteOutOfOrder() {
		Map<String, Integer> delaysByText = Map.of(
				"page-one", 80,
				"page-two", 10,
				"page-three", 40);
		Phase02PageDetectionDispatcher dispatcher = text -> CompletableFuture.supplyAsync(() -> {
			sleep(delaysByText.getOrDefault(text, 0));
			return List.of(new DetectedForeignWord(text + "-word", "en"));
		});
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(dispatcher);

		List<Words4PhaseItem> out = processor.detectForeignWords(List.of(
				Words4PhaseItem.fromPageText(new PageText(3, "page-three")),
				Words4PhaseItem.fromPageText(new PageText(1, "page-one")),
				Words4PhaseItem.fromPageText(new PageText(2, "page-two"))), "fi");

		assertThat(out).hasSize(3);
		assertThat(out).extracting(item -> item.pages().get(0)).containsExactly(1, 2, 3);
		assertThat(out).extracting(Words4PhaseItem::word).containsExactly(
				"page-one-word",
				"page-two-word",
				"page-three-word");
	}

	@Test
	void usesEmptyResultForFailedPageFutureAndKeepsSuccessfulPages() {
		Phase02PageDetectionDispatcher dispatcher = text -> {
			if ("bad".equals(text)) {
				return CompletableFuture.failedFuture(new IllegalStateException("llm failure"));
			}
			return CompletableFuture.completedFuture(List.of(new DetectedForeignWord(text + "-ok", "en")));
		};
		Phase02ForeignWordsProcessor processor = new Phase02ForeignWordsProcessor(dispatcher);

		List<Words4PhaseItem> out = processor.detectForeignWords(List.of(
				Words4PhaseItem.fromPageText(new PageText(1, "good")),
				Words4PhaseItem.fromPageText(new PageText(2, "bad"))), "fi");

		assertThat(out).hasSize(1);
		assertThat(out.get(0).word()).isEqualTo("good-ok");
		assertThat(out.get(0).pages()).containsExactly(1);
	}

	private static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted in test", e);
		}
	}
}

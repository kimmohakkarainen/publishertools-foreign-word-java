package fi.publishertools.foreign.jobs.phase04inflections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;
import fi.publishertools.foreign.phase4.MergedInflectionWord;
import fi.publishertools.foreign.phase4.Phase04InflectionMergeDispatcher;
import fi.publishertools.foreign.phase4.Phase04InflectionMergeProcessor;

class Phase04InflectionMergeProcessorTest {

	@Test
	void mergesCaseInsensitiveWordsAndCombinesPages() {
		Phase04InflectionMergeDispatcher dispatcher = words -> CompletableFuture
				.completedFuture(List.of(new MergedInflectionWord("John", List.of("Johnin", "johnille"))));
		Phase04InflectionMergeProcessor processor = new Phase04InflectionMergeProcessor(dispatcher);

		List<Words4PhaseItem> out = processor.mergeInflections(List.of(
				new Words4PhaseItem(null, null, List.of(), "", "en", List.of(1), "", "Johnin"),
				new Words4PhaseItem(null, null, List.of(), "", "en", List.of(3), "", "johnille"),
				new Words4PhaseItem(null, null, List.of(), "", "en", List.of(2), "", "John")));

		assertThat(out).hasSize(1);
		assertThat(out.get(0).word()).isEqualTo("John");
		assertThat(out.get(0).inflections()).containsExactly("johnille", "Johnin");
		assertThat(out.get(0).pages()).containsExactly(1, 2, 3);
	}

	@Test
	void skipsLlmCallForSingletonPrefixBucket() {
		Phase04InflectionMergeDispatcher dispatcher = words -> {
			throw new IllegalStateException("should not be called");
		};
		Phase04InflectionMergeProcessor processor = new Phase04InflectionMergeProcessor(dispatcher);
		Words4PhaseItem only = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(9), "", "Zed");

		List<Words4PhaseItem> out = processor.mergeInflections(List.of(only));
		assertThat(out).containsExactly(only);
	}

	@Test
	void fallsBackToOriginalBucketWhenLlmFails() {
		Phase04InflectionMergeDispatcher dispatcher = words -> CompletableFuture
				.failedFuture(new IllegalStateException("llm down"));
		Phase04InflectionMergeProcessor processor = new Phase04InflectionMergeProcessor(dispatcher);
		Words4PhaseItem a = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(1), "", "John");
		Words4PhaseItem b = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(2), "", "Johnin");

		List<Words4PhaseItem> out = processor.mergeInflections(List.of(a, b));
		assertThat(out).containsExactly(a, b);
	}

	@Test
	void joinsBucketsInDeterministicBucketOrder() {
		Phase04InflectionMergeDispatcher dispatcher = words -> {
			if (words.stream().anyMatch(word -> word.toLowerCase().startsWith("aaa"))) {
				return CompletableFuture.completedFuture(List.of(new MergedInflectionWord("aaa", List.of("aaab"))));
			}
			return CompletableFuture.completedFuture(List.of(new MergedInflectionWord("bbb", List.of("bbbc"))));
		};
		Phase04InflectionMergeProcessor processor = new Phase04InflectionMergeProcessor(dispatcher);
		Words4PhaseItem aaa = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(1), "", "aaa");
		Words4PhaseItem aaab = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(2), "", "aaab");
		Words4PhaseItem bbb = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(3), "", "bbb");
		Words4PhaseItem bbbc = new Words4PhaseItem(null, null, List.of(), "", "fi", List.of(4), "", "bbbc");

		List<Words4PhaseItem> out = processor.mergeInflections(List.of(aaa, aaab, bbb, bbbc));

		assertThat(out).hasSize(2);
		assertThat(out.get(0).word()).isEqualTo("aaa");
		assertThat(out.get(1).word()).isEqualTo("bbb");
	}
}

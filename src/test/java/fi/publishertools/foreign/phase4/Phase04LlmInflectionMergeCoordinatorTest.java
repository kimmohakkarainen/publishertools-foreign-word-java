package fi.publishertools.foreign.phase4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import fi.publishertools.foreign.phase2.ForeignWordsProperties;

class Phase04LlmInflectionMergeCoordinatorTest {

	@Test
	void retriesUntilThirdAttemptThenSucceeds() throws Exception {
		ConcurrentMap<String, AtomicInteger> attemptsByWord = new ConcurrentHashMap<>();
		InflectionMergeClient client = words -> {
			String key = String.join(",", words);
			int attempt = attemptsByWord.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
			if (attempt < 3) {
				throw new IllegalStateException("transient");
			}
			return List.of(new MergedInflectionWord("John", List.of("Johnin")));
		};
		Phase04LlmInflectionMergeCoordinator coordinator = new Phase04LlmInflectionMergeCoordinator(
				client,
				new ForeignWordsProperties(
						ForeignWordsProperties.PROVIDER_OLLAMA,
						null,
						null,
						null,
						new ForeignWordsProperties.Phase4(2, 16, null)));

		coordinator.start();
		try {
			List<String> words = List.of("John", "Johnin");
			List<MergedInflectionWord> result = coordinator.submit(words).get(2, TimeUnit.SECONDS);
			assertThat(result).hasSize(1);
			assertThat(result.get(0).word()).isEqualTo("John");
			assertThat(attemptsByWord.get("John,Johnin").get()).isEqualTo(3);
		} finally {
			coordinator.stop();
		}
	}

	@Test
	void returnsEmptyAfterThreeFailedAttempts() throws Exception {
		ConcurrentMap<String, AtomicInteger> attemptsByWord = new ConcurrentHashMap<>();
		InflectionMergeClient client = words -> {
			String key = String.join(",", words);
			attemptsByWord.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
			throw new IllegalStateException("always failing");
		};
		Phase04LlmInflectionMergeCoordinator coordinator = new Phase04LlmInflectionMergeCoordinator(
				client,
				new ForeignWordsProperties(
						ForeignWordsProperties.PROVIDER_OLLAMA,
						null,
						null,
						null,
						new ForeignWordsProperties.Phase4(1, 16, null)));

		coordinator.start();
		try {
			List<String> words = List.of("John", "Johnin");
			List<MergedInflectionWord> result = coordinator.submit(words).get(2, TimeUnit.SECONDS);
			assertThat(result).isEmpty();
			assertThat(attemptsByWord.get("John,Johnin").get()).isEqualTo(3);
		} finally {
			coordinator.stop();
		}
	}

	@Test
	void submitFailsAfterStop() {
		InflectionMergeClient client = words -> List.of();
		Phase04LlmInflectionMergeCoordinator coordinator = new Phase04LlmInflectionMergeCoordinator(
				client,
				new ForeignWordsProperties(
						ForeignWordsProperties.PROVIDER_OLLAMA,
						null,
						null,
						null,
						new ForeignWordsProperties.Phase4(1, 16, null)));
		coordinator.start();
		coordinator.stop();

		assertThat(coordinator.submit(List.of("a")).isCompletedExceptionally()).isTrue();
	}
}

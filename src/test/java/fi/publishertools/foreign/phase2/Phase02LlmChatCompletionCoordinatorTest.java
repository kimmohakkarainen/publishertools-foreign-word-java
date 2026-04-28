package fi.publishertools.foreign.phase2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class Phase02LlmChatCompletionCoordinatorTest {

	@Test
	void retriesUntilThirdAttemptThenSucceeds() throws Exception {
		ConcurrentMap<String, AtomicInteger> attemptsByText = new ConcurrentHashMap<>();
		ForeignWordDetectionClient client = text -> {
			int attempt = attemptsByText.computeIfAbsent(text, ignored -> new AtomicInteger()).incrementAndGet();
			if (attempt < 3) {
				throw new IllegalStateException("transient");
			}
			return List.of(new DetectedForeignWord("ok-" + attempt, "en"));
		};
		Phase02LlmChatCompletionCoordinator coordinator = new Phase02LlmChatCompletionCoordinator(
				client,
				new ForeignWordsProperties(
						ForeignWordsProperties.PROVIDER_OLLAMA,
						null,
						null,
						new ForeignWordsProperties.Phase2(1, 16)));

		coordinator.start();
		try {
			List<DetectedForeignWord> result = coordinator.submit("page-1").get(2, TimeUnit.SECONDS);
			assertThat(result).hasSize(1);
			assertThat(result.get(0).word()).isEqualTo("ok-3");
			assertThat(attemptsByText.get("page-1").get()).isEqualTo(3);
		} finally {
			coordinator.stop();
		}
	}

	@Test
	void returnsEmptyAfterThreeFailedAttempts() throws Exception {
		ConcurrentMap<String, AtomicInteger> attemptsByText = new ConcurrentHashMap<>();
		ForeignWordDetectionClient client = text -> {
			attemptsByText.computeIfAbsent(text, ignored -> new AtomicInteger()).incrementAndGet();
			throw new IllegalStateException("always failing");
		};
		Phase02LlmChatCompletionCoordinator coordinator = new Phase02LlmChatCompletionCoordinator(
				client,
				new ForeignWordsProperties(
						ForeignWordsProperties.PROVIDER_OLLAMA,
						null,
						null,
						new ForeignWordsProperties.Phase2(1, 16)));

		coordinator.start();
		try {
			List<DetectedForeignWord> result = coordinator.submit("page-2").get(2, TimeUnit.SECONDS);
			assertThat(result).isEmpty();
			assertThat(attemptsByText.get("page-2").get()).isEqualTo(3);
		} finally {
			coordinator.stop();
		}
	}
}

package fi.publishertools.foreign.phase2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase02LlmChatCompletionCoordinator implements Phase02PageDetectionDispatcher {

	private static final Logger log = LoggerFactory.getLogger(Phase02LlmChatCompletionCoordinator.class);
	private static final int MAX_ATTEMPTS = 3;
	private final ForeignWordDetectionClient client;
	private final ForeignWordsProperties properties;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final List<Thread> workerThreads = new ArrayList<>();
	private BlockingQueue<PageDetectionTask> queue;

	public Phase02LlmChatCompletionCoordinator(
			ForeignWordDetectionClient client,
			ForeignWordsProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@PostConstruct
	public void start() {
		int queueCapacity = properties.phase2().llmQueueCapacity();
		int workerCount = properties.phase2().llmWorkerCount();
		queue = new ArrayBlockingQueue<>(queueCapacity);
		for (int i = 0; i < workerCount; i++) {
			Thread worker = Thread.ofPlatform()
					.daemon()
					.name("phase02-llm-chat-worker-" + (i + 1))
					.unstarted(this::runLoop);
			workerThreads.add(worker);
			worker.start();
		}
	}

	@PreDestroy
	public void stop() {
		running.set(false);
		for (Thread workerThread : workerThreads) {
			workerThread.interrupt();
		}
	}

	@Override
	public CompletableFuture<List<DetectedForeignWord>> submit(String pageText) {
		if (!running.get()) {
			return CompletableFuture.failedFuture(new IllegalStateException("Phase02 LLM coordinator is stopped"));
		}
		CompletableFuture<List<DetectedForeignWord>> future = new CompletableFuture<>();
		try {
			queue.put(new PageDetectionTask(pageText, future, 1));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.completeExceptionally(new IllegalStateException("Interrupted while queuing page detection task", e));
		}
		return future;
	}

	private void runLoop() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				PageDetectionTask task = queue.take();
				try {
					String threadName = Thread.currentThread().getName();
					int chars = task.pageText() == null ? 0 : task.pageText().length();
					long startedAt = System.nanoTime();
					log.info(
							"LLM call started thread={} pageTextChars={} attempt={}/{}",
							threadName,
							chars,
							task.attempt(),
							MAX_ATTEMPTS);
					List<DetectedForeignWord> detectedWords = client.detect(task.pageText());
					long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
					int resultCount = detectedWords == null ? 0 : detectedWords.size();
					log.info(
							"LLM call finished thread={} pageTextChars={} resultCount={} elapsedMs={} attempt={}/{}",
							threadName,
							chars,
							resultCount,
							elapsedMs,
							task.attempt(),
							MAX_ATTEMPTS);
					task.future().complete(detectedWords);
				} catch (Exception e) {
					int chars = task.pageText() == null ? 0 : task.pageText().length();
					if (task.attempt() < MAX_ATTEMPTS) {
						int nextAttempt = task.attempt() + 1;
						log.info(
								"LLM call failed, requeueing at tail thread={} pageTextChars={} attempt={}/{} error={}",
								Thread.currentThread().getName(),
								chars,
								task.attempt(),
								MAX_ATTEMPTS,
								e.getMessage());
						try {
							queue.put(task.withAttempt(nextAttempt));
						} catch (InterruptedException interrupted) {
							Thread.currentThread().interrupt();
							task.future().complete(List.of());
						}
					} else {
						log.info(
								"LLM call failed after max retries; using empty result thread={} pageTextChars={} attempt={}/{} error={}",
								Thread.currentThread().getName(),
								chars,
								task.attempt(),
								MAX_ATTEMPTS,
								e.getMessage());
						task.future().complete(List.of());
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private record PageDetectionTask(
			String pageText,
			CompletableFuture<List<DetectedForeignWord>> future,
			int attempt) {

		private PageDetectionTask withAttempt(int nextAttempt) {
			return new PageDetectionTask(pageText, future, nextAttempt);
		}
	}
}

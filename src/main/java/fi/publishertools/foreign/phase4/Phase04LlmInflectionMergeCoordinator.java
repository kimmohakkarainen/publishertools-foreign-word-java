package fi.publishertools.foreign.phase4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.publishertools.foreign.phase2.ForeignWordsProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase04LlmInflectionMergeCoordinator implements Phase04InflectionMergeDispatcher {

	private static final Logger log = LoggerFactory.getLogger(Phase04LlmInflectionMergeCoordinator.class);
	private static final int MAX_ATTEMPTS = 3;
	private final InflectionMergeClient client;
	private final ForeignWordsProperties properties;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final List<Thread> workerThreads = new ArrayList<>();
	private BlockingQueue<InflectionMergeTask> queue;

	public Phase04LlmInflectionMergeCoordinator(
			InflectionMergeClient client,
			ForeignWordsProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@PostConstruct
	public void start() {
		int queueCapacity = properties.phase4().llmQueueCapacity();
		int workerCount = properties.phase4().llmWorkerCount();
		queue = new ArrayBlockingQueue<>(queueCapacity);
		for (int i = 0; i < workerCount; i++) {
			Thread worker = Thread.ofPlatform()
					.daemon()
					.name("phase04-llm-merge-worker-" + (i + 1))
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
	public CompletableFuture<List<MergedInflectionWord>> submit(List<String> words) {
		if (!running.get()) {
			return CompletableFuture.failedFuture(new IllegalStateException("Phase04 LLM coordinator is stopped"));
		}
		CompletableFuture<List<MergedInflectionWord>> future = new CompletableFuture<>();
		try {
			queue.put(new InflectionMergeTask(words, future, 1));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.completeExceptionally(new IllegalStateException("Interrupted while queuing inflection merge task", e));
		}
		return future;
	}

	private void runLoop() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				InflectionMergeTask task = queue.take();
				try {
					int wordCount = task.words() == null ? 0 : task.words().size();
					long startedAt = System.nanoTime();
					log.info(
							"Inflection LLM call started thread={} bucketWordCount={} attempt={}/{}",
							Thread.currentThread().getName(),
							wordCount,
							task.attempt(),
							MAX_ATTEMPTS);
					List<MergedInflectionWord> mergedWords = client.detectInflections(task.words());
					long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
					int resultCount = mergedWords == null ? 0 : mergedWords.size();
					log.info(
							"Inflection LLM call finished thread={} bucketWordCount={} resultCount={} elapsedMs={} attempt={}/{}",
							Thread.currentThread().getName(),
							wordCount,
							resultCount,
							elapsedMs,
							task.attempt(),
							MAX_ATTEMPTS);
					task.future().complete(mergedWords == null ? List.of() : mergedWords);
				} catch (Exception e) {
					int wordCount = task.words() == null ? 0 : task.words().size();
					if (task.attempt() < MAX_ATTEMPTS) {
						log.info(
								"Inflection LLM call failed, requeueing at tail thread={} bucketWordCount={} attempt={}/{} error={}",
								Thread.currentThread().getName(),
								wordCount,
								task.attempt(),
								MAX_ATTEMPTS,
								e.getMessage());
						try {
							queue.put(task.withAttempt(task.attempt() + 1));
						} catch (InterruptedException interrupted) {
							Thread.currentThread().interrupt();
							task.future().complete(List.of());
						}
					} else {
						log.info(
								"Inflection LLM call failed after max retries; using empty result thread={} bucketWordCount={} attempt={}/{} error={}",
								Thread.currentThread().getName(),
								wordCount,
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

	private record InflectionMergeTask(
			List<String> words,
			CompletableFuture<List<MergedInflectionWord>> future,
			int attempt) {

		private InflectionMergeTask withAttempt(int nextAttempt) {
			return new InflectionMergeTask(words, future, nextAttempt);
		}
	}
}

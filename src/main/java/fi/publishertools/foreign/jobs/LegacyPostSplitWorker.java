package fi.publishertools.foreign.jobs;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Consumes jobs after phase 1 split and produces the legacy text summary result (non-Words4 uploads).
 */
@Component
public class LegacyPostSplitWorker {

	private final JobService jobService;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public LegacyPostSplitWorker(JobService jobService) {
		this.jobService = jobService;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("legacy-post-split-worker")
				.unstarted(this::runLoop);
		workerThread.start();
	}

	@PreDestroy
	public void stop() {
		running.set(false);
		if (workerThread != null) {
			workerThread.interrupt();
		}
	}

	private void runLoop() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				String id = jobService.legacyPostSplitJobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null || job.getStatus() == JobStatus.ERROR) {
					continue;
				}
				try {
					job.setPhase(JobPhase.LEGACY_PROCESSING);
					String result = processLegacy(job);
					job.setResult(result);
					job.setStatus(JobStatus.FINISHED);
					job.setPhase(JobPhase.COMPLETED);
				} catch (Exception e) {
					failJob(job, e);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private static void failJob(Job job, Exception e) {
		String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
		job.setErrorMessage(msg);
		job.setStatus(JobStatus.ERROR);
		job.setPhase(JobPhase.FAILED);
	}

	String processLegacy(Job job) {
		List<PageText> pages = job.getPages().stream()
				.sorted(Comparator.comparingInt(PageText::page))
				.toList();
		int pageCount = pages.size();
		int totalWords = pages.stream().map(PageText::text).mapToInt(this::countWords).sum();
		String preview = pageCount > 0
				? pages.get(0).text().substring(0, Math.min(120, pages.get(0).text().length())).replaceAll("\\R", " ")
				: "";
		String descriptionSuffix = (job.getDescription() == null || job.getDescription().isBlank())
				? ""
				: " Description: " + job.getDescription();
		return "Processed " + pageCount + " pages and " + totalWords + " words from "
				+ job.getOriginalFilename() + ". First page preview: " + preview + descriptionSuffix;
	}

	private int countWords(String text) {
		int count = 0;
		boolean inWord = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (isWordChar(ch)) {
				if (!inWord) {
					count++;
					inWord = true;
				}
			} else {
				inWord = false;
			}
		}
		return count;
	}

	private boolean isWordChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '\'';
	}
}

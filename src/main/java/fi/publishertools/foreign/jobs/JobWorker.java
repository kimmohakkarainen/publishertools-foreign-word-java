package fi.publishertools.foreign.jobs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Two daemon workers: phase 1 splits pages, phase 2 processes split pages.
 */
@Component
public class JobWorker {

	private final JobService jobService;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread splitWorkerThread;
	private Thread processWorkerThread;

	public JobWorker(JobService jobService) {
		this.jobService = jobService;
	}

	@PostConstruct
	public void start() {
		splitWorkerThread = Thread.ofPlatform()
				.daemon()
				.name("job-split-worker")
				.unstarted(this::runSplitLoop);
		processWorkerThread = Thread.ofPlatform()
				.daemon()
				.name("job-process-worker")
				.unstarted(this::runProcessLoop);
		splitWorkerThread.start();
		processWorkerThread.start();
	}

	@PreDestroy
	public void stop() {
		running.set(false);
		if (splitWorkerThread != null) {
			splitWorkerThread.interrupt();
		}
		if (processWorkerThread != null) {
			processWorkerThread.interrupt();
		}
	}

	private void runSplitLoop() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				String id = jobService.phaseOneJobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null) {
					continue;
				}
				try {
					job.setPhase(JobPhase.SPLITTING);
					List<PageText> pages = splitIntoPages(job);
					job.setPages(pages);
					job.setPhase(JobPhase.QUEUED_FOR_PROCESSING);
					jobService.phaseTwoJobIds.put(id);
				} catch (Exception e) {
					failJob(job, e);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void runProcessLoop() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				String id = jobService.phaseTwoJobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null || job.getStatus() == JobStatus.ERROR) {
					continue;
				}
				try {
					job.setPhase(JobPhase.PROCESSING);
					String result = process(job);
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

	private void failJob(Job job, Exception e) {
		String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
		job.setErrorMessage(msg);
		job.setStatus(JobStatus.ERROR);
		job.setPhase(JobPhase.FAILED);
	}

	List<PageText> splitIntoPages(Job job) {
		String text = new String(job.getContent(), StandardCharsets.UTF_8);
		List<PageText> pages = new ArrayList<>();
		if (text.isEmpty()) {
			return pages;
		}

		StringBuilder page = new StringBuilder();
		int wordsInPage = 0;
		boolean waitForPunctuation = false;
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (isWordChar(ch)) {
				int start = i;
				i++;
				while (i < text.length() && isWordChar(text.charAt(i))) {
					i++;
				}
				page.append(text, start, i);
				wordsInPage++;
				if (wordsInPage >= 100) {
					waitForPunctuation = true;
				}
				continue;
			}

			page.append(ch);
			if (waitForPunctuation && isPunctuation(ch)) {
				pages.add(new PageText(pages.size() + 1, page.toString()));
				page.setLength(0);
				wordsInPage = 0;
				waitForPunctuation = false;
			}
			i++;
		}

		if (page.length() > 0) {
			pages.add(new PageText(pages.size() + 1, page.toString()));
		}
		return pages;
	}

	String process(Job job) {
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

	private boolean isPunctuation(char ch) {
		return switch (Character.getType(ch)) {
			case Character.CONNECTOR_PUNCTUATION,
					Character.DASH_PUNCTUATION,
					Character.START_PUNCTUATION,
					Character.END_PUNCTUATION,
					Character.INITIAL_QUOTE_PUNCTUATION,
					Character.FINAL_QUOTE_PUNCTUATION,
					Character.OTHER_PUNCTUATION -> true;
			default -> false;
		};
	}
}

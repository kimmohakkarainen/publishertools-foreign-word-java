package fi.publishertools.foreign.jobs.phase02foreignwords;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.Job;
import fi.publishertools.foreign.jobs.JobPhase;
import fi.publishertools.foreign.jobs.JobService;
import fi.publishertools.foreign.jobs.JobStatus;
import fi.publishertools.foreign.jobs.JobWords4;
import fi.publishertools.foreign.jobs.PageText;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase02ForeignWordsWorker {

	private final JobService jobService;
	private final Phase02ForeignWordsProcessor processor;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public Phase02ForeignWordsWorker(JobService jobService, Phase02ForeignWordsProcessor processor) {
		this.jobService = jobService;
		this.processor = processor;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("phase02-foreign-words-worker")
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
				String id = jobService.words4Phase02JobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null || job.getStatus() == JobStatus.ERROR) {
					continue;
				}
				try {
					job.setPhase(JobPhase.WORDS4_PHASE02);
					List<PageText> pages = job.getPages().stream()
							.sorted(Comparator.comparingInt(PageText::page))
							.toList();
					String language = JobWords4.parseDefaultLanguage(job);
					List<Words4TranscriptionItem> transcriptions = processor.detectForeignWords(pages, language);
					job.setWords4Transcriptions(transcriptions);
					job.setPhase(JobPhase.QUEUED_WORDS4_PHASE03);
					jobService.words4Phase03JobIds.put(id);
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
}

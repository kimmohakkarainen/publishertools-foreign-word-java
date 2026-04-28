package fi.publishertools.foreign.jobs.phase03crosspage;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.Job;
import fi.publishertools.foreign.jobs.JobPhase;
import fi.publishertools.foreign.jobs.JobService;
import fi.publishertools.foreign.jobs.JobStatus;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase03CrossPageWorker {

	private final JobService jobService;
	private final Phase03CrossPageProcessor processor = new Phase03CrossPageProcessor();
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public Phase03CrossPageWorker(JobService jobService) {
		this.jobService = jobService;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("phase03-crosspage-worker")
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
				String id = jobService.words4Phase03JobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null || job.getStatus() == JobStatus.ERROR) {
					continue;
				}
				try {
					job.setPhase(JobPhase.WORDS4_PHASE03);
					List<Words4TranscriptionItem> current = job.getWords4Transcriptions();
					List<Words4TranscriptionItem> merged = processor.mergeCrossPage(current);
					job.setWords4Transcriptions(merged);
					job.setPhase(JobPhase.QUEUED_WORDS4_PHASE04);
					jobService.words4Phase04JobIds.put(id);
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

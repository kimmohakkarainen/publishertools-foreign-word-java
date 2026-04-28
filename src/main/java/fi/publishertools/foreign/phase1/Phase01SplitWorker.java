package fi.publishertools.foreign.phase1;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.Job;
import fi.publishertools.foreign.jobs.JobPhase;
import fi.publishertools.foreign.jobs.JobService;
import fi.publishertools.foreign.jobs.JobStatus;
import fi.publishertools.foreign.jobs.PageText;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase01SplitWorker {

	private final JobService jobService;
	private final Phase01PageSplitter pageSplitter = new Phase01PageSplitter();
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public Phase01SplitWorker(JobService jobService) {
		this.jobService = jobService;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("phase01-split-worker")
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
				String id = jobService.phase01SplitJobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null) {
					continue;
				}
				try {
					job.setPhase(JobPhase.PHASE01_SPLITTING);
					List<PageText> pages = pageSplitter.splitIntoPages(job.getContent());
					job.setPages(pages);
					job.setPhase(JobPhase.QUEUED_LEGACY_POST_SPLIT);
					jobService.legacyPostSplitJobIds.put(id);
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

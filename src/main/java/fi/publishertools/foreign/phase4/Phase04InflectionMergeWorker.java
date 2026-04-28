package fi.publishertools.foreign.phase4;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.Job;
import fi.publishertools.foreign.jobs.JobPhase;
import fi.publishertools.foreign.jobs.JobService;
import fi.publishertools.foreign.jobs.JobStatus;
import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase04InflectionMergeWorker {

	private final JobService jobService;
	private final Phase04InflectionMergeProcessor processor;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public Phase04InflectionMergeWorker(JobService jobService, Phase04InflectionMergeProcessor processor) {
		this.jobService = jobService;
		this.processor = processor;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("phase04-inflection-merge-worker")
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
				String id = jobService.words4Phase04JobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null || job.getStatus() == JobStatus.ERROR) {
					continue;
				}
				try {
					job.setPhase(JobPhase.WORDS4_PHASE04);
					List<Words4PhaseItem> current = job.getWords4PhaseItems();
					List<Words4PhaseItem> merged = processor.mergeInflections(current);
					job.setWords4PhaseItems(merged);
					job.setPhase(JobPhase.QUEUED_WORDS4_PHASE05);
					jobService.words4Phase05JobIds.put(id);
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

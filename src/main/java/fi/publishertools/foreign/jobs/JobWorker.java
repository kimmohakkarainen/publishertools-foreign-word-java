package fi.publishertools.foreign.jobs;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Single daemon thread that drains the job id queue and updates job state.
 */
@Component
public class JobWorker {

	private final JobService jobService;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public JobWorker(JobService jobService) {
		this.jobService = jobService;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("job-worker")
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
				String id = jobService.jobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null) {
					continue;
				}
				try {
					String result = process(job);
					job.setResult(result);
					job.setStatus(JobStatus.FINISHED);
				} catch (Exception e) {
					String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
					job.setErrorMessage(msg);
					job.setStatus(JobStatus.ERROR);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Placeholder: line count and short text preview. Replace with real processing later.
	 */
	String process(Job job) {
		String text = new String(job.getContent(), StandardCharsets.UTF_8);
		int lines = 0;
		if (!text.isEmpty()) {
			lines = 1;
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == '\n') {
					lines++;
				}
			}
		}
		int previewLength = Math.min(200, text.length());
		String preview = text.substring(0, previewLength).replaceAll("\\R", " ");
		return "Processed " + lines + " lines from " + job.getOriginalFilename() + ". Preview: " + preview;
	}
}

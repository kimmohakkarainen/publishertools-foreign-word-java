package fi.publishertools.foreign.phase5;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.jobs.Job;
import fi.publishertools.foreign.jobs.JobPhase;
import fi.publishertools.foreign.jobs.JobService;
import fi.publishertools.foreign.jobs.JobStatus;
import fi.publishertools.foreign.jobs.dto.Words4FinishedPayload;
import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Phase05IpaWorker {

	private final JobService jobService;
	private final ObjectMapper objectMapper;
	private final Phase05IpaProcessor processor = new Phase05IpaProcessor();
	private final AtomicBoolean running = new AtomicBoolean(true);
	private Thread workerThread;

	public Phase05IpaWorker(JobService jobService, ObjectMapper objectMapper) {
		this.jobService = jobService;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void start() {
		workerThread = Thread.ofPlatform()
				.daemon()
				.name("phase05-ipa-worker")
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
				String id = jobService.words4Phase05JobIds.take();
				Job job = jobService.jobs.get(id);
				if (job == null || job.getStatus() == JobStatus.ERROR) {
					continue;
				}
				try {
					job.setPhase(JobPhase.WORDS4_PHASE05);
					List<Words4PhaseItem> withIpa = processor.addIpa(job.getWords4PhaseItems());
					List<Words4TranscriptionItem> transcriptions = withIpa.stream()
							.map(Words4PhaseItem::toTranscriptionItem)
							.toList();
					String json = objectMapper.writeValueAsString(new Words4FinishedPayload(transcriptions));
					job.setWords4PhaseItems(List.of());
					job.setResult(json);
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
		if (e instanceof JsonProcessingException) {
			msg = "Failed to serialize words4 result: " + msg;
		}
		job.setErrorMessage(msg);
		job.setStatus(JobStatus.ERROR);
		job.setPhase(JobPhase.FAILED);
	}
}

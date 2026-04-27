package fi.publishertools.foreign.jobs;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * In-memory job registry and queues for multi-phase processing.
 */
@Service
public class JobService {

	/** For {@link JobWorker} only. */
	final Map<String, Job> jobs = new ConcurrentHashMap<>();

	/** For {@link JobWorker} phase 1 only. */
	final BlockingQueue<String> phaseOneJobIds = new LinkedBlockingQueue<>();

	/** For {@link JobWorker} phase 2 only. */
	final BlockingQueue<String> phaseTwoJobIds = new LinkedBlockingQueue<>();

	public String submit(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is required and must not be empty");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.toLowerCase().startsWith("text/")) {
			throw new IllegalArgumentException("File must be a text file (Content-Type text/*)");
		}
		byte[] bytes = file.getBytes();
		if (bytes.length == 0) {
			throw new IllegalArgumentException("File must not be empty");
		}
		String id = UUID.randomUUID().toString();
		String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.txt";
		Job job = new Job(id, name, Instant.now(), bytes);
		job.setStatus(JobStatus.IN_PROGRESS);
		job.setPhase(JobPhase.QUEUED_FOR_SPLITTING);
		jobs.put(id, job);
		phaseOneJobIds.offer(id);
		return id;
	}

	public Optional<Job> find(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(jobs.get(id));
	}
}

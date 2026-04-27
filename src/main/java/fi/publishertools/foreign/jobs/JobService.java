package fi.publishertools.foreign.jobs;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
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
		job.setDescription("defaultLanguage=en");
		jobs.put(id, job);
		phaseOneJobIds.offer(id);
		return id;
	}

	public String submitPages(List<PageText> pages) {
		return submitPages(null, "en", pages);
	}

	public String submitPages(String requestedId, String defaultLanguage, List<PageText> pages) {
		if (pages == null || pages.isEmpty()) {
			throw new IllegalArgumentException("Pages are required and must not be empty");
		}
		if (pages.stream().anyMatch(page -> page == null || page.text() == null || page.page() <= 0)) {
			throw new IllegalArgumentException("Pages must include positive page and non-null text");
		}
		String id = (requestedId == null || requestedId.isBlank()) ? UUID.randomUUID().toString() : requestedId;
		if (jobs.containsKey(id)) {
			throw new IllegalArgumentException("Job id already exists: " + id);
		}
		String language = (defaultLanguage == null || defaultLanguage.isBlank()) ? "en" : defaultLanguage;
		Job job = new Job(id, "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.IN_PROGRESS);
		job.setPhase(JobPhase.QUEUED_FOR_PROCESSING);
		job.setDescription("defaultLanguage=" + language);
		job.setPages(pages);
		jobs.put(id, job);
		phaseTwoJobIds.offer(id);
		return id;
	}

	public Optional<Job> find(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(jobs.get(id));
	}
}

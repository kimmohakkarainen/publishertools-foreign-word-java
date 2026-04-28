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
 * <p>
 * To run multiple workers on one stage, start additional threads that call {@link java.util.concurrent.BlockingQueue#take()}
 * on the same
 * queue instance (same pattern as the single daemon worker per queue today).
 */
@Service
public class JobService {

	/** Workers in phase packages poll job ids and resolve {@link #jobs}. */
	public final Map<String, Job> jobs = new ConcurrentHashMap<>();

	public final BlockingQueue<String> phase01SplitJobIds = new LinkedBlockingQueue<>();

	public final BlockingQueue<String> legacyPostSplitJobIds = new LinkedBlockingQueue<>();

	public final BlockingQueue<String> words4Phase02JobIds = new LinkedBlockingQueue<>();

	public final BlockingQueue<String> words4Phase03JobIds = new LinkedBlockingQueue<>();

	public final BlockingQueue<String> words4Phase04JobIds = new LinkedBlockingQueue<>();

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
		job.setPhase(JobPhase.QUEUED_PHASE01_SPLIT);
		job.setDescription("defaultLanguage=en");
		jobs.put(id, job);
		phase01SplitJobIds.offer(id);
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
		job.setPhase(JobPhase.QUEUED_WORDS4_PHASE02);
		job.setDescription("defaultLanguage=" + language);
		job.setPages(pages);
		jobs.put(id, job);
		words4Phase02JobIds.offer(id);
		return id;
	}

	public Optional<Job> find(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(jobs.get(id));
	}
}

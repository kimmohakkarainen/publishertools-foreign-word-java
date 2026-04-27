package fi.publishertools.foreign.jobs;

import java.time.Instant;

/**
 * In-memory job: uploaded file bytes and processing outcome.
 */
public class Job {

	private final String id;
	private final String originalFilename;
	private final Instant submittedAt;
	private final byte[] content;

	private volatile JobStatus status;
	private volatile String result;
	private volatile String errorMessage;

	public Job(String id, String originalFilename, Instant submittedAt, byte[] content) {
		this.id = id;
		this.originalFilename = originalFilename;
		this.submittedAt = submittedAt;
		this.content = content;
	}

	public String getId() {
		return id;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public byte[] getContent() {
		return content;
	}

	public JobStatus getStatus() {
		return status;
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}

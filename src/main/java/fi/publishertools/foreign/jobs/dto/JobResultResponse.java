package fi.publishertools.foreign.jobs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import fi.publishertools.foreign.jobs.JobStatus;

/**
 * For FINISHED: result is set. For ERROR: errorMessage is set. status mirrors job state
 * for clients that use a single response type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResultResponse(String jobId, JobStatus status, String result, String errorMessage) {
	public static JobResultResponse success(String jobId, String result) {
		return new JobResultResponse(jobId, JobStatus.FINISHED, result, null);
	}

	public static JobResultResponse failure(String jobId, String errorMessage) {
		return new JobResultResponse(jobId, JobStatus.ERROR, null, errorMessage);
	}
}

package fi.publishertools.foreign.jobs.dto;

import fi.publishertools.foreign.jobs.JobStatus;

public record JobStatusResponse(String jobId, JobStatus status) {
}

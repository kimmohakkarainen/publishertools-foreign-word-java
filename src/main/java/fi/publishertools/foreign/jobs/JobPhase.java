package fi.publishertools.foreign.jobs;

public enum JobPhase {
	QUEUED_FOR_SPLITTING,
	SPLITTING,
	QUEUED_FOR_PROCESSING,
	PROCESSING,
	COMPLETED,
	FAILED
}

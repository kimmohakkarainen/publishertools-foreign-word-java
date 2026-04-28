package fi.publishertools.foreign.jobs.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Words4ResourceResponse(String status, List<Words4TranscriptionItem> transcriptions) {

	public static Words4ResourceResponse inProgress() {
		return new Words4ResourceResponse("IN_PROGRESS", null);
	}

	public static Words4ResourceResponse failed() {
		return new Words4ResourceResponse("FAILED", null);
	}

	public static Words4ResourceResponse finished(List<Words4TranscriptionItem> transcriptions) {
		return new Words4ResourceResponse(null, transcriptions);
	}
}

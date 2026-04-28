package fi.publishertools.foreign.jobs;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.jobs.dto.JobSubmissionResponse;
import fi.publishertools.foreign.jobs.dto.Words4FinishedPayload;
import fi.publishertools.foreign.jobs.dto.Words4ResourceResponse;
import fi.publishertools.foreign.jobs.dto.Words4TranscriptionItem;
import fi.publishertools.foreign.jobs.dto.WordsSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Words4", description = "Direct page processing API")
@RestController
public class Words4Controller {

	private static final String WORDS4_JOB_NAME = "words4-submit";
	private static final Logger log = LoggerFactory.getLogger(Words4Controller.class);

	private final JobService jobService;
	private final ObjectMapper objectMapper;

	public Words4Controller(JobService jobService, ObjectMapper objectMapper) {
		this.jobService = jobService;
		this.objectMapper = objectMapper;
	}

	@Operation(summary = "Submit pre-split pages directly to phase-2 processing")
	@PostMapping(path = "/words4/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JobSubmissionResponse> submit(
			@RequestParam(name = "id", required = false) String id,
			@RequestParam(name = "defaultLanguage", defaultValue = "en") String defaultLanguage,
			@RequestBody WordsSubmitRequest request) {
		int pageCount = request != null && request.text() != null ? request.text().size() : 0;
		log.info("REST /words4/submit called id={} defaultLanguage={} pageCount={}", id, defaultLanguage, pageCount);
		try {
			if (request == null || request.text() == null) {
				throw new IllegalArgumentException("Request body with text list is required");
			}
			if (request.text().stream().anyMatch(item -> item == null)) {
				throw new IllegalArgumentException("Request text list must not contain null items");
			}
			List<PageText> pages = request.text().stream()
					.map(item -> new PageText(item.page() != null ? item.page() : 0, item.text()))
					.toList();
			String submittedId = jobService.submitPages(id, defaultLanguage, pages);
			log.info("REST /words4/submit accepted jobId={} pageCount={}", submittedId, pages.size());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(new JobSubmissionResponse(submittedId, JobStatus.IN_PROGRESS));
		} catch (IllegalArgumentException e) {
			log.info("REST /words4/submit rejected id={} reason={}", id, e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@Operation(summary = "Get words4 job status")
	@GetMapping(path = "/words4/jobs/{id}/status", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> status(@PathVariable String id) {
		log.info("REST /words4/jobs/{}/status called", id);
		Job job = jobService.find(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
		String status = switch (job.getStatus()) {
			case IN_PROGRESS -> "IN_PROGRESS";
			case FINISHED -> "FINISHED";
			case ERROR -> "FAILED";
		};
		return ResponseEntity.ok(status);
	}

	@Operation(summary = "Get words4 job resource (status or transcriptions JSON)")
	@GetMapping(path = "/words4/{id}/resource", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Words4ResourceResponse> resource(@PathVariable String id) {
		log.info("REST /words4/{}/resource called", id);
		Job job = jobService.find(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
		if (!WORDS4_JOB_NAME.equals(job.getOriginalFilename())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
		}
		return switch (job.getStatus()) {
			case IN_PROGRESS -> ResponseEntity.ok(Words4ResourceResponse.inProgress());
			case ERROR -> ResponseEntity.ok(Words4ResourceResponse.failed());
			case FINISHED -> ResponseEntity.ok(Words4ResourceResponse.finished(parseFinishedTranscriptions(job.getResult())));
		};
	}

	private List<Words4TranscriptionItem> parseFinishedTranscriptions(String result) {
		if (result == null || result.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(result, Words4FinishedPayload.class).transcriptions();
		} catch (JsonProcessingException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid job result", e);
		}
	}
}

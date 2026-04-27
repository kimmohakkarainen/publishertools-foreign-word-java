package fi.publishertools.foreign.jobs;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import fi.publishertools.foreign.jobs.dto.JobSubmissionResponse;
import fi.publishertools.foreign.jobs.dto.WordsSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Words4", description = "Direct page processing API")
@RestController
public class Words4Controller {

	private final JobService jobService;

	public Words4Controller(JobService jobService) {
		this.jobService = jobService;
	}

	@Operation(summary = "Submit pre-split pages directly to phase-2 processing")
	@PostMapping(path = "/words4/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JobSubmissionResponse> submit(@RequestBody WordsSubmitRequest request) {
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
			String id = jobService.submitPages(pages);
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(new JobSubmissionResponse(id, JobStatus.IN_PROGRESS));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}
}

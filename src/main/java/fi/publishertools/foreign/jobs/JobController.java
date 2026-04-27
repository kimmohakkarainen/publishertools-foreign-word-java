package fi.publishertools.foreign.jobs;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import fi.publishertools.foreign.jobs.dto.JobResultResponse;
import fi.publishertools.foreign.jobs.dto.JobStatusResponse;
import fi.publishertools.foreign.jobs.dto.JobSubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Jobs", description = "Asynchronous text file processing")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@Operation(summary = "Upload a text file and enqueue for processing")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<JobSubmissionResponse> submit(@RequestPart("file") MultipartFile file) {
		try {
			String id = jobService.submit(file);
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(new JobSubmissionResponse(id, JobStatus.IN_PROGRESS));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file: " + e.getMessage(), e);
		}
	}

	@Operation(summary = "Get processing status for a job")
	@GetMapping("/{id}/status")
	public JobStatusResponse status(@PathVariable String id) {
		Job job = jobService.find(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
		return new JobStatusResponse(job.getId(), job.getStatus());
	}

	@Operation(summary = "Get processing result or error message when done")
	@GetMapping("/{id}/result")
	public ResponseEntity<JobResultResponse> result(@PathVariable String id) {
		Job job = jobService.find(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
		return switch (job.getStatus()) {
			case IN_PROGRESS -> ResponseEntity.status(HttpStatus.CONFLICT).build();
			case FINISHED -> ResponseEntity.ok(JobResultResponse.success(job.getId(), job.getResult()));
			case ERROR -> ResponseEntity.ok(JobResultResponse.failure(job.getId(), job.getErrorMessage()));
		};
	}
}

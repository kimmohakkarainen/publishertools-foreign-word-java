package fi.publishertools.foreign.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

	@Mock
	private JobService jobService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = standaloneSetup(new JobController(jobService)).build();
	}

	@Test
	void postUploadReturnsCreated() throws Exception {
		when(jobService.submit(any(MultipartFile.class))).thenReturn("job-1");
		MockMultipartFile file = new MockMultipartFile("file", "t.txt", "text/plain", "hello".getBytes());
		mockMvc.perform(multipart("/api/v1/jobs").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.jobId").value("job-1"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void getStatusFound() throws Exception {
		Job j = new Job("x", "f.txt", Instant.parse("2020-01-01T00:00:00Z"), "a".getBytes());
		j.setStatus(JobStatus.IN_PROGRESS);
		when(jobService.find("x")).thenReturn(Optional.of(j));
		mockMvc.perform(get("/api/v1/jobs/x/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value("x"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void getStatusNotFound() throws Exception {
		when(jobService.find("missing")).thenReturn(Optional.empty());
		mockMvc.perform(get("/api/v1/jobs/missing/status"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getResultWhenInProgressReturns409() throws Exception {
		Job j = new Job("x", "f.txt", Instant.now(), "a".getBytes());
		j.setStatus(JobStatus.IN_PROGRESS);
		when(jobService.find("x")).thenReturn(Optional.of(j));
		mockMvc.perform(get("/api/v1/jobs/x/result"))
				.andExpect(status().isConflict());
	}
}

package fi.publishertools.foreign.jobs;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class Words4ControllerTest {

	@Mock
	private JobService jobService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = standaloneSetup(new Words4Controller(jobService, new ObjectMapper())).build();
	}

	@Test
	void postSubmitReturnsCreated() throws Exception {
		when(jobService.submitPages(eq("job-words-1"), eq("fi"), anyList())).thenReturn("job-words-1");
		mockMvc.perform(post("/words4/submit")
				.queryParam("id", "job-words-1")
				.queryParam("defaultLanguage", "fi")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":[{\"page\":1,\"text\":\"first page\"},{\"page\":2,\"text\":\"second page\"}]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.jobId").value("job-words-1"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void postSubmitBadPayloadReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/words4/submit")
				.queryParam("id", "job-words-1")
				.queryParam("defaultLanguage", "fi")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":null}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getStatusReturns404WhenMissing() throws Exception {
		when(jobService.find("missing")).thenReturn(Optional.empty());
		mockMvc.perform(get("/words4/jobs/missing/status"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getStatusReturnsInProgress() throws Exception {
		Job job = new Job("j1", "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.IN_PROGRESS);
		when(jobService.find("j1")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/jobs/j1/status"))
				.andExpect(status().isOk())
				.andExpect(content().string("IN_PROGRESS"));
	}

	@Test
	void getStatusReturnsFinished() throws Exception {
		Job job = new Job("j2", "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.FINISHED);
		when(jobService.find("j2")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/jobs/j2/status"))
				.andExpect(status().isOk())
				.andExpect(content().string("FINISHED"));
	}

	@Test
	void getStatusReturnsFailedForError() throws Exception {
		Job job = new Job("j3", "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.ERROR);
		when(jobService.find("j3")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/jobs/j3/status"))
				.andExpect(status().isOk())
				.andExpect(content().string("FAILED"));
	}

	@Test
	void getResourceReturnsFinishedTranscriptions() throws Exception {
		Job job = new Job("j4", "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.FINISHED);
		job.setResult(
				"{\"transcriptions\":[{\"inflections\":[],\"ipa\":\"hello\",\"language\":\"en\",\"pages\":[1],\"rawIpa\":\"həˈloʊ\",\"word\":\"Hello\"}]}");
		when(jobService.find("j4")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/j4/resource"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.transcriptions").isArray())
				.andExpect(jsonPath("$.transcriptions[0].word").value("Hello"))
				.andExpect(jsonPath("$.transcriptions[0].language").value("en"))
				.andExpect(jsonPath("$.transcriptions[0].pages[0]").value(1));
	}

	@Test
	void getResourceReturnsInProgress() throws Exception {
		Job job = new Job("j5", "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.IN_PROGRESS);
		when(jobService.find("j5")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/j5/resource"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void getResourceReturnsFailedForError() throws Exception {
		Job job = new Job("j6", "words4-submit", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.ERROR);
		when(jobService.find("j6")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/j6/resource"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void getResourceReturns404ForNonWords4Job() throws Exception {
		Job job = new Job("j7", "upload.txt", Instant.now(), new byte[0]);
		job.setStatus(JobStatus.FINISHED);
		when(jobService.find("j7")).thenReturn(Optional.of(job));
		mockMvc.perform(get("/words4/j7/resource"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getResourceReturns404WhenMissing() throws Exception {
		when(jobService.find("missing")).thenReturn(Optional.empty());
		mockMvc.perform(get("/words4/missing/resource"))
				.andExpect(status().isNotFound());
	}
}

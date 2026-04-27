package fi.publishertools.foreign.jobs;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class Words4ControllerTest {

	@Mock
	private JobService jobService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = standaloneSetup(new Words4Controller(jobService)).build();
	}

	@Test
	void postSubmitReturnsCreated() throws Exception {
		when(jobService.submitPages(anyList())).thenReturn("job-words-1");
		mockMvc.perform(post("/words4/submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":[{\"page\":1,\"text\":\"first page\"},{\"page\":2,\"text\":\"second page\"}]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.jobId").value("job-words-1"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void postSubmitBadPayloadReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/words4/submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":null}"))
				.andExpect(status().isBadRequest());
	}
}

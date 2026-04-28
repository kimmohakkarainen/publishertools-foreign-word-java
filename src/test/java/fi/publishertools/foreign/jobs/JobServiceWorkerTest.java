package fi.publishertools.foreign.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.phase1.Phase01SplitWorker;
import fi.publishertools.foreign.phase2.DetectedForeignWord;
import fi.publishertools.foreign.phase2.ForeignWordDetectionClient;
import fi.publishertools.foreign.phase2.Phase02ForeignWordsProcessor;
import fi.publishertools.foreign.phase2.Phase02ForeignWordsWorker;
import fi.publishertools.foreign.phase3.Phase03CrossPageWorker;
import fi.publishertools.foreign.phase4.Phase04IpaWorker;

@SpringBootTest(
		classes = JobServiceWorkerTest.WorkerTestContext.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JobServiceWorkerTest {

	@Autowired
	private JobService jobService;

	@Autowired
	private ObjectMapper objectMapper;

	@Configuration
	static class WorkerTestContext {

		@Bean
		JobService jobService() {
			return new JobService();
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

		@Bean
		Phase01SplitWorker phase01SplitWorker(JobService service) {
			return new Phase01SplitWorker(service);
		}

		@Bean
		LegacyPostSplitWorker legacyPostSplitWorker(JobService service) {
			return new LegacyPostSplitWorker(service);
		}

		@Bean
		ForeignWordDetectionClient foreignWordDetectionClient() {
			return text -> {
				if (text == null || text.isBlank()) {
					return List.of();
				}
				String firstToken = text.trim().split("\\s+")[0];
				return List.of(new DetectedForeignWord(firstToken, "fi"));
			};
		}

		@Bean
		Phase02ForeignWordsProcessor phase02ForeignWordsProcessor(ForeignWordDetectionClient client) {
			return new Phase02ForeignWordsProcessor(client);
		}

		@Bean
		Phase02ForeignWordsWorker phase02ForeignWordsWorker(JobService service,
				Phase02ForeignWordsProcessor processor) {
			return new Phase02ForeignWordsWorker(service, processor);
		}

		@Bean
		Phase03CrossPageWorker phase03CrossPageWorker(JobService service) {
			return new Phase03CrossPageWorker(service);
		}

		@Bean
		Phase04IpaWorker phase04IpaWorker(JobService service, ObjectMapper objectMapper) {
			return new Phase04IpaWorker(service, objectMapper);
		}
	}

	@Test
	void submitEventuallyFinishesAfterSplitAndLegacyPhase() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "a\nb\nc".getBytes());
		String id = jobService.submit(file);
		assertThat(id).isNotBlank();

		Job finished = awaitFinishedJob(id);
		assertThat(finished.getPhase()).isEqualTo(JobPhase.COMPLETED);
		assertThat(finished.getResult()).contains("Processed 1 pages");
		assertThat(finished.getPages()).hasSize(1);
	}

	@Test
	void submitPagesDirectlyEnqueuesWords4PhasesAndFinishes() throws Exception {
		// Single token per page so phase-2 picks are distinct and phase-3 does not merge rows.
		String id = jobService.submitPages("job-words-1", "fi", List.of(
				new PageText(1, "aaa"),
				new PageText(2, "bbb")));
		assertThat(id).isEqualTo("job-words-1");

		Job finished = awaitFinishedJob(id);
		assertThat(finished.getPhase()).isEqualTo(JobPhase.COMPLETED);
		assertThat(finished.getPages()).hasSize(2);
		JsonNode root = objectMapper.readTree(finished.getResult());
		assertThat(root.get("transcriptions").size()).isEqualTo(2);
		for (int i = 0; i < 2; i++) {
			JsonNode t = root.get("transcriptions").get(i);
			assertThat(t.get("language").asText()).isEqualTo("fi");
			assertThat(t.get("word").asText()).isNotBlank();
			assertThat(t.get("ipa").asText()).isEqualTo(t.get("word").asText());
			assertThat(t.get("rawIpa").asText()).isEqualTo(t.get("word").asText());
			assertThat(t.get("pages").isArray()).isTrue();
		}
	}

	@Test
	void splitterEndsPageAtFirstPunctuationAfterHundredWords() throws Exception {
		String firstHundredWords = IntStream.rangeClosed(1, 100)
				.mapToObj(i -> "w" + i)
				.collect(Collectors.joining(" "));
		String content = firstHundredWords + " tail words continue until stop! after split";
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", content.getBytes());

		Job finished = awaitFinishedJob(jobService.submit(file));
		assertThat(finished.getPages()).hasSize(2);
		assertThat(finished.getPages().get(0).text()).endsWith("stop!");
		assertThat(finished.getPages().get(1).text()).isEqualTo(" after split");
	}

	@Test
	void splitterStrictlyWaitsForDelayedPunctuation() throws Exception {
		String firstHundredWords = IntStream.rangeClosed(1, 100)
				.mapToObj(i -> "w" + i)
				.collect(Collectors.joining(" "));
		String delayedPunctuationWords = IntStream.rangeClosed(101, 170)
				.mapToObj(i -> "w" + i)
				.collect(Collectors.joining(" "));
		String content = firstHundredWords + " " + delayedPunctuationWords + " finally.";
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", content.getBytes());

		Job finished = awaitFinishedJob(jobService.submit(file));
		assertThat(finished.getPages()).hasSize(1);
		assertThat(finished.getPages().get(0).text()).endsWith("finally.");
	}

	@Test
	void splitterClosesLastPageAtEofWhenNoPunctuationAfterThreshold() throws Exception {
		String words = IntStream.rangeClosed(1, 130)
				.mapToObj(i -> "w" + i)
				.collect(Collectors.joining(" "));
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", words.getBytes());

		Job finished = awaitFinishedJob(jobService.submit(file));
		assertThat(finished.getPages()).hasSize(1);
		assertThat(finished.getPages().get(0).text()).endsWith("w130");
	}

	@Test
	void processingErrorMarksJobAsError() throws Exception {
		Job job = new Job("bad-job", "bad.txt", Instant.now(), null);
		job.setStatus(JobStatus.IN_PROGRESS);
		job.setPhase(JobPhase.QUEUED_PHASE01_SPLIT);
		jobService.jobs.put(job.getId(), job);
		jobService.phase01SplitJobIds.offer(job.getId());

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			Optional<Job> opt = jobService.find(job.getId());
			if (opt.isPresent() && opt.get().getStatus() == JobStatus.ERROR) {
				assertThat(opt.get().getPhase()).isEqualTo(JobPhase.FAILED);
				assertThat(opt.get().getErrorMessage()).isNotBlank();
				return;
			}
			Thread.sleep(20);
		}
		fail("Job did not reach ERROR within timeout");
	}

	private Job awaitFinishedJob(String id) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			Optional<Job> opt = jobService.find(id);
			if (opt.isPresent() && opt.get().getStatus() == JobStatus.FINISHED) {
				return opt.get();
			}
			if (opt.isPresent() && opt.get().getStatus() == JobStatus.ERROR) {
				fail("Job unexpectedly failed: " + opt.get().getErrorMessage());
			}
			Thread.sleep(20);
		}
		fail("Job did not reach FINISHED within timeout");
		return null;
	}
}

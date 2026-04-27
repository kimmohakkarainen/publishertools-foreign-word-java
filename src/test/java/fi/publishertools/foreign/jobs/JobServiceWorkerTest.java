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

@SpringBootTest(
		classes = JobServiceWorkerTest.WorkerTestContext.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JobServiceWorkerTest {

	@Autowired
	private JobService jobService;

	@Configuration
	static class WorkerTestContext {

		@Bean
		JobService jobService() {
			return new JobService();
		}

		@Bean
		JobWorker jobWorker(JobService service) {
			return new JobWorker(service);
		}
	}

	@Test
	void submitEventuallyFinishesAfterTwoPhases() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "a\nb\nc".getBytes());
		String id = jobService.submit(file);
		assertThat(id).isNotBlank();

		Job finished = awaitFinishedJob(id);
		assertThat(finished.getPhase()).isEqualTo(JobPhase.COMPLETED);
		assertThat(finished.getResult()).contains("Processed 1 pages");
		assertThat(finished.getPages()).hasSize(1);
	}

	@Test
	void submitPagesDirectlyEnqueuesToPhaseTwoAndFinishes() throws Exception {
		String id = jobService.submitPages(List.of(
				new PageText(1, "first page text"),
				new PageText(2, "second page text")));
		assertThat(id).isNotBlank();

		Job finished = awaitFinishedJob(id);
		assertThat(finished.getPhase()).isEqualTo(JobPhase.COMPLETED);
		assertThat(finished.getPages()).hasSize(2);
		assertThat(finished.getResult()).contains("Processed 2 pages");
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
		job.setPhase(JobPhase.QUEUED_FOR_SPLITTING);
		jobService.jobs.put(job.getId(), job);
		jobService.phaseOneJobIds.offer(job.getId());

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

package fi.publishertools.foreign.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
	void submitEventuallyFinishesWithLineCount() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "a\nb\nc".getBytes());
		String id = jobService.submit(file);
		assertThat(id).isNotBlank();

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			Optional<Job> opt = jobService.find(id);
			if (opt.isPresent() && opt.get().getStatus() == JobStatus.FINISHED) {
				String r = opt.get().getResult();
				assertThat(r).contains("Processed");
				assertThat(r).contains("3");
				return;
			}
			Thread.sleep(20);
		}
		fail("Job did not reach FINISHED within timeout");
	}
}

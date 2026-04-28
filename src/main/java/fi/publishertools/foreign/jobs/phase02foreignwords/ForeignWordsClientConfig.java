package fi.publishertools.foreign.jobs.phase02foreignwords;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wires the active {@link ForeignWordDetectionClient} bean based on
 * {@code foreign-words.provider}.
 * <p>
 * Exactly one client is created at startup; processors and tests depend only on
 * the {@link ForeignWordDetectionClient} interface.
 */
@Configuration
public class ForeignWordsClientConfig {

	@Bean
	public ForeignWordResponseParser foreignWordResponseParser(ObjectMapper objectMapper) {
		return new ForeignWordResponseParser(objectMapper);
	}

	@Bean
	@ConditionalOnProperty(name = "foreign-words.provider", havingValue = ForeignWordsProperties.PROVIDER_OLLAMA,
			matchIfMissing = true)
	public ForeignWordDetectionClient ollamaForeignWordDetectionClient(
			ForeignWordsProperties properties,
			ForeignWordResponseParser parser) {
		ForeignWordsProperties.Ollama cfg = properties.ollama();
		RestClient restClient = RestClient.builder()
				.baseUrl(cfg.baseUrl())
				.requestFactory(timeoutRequestFactory(cfg.requestTimeout()))
				.build();
		return new OllamaForeignWordDetectionClient(restClient, cfg.model(), parser);
	}

	@Bean
	@ConditionalOnProperty(name = "foreign-words.provider", havingValue = ForeignWordsProperties.PROVIDER_MS_FOUNDRY)
	public ForeignWordDetectionClient msFoundryForeignWordDetectionClient(
			ForeignWordsProperties properties,
			ForeignWordResponseParser parser) {
		ForeignWordsProperties.MsFoundry cfg = properties.msFoundry();
		String endpoint = cfg.endpoint();
		if (endpoint == null || endpoint.isBlank()) {
			throw new IllegalStateException(
					"foreign-words.ms-foundry.endpoint must be set when provider is 'ms-foundry'");
		}
		String apiKey = cfg.apiKey();
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException(
					"foreign-words.ms-foundry.api-key must be set when provider is 'ms-foundry'");
		}
		RestClient restClient = RestClient.builder()
				.baseUrl(stripTrailingSlash(endpoint))
				.defaultHeader("api-key", apiKey)
				.requestFactory(timeoutRequestFactory(cfg.requestTimeout()))
				.build();
		return new MsFoundryForeignWordDetectionClient(restClient, cfg.model(), parser);
	}

	private static SimpleClientHttpRequestFactory timeoutRequestFactory(Duration timeout) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		int millis = (int) Math.min(Integer.MAX_VALUE, Math.max(1_000L, timeout.toMillis()));
		factory.setConnectTimeout(millis);
		factory.setReadTimeout(millis);
		return factory;
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}

package fi.publishertools.foreign.phase4;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.phase2.ForeignWordsProperties;

@Configuration
public class InflectionMergeClientConfig {

	@Bean
	public InflectionMergeResponseParser inflectionMergeResponseParser(ObjectMapper objectMapper) {
		return new InflectionMergeResponseParser(objectMapper);
	}

	@Bean
	@ConditionalOnProperty(name = "foreign-words.provider", havingValue = ForeignWordsProperties.PROVIDER_OLLAMA,
			matchIfMissing = true)
	public InflectionMergeClient ollamaInflectionMergeClient(
			ForeignWordsProperties properties,
			InflectionMergeResponseParser parser) {
		ForeignWordsProperties.Ollama cfg = properties.ollama();
		String model = selectModel(properties.phase4().model(), cfg.model());
		RestClient restClient = RestClient.builder()
				.baseUrl(cfg.baseUrl())
				.requestFactory(timeoutRequestFactory(cfg.requestTimeout()))
				.build();
		return new OllamaInflectionMergeClient(restClient, model, parser);
	}

	@Bean
	@ConditionalOnProperty(name = "foreign-words.provider", havingValue = ForeignWordsProperties.PROVIDER_MS_FOUNDRY)
	public InflectionMergeClient msFoundryInflectionMergeClient(
			ForeignWordsProperties properties,
			InflectionMergeResponseParser parser) {
		ForeignWordsProperties.MsFoundry cfg = properties.msFoundry();
		String model = selectModel(properties.phase4().model(), cfg.model());
		String endpoint = cfg.endpoint();
		if (endpoint == null || endpoint.isBlank()) {
			throw new IllegalStateException("foreign-words.ms-foundry.endpoint must be set when provider is 'ms-foundry'");
		}
		String apiKey = cfg.apiKey();
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("foreign-words.ms-foundry.api-key must be set when provider is 'ms-foundry'");
		}
		RestClient restClient = RestClient.builder()
				.baseUrl(stripTrailingSlash(endpoint))
				.defaultHeader("api-key", apiKey)
				.requestFactory(timeoutRequestFactory(cfg.requestTimeout()))
				.build();
		return new MsFoundryInflectionMergeClient(restClient, model, parser);
	}

	private static String selectModel(String phaseModel, String providerModel) {
		return (phaseModel == null || phaseModel.isBlank()) ? providerModel : phaseModel;
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

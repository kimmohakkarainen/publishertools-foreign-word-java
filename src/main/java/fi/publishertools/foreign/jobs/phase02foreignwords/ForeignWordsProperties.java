package fi.publishertools.foreign.jobs.phase02foreignwords;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Phase02 foreign-word detection.
 * <p>
 * The active provider is chosen via {@link #provider()}; the matching nested
 * settings block is then required.
 */
@ConfigurationProperties("foreign-words")
public record ForeignWordsProperties(
		String provider,
		Ollama ollama,
		MsFoundry msFoundry) {

	public static final String PROVIDER_OLLAMA = "ollama";
	public static final String PROVIDER_MS_FOUNDRY = "ms-foundry";

	public ForeignWordsProperties {
		if (provider == null || provider.isBlank()) {
			provider = PROVIDER_OLLAMA;
		}
		if (ollama == null) {
			ollama = new Ollama(null, null, null);
		}
		if (msFoundry == null) {
			msFoundry = new MsFoundry(null, null, null, null);
		}
	}

	public record Ollama(
			String baseUrl,
			String model,
			Duration requestTimeout) {

		public Ollama {
			if (baseUrl == null || baseUrl.isBlank()) {
				baseUrl = "http://localhost:11434";
			}
			if (model == null || model.isBlank()) {
				model = "mistral";
			}
			if (requestTimeout == null) {
				requestTimeout = Duration.ofSeconds(120);
			}
		}
	}

	public record MsFoundry(
			String endpoint,
			String apiKey,
			String model,
			Duration requestTimeout) {

		public MsFoundry {
			if (model == null || model.isBlank()) {
				model = "mistral-2505";
			}
			if (requestTimeout == null) {
				requestTimeout = Duration.ofSeconds(120);
			}
		}
	}
}

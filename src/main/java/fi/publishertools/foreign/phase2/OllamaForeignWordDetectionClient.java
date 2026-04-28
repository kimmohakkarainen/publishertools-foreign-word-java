package fi.publishertools.foreign.phase2;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls a local Ollama server's {@code /api/chat} endpoint with {@code format=json}
 * to detect foreign words on a single page of text.
 */
public class OllamaForeignWordDetectionClient implements ForeignWordDetectionClient {

	private final RestClient restClient;
	private final String model;
	private final ForeignWordResponseParser parser;

	public OllamaForeignWordDetectionClient(
			RestClient restClient,
			String model,
			ForeignWordResponseParser parser) {
		this.restClient = restClient;
		this.model = model;
		this.parser = parser;
	}

	@Override
	public List<DetectedForeignWord> detect(String pageText) {
		if (pageText == null || pageText.isBlank()) {
			return List.of();
		}
		Map<String, Object> body = Map.of(
				"model", model,
				"stream", false,
				"format", "json",
				"messages", List.of(
						Map.of("role", "system", "content",
								ForeignWordDetectionPrompt.FOREIGN_WORD_DETECTION_PROMPT),
						Map.of("role", "user", "content", pageText)));
		Map<?, ?> response;
		try {
			response = restClient.post()
					.uri("/api/chat")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);
		} catch (RestClientException e) {
			throw new ForeignWordDetectionException(
					"Ollama chat request failed: " + e.getMessage(), e);
		}
		String content = extractContent(response);
		return parser.parse(content);
	}

	private static String extractContent(Map<?, ?> response) {
		if (response == null) {
			throw new ForeignWordDetectionException("Ollama returned an empty response body");
		}
		Object message = response.get("message");
		if (!(message instanceof Map<?, ?> msgMap)) {
			throw new ForeignWordDetectionException(
					"Ollama response missing 'message' object: " + response);
		}
		Object content = msgMap.get("content");
		return content == null ? "" : content.toString();
	}
}

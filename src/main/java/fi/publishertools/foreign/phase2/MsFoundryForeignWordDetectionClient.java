package fi.publishertools.foreign.phase2;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls a Microsoft AI Foundry "Models as a Service" endpoint at
 * {@code <endpoint>/chat/completions} (OpenAI-compatible) to detect foreign words
 * on a single page of text. Authentication uses the {@code api-key} header.
 */
public class MsFoundryForeignWordDetectionClient implements ForeignWordDetectionClient {

	private final RestClient restClient;
	private final String model;
	private final ForeignWordResponseParser parser;

	public MsFoundryForeignWordDetectionClient(
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
				"messages", List.of(
						Map.of("role", "system", "content",
								ForeignWordDetectionPrompt.FOREIGN_WORD_DETECTION_PROMPT),
						Map.of("role", "user", "content", pageText)));
		Map<?, ?> response;
		try {
			response = restClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);
		} catch (RestClientException e) {
			throw new ForeignWordDetectionException(
					"MS Foundry chat request failed: " + e.getMessage(), e);
		}
		String content = extractContent(response);
		return parser.parse(content);
	}

	private static String extractContent(Map<?, ?> response) {
		if (response == null) {
			throw new ForeignWordDetectionException("MS Foundry returned an empty response body");
		}
		Object choices = response.get("choices");
		if (!(choices instanceof List<?> choicesList) || choicesList.isEmpty()) {
			throw new ForeignWordDetectionException(
					"MS Foundry response missing 'choices' array: " + response);
		}
		Object first = choicesList.get(0);
		if (!(first instanceof Map<?, ?> firstChoice)) {
			throw new ForeignWordDetectionException(
					"MS Foundry response 'choices[0]' was not an object: " + first);
		}
		Object message = firstChoice.get("message");
		if (!(message instanceof Map<?, ?> msgMap)) {
			throw new ForeignWordDetectionException(
					"MS Foundry response 'choices[0].message' was not an object: " + firstChoice);
		}
		Object content = msgMap.get("content");
		return content == null ? "" : content.toString();
	}
}

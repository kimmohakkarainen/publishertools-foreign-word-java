package fi.publishertools.foreign.phase4;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.phase2.ForeignWordDetectionException;

public class OllamaInflectionMergeClient implements InflectionMergeClient {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final RestClient restClient;
	private final String model;
	private final InflectionMergeResponseParser parser;

	public OllamaInflectionMergeClient(RestClient restClient, String model, InflectionMergeResponseParser parser) {
		this.restClient = restClient;
		this.model = model;
		this.parser = parser;
	}

	@Override
	public List<MergedInflectionWord> detectInflections(List<String> words) {
		if (words == null || words.isEmpty()) {
			return List.of();
		}
		String userContent = asJson(words);
		Map<String, Object> body = Map.of(
				"model", model,
				"stream", false,
				"format", "json",
				"messages", List.of(
						Map.of("role", "system", "content", InflectionMergePrompt.INFLECTION_MERGE_PROMPT),
						Map.of("role", "user", "content", userContent)));
		Map<?, ?> response;
		try {
			response = restClient.post()
					.uri("/api/chat")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);
		} catch (RestClientException e) {
			throw new ForeignWordDetectionException("Ollama inflection chat request failed: " + e.getMessage(), e);
		}
		Object message = response == null ? null : response.get("message");
		if (!(message instanceof Map<?, ?> msgMap)) {
			throw new ForeignWordDetectionException("Ollama inflection response missing message object");
		}
		Object content = msgMap.get("content");
		return parser.parse(content == null ? "" : content.toString());
	}

	private static String asJson(List<String> words) {
		try {
			return OBJECT_MAPPER.writeValueAsString(words);
		} catch (JsonProcessingException e) {
			throw new ForeignWordDetectionException("Failed to serialize inflection words input as JSON", e);
		}
	}
}

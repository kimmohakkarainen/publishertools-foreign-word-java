package fi.publishertools.foreign.phase4;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.phase2.ForeignWordDetectionException;

public class MsFoundryInflectionMergeClient implements InflectionMergeClient {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final RestClient restClient;
	private final String model;
	private final InflectionMergeResponseParser parser;

	public MsFoundryInflectionMergeClient(RestClient restClient, String model, InflectionMergeResponseParser parser) {
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
				"messages", List.of(
						Map.of("role", "system", "content", InflectionMergePrompt.INFLECTION_MERGE_PROMPT),
						Map.of("role", "user", "content", userContent)),
				"max_tokens", 4000,
				"temperature", 0.0,
				"response_format", Map.of("type", "json_object"),
				"random_seed", 34088039);
		Map<?, ?> response;
		try {
			response = restClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);
		} catch (RestClientException e) {
			throw new ForeignWordDetectionException("MS Foundry inflection chat request failed: " + e.getMessage(), e);
		}
		Object choices = response == null ? null : response.get("choices");
		if (!(choices instanceof List<?> list) || list.isEmpty()) {
			throw new ForeignWordDetectionException("MS Foundry inflection response missing choices array");
		}
		Object first = list.get(0);
		if (!(first instanceof Map<?, ?> choice)) {
			throw new ForeignWordDetectionException("MS Foundry inflection choice entry was not an object");
		}
		Object message = choice.get("message");
		if (!(message instanceof Map<?, ?> msgMap)) {
			throw new ForeignWordDetectionException("MS Foundry inflection response missing message object");
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

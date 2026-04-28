package fi.publishertools.foreign.phase2;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extracts a list of {@link DetectedForeignWord} from raw LLM message content.
 * <p>
 * The prompt asks the model to return a bare JSON array of {@code {word, language}} objects.
 * In practice, models occasionally wrap that array in surrounding text or in a single-key
 * object. This parser tries the strict shape first and then falls back to lenient extraction.
 */
public class ForeignWordResponseParser {

	private static final TypeReference<List<DetectedForeignWord>> WORD_LIST_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public ForeignWordResponseParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<DetectedForeignWord> parse(String content) {
		if (content == null || content.isBlank()) {
			return List.of();
		}
		String trimmed = content.trim();
		try {
			return parseFromJsonNode(objectMapper.readTree(trimmed));
		} catch (Exception ignored) {
			// fall through to bracket-extraction
		}
		String arraySlice = extractFirstJsonArray(trimmed);
		if (arraySlice != null) {
			try {
				return objectMapper.readValue(arraySlice, WORD_LIST_TYPE);
			} catch (Exception e) {
				throw new ForeignWordDetectionException(
						"Failed to parse JSON array from LLM response: " + e.getMessage(), e);
			}
		}
		throw new ForeignWordDetectionException(
				"LLM response did not contain a JSON array of foreign words: " + truncate(trimmed, 200));
	}

	private List<DetectedForeignWord> parseFromJsonNode(JsonNode node) {
		if (node == null || node.isNull()) {
			return List.of();
		}
		if (node.isArray()) {
			return objectMapper.convertValue(node, WORD_LIST_TYPE);
		}
		if (node.isObject()) {
			if (node.hasNonNull("word")) {
				return List.of(objectMapper.convertValue(node, DetectedForeignWord.class));
			}
			for (JsonNode child : node) {
				if (child.isArray()) {
					return objectMapper.convertValue(child, WORD_LIST_TYPE);
				}
			}
		}
		throw new ForeignWordDetectionException("LLM response was not a JSON array or wrapper object");
	}

	private static String extractFirstJsonArray(String text) {
		int depth = 0;
		int start = -1;
		boolean inString = false;
		boolean escape = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (escape) {
				escape = false;
				continue;
			}
			if (c == '\\') {
				escape = true;
				continue;
			}
			if (c == '"') {
				inString = !inString;
				continue;
			}
			if (inString) {
				continue;
			}
			if (c == '[') {
				if (depth == 0) {
					start = i;
				}
				depth++;
			} else if (c == ']') {
				depth--;
				if (depth == 0 && start >= 0) {
					return text.substring(start, i + 1);
				}
			}
		}
		return null;
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}
}

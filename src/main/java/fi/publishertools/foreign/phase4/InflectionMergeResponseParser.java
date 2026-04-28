package fi.publishertools.foreign.phase4;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.phase2.ForeignWordDetectionException;

public class InflectionMergeResponseParser {

	private static final TypeReference<List<MergedInflectionWord>> TYPE = new TypeReference<>() {
	};
	private final ObjectMapper objectMapper;

	public InflectionMergeResponseParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<MergedInflectionWord> parse(String content) {
		if (content == null || content.isBlank()) {
			return List.of();
		}
		String trimmed = content.trim();
		try {
			return parseFromJsonNode(objectMapper.readTree(trimmed));
		} catch (Exception ignored) {
			// fall through to extraction
		}
		String arraySlice = extractFirstJsonArray(trimmed);
		if (arraySlice != null) {
			try {
				return objectMapper.readValue(arraySlice, TYPE);
			} catch (Exception e) {
				throw new ForeignWordDetectionException("Failed to parse inflection merge response: " + e.getMessage(), e);
			}
		}
		throw new ForeignWordDetectionException("Inflection merge response did not contain a JSON array");
	}

	private List<MergedInflectionWord> parseFromJsonNode(JsonNode node) {
		if (node == null || node.isNull()) {
			return List.of();
		}
		if (node.isArray()) {
			return objectMapper.convertValue(node, TYPE);
		}
		if (node.isObject()) {
			for (JsonNode child : node) {
				if (child.isArray()) {
					return objectMapper.convertValue(child, TYPE);
				}
			}
		}
		throw new ForeignWordDetectionException("Inflection merge response was not a JSON array");
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
}

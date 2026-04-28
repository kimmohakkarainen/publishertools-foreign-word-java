package fi.publishertools.foreign.phase2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One foreign-word entry returned by the LLM detection step.
 *
 * @param word     exact surface form
 * @param language ISO 639-1 code of the foreign word's language
 */
public record DetectedForeignWord(String word, String language) {

	@JsonCreator
	public static DetectedForeignWord create(
			@JsonProperty("word") String word,
			@JsonProperty("language") String language) {
		return new DetectedForeignWord(word, language);
	}
}

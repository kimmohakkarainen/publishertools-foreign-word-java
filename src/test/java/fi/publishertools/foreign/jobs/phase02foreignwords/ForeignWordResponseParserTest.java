package fi.publishertools.foreign.jobs.phase02foreignwords;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.publishertools.foreign.phase2.DetectedForeignWord;
import fi.publishertools.foreign.phase2.ForeignWordDetectionException;
import fi.publishertools.foreign.phase2.ForeignWordResponseParser;

class ForeignWordResponseParserTest {

	private final ForeignWordResponseParser parser = new ForeignWordResponseParser(new ObjectMapper());

	@Test
	void parsesBareJsonArray() {
		String content = "[{\"word\":\"Bordeaux'n\",\"language\":\"fr\"},"
				+ "{\"word\":\"Ljuset har försvunnit\",\"language\":\"sv\"}]";

		List<DetectedForeignWord> out = parser.parse(content);

		assertThat(out).containsExactly(
				new DetectedForeignWord("Bordeaux'n", "fr"),
				new DetectedForeignWord("Ljuset har försvunnit", "sv"));
	}

	@Test
	void parsesArrayWrappedInObject() {
		String content = "{\"words\":[{\"word\":\"hello\",\"language\":\"en\"}]}";

		List<DetectedForeignWord> out = parser.parse(content);

		assertThat(out).containsExactly(new DetectedForeignWord("hello", "en"));
	}

	@Test
	void parsesSingleObjectAsOneItemList() {
		String content = "{\"word\":\"bonjour\",\"language\":\"fr\"}";

		List<DetectedForeignWord> out = parser.parse(content);

		assertThat(out).containsExactly(new DetectedForeignWord("bonjour", "fr"));
	}

	@Test
	void parsesArrayEmbeddedInSurroundingText() {
		String content = "Sure! Here is the result:\n"
				+ "[{\"word\":\"foo\",\"language\":\"de\"}]\n"
				+ "Hope it helps.";

		List<DetectedForeignWord> out = parser.parse(content);

		assertThat(out).containsExactly(new DetectedForeignWord("foo", "de"));
	}

	@Test
	void blankContentReturnsEmptyList() {
		assertThat(parser.parse(null)).isEmpty();
		assertThat(parser.parse("")).isEmpty();
		assertThat(parser.parse("   ")).isEmpty();
	}

	@Test
	void emptyJsonArrayReturnsEmptyList() {
		assertThat(parser.parse("[]")).isEmpty();
	}

	@Test
	void unparseableContentThrows() {
		assertThatThrownBy(() -> parser.parse("not json at all"))
				.isInstanceOf(ForeignWordDetectionException.class);
	}
}

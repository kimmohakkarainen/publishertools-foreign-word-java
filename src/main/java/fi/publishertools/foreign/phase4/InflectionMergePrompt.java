package fi.publishertools.foreign.phase4;

public final class InflectionMergePrompt {

	private InflectionMergePrompt() {
	}

	public static final String INFLECTION_MERGE_PROMPT = """
			You are a helpful agent that detects and merges inflections in an array of words.
			You will receive an array of words, and your task is to identify and list any inflected
			forms of the same word under a single base form.
			The input words are expected to be in any language but the suffixes indicating inflection are in Finnish.
			You must not return any additional text or explanations.
			If a word has no inflections, return an empty list for that word.
			Example input: ["Johnin", "John", "Johnille", "Jacques"]
			Example output: [
			    {
			        "word": "John",
			        "inflections": ["Johnin", "Johnille"]
			    },
			    {
			        "word": "Jacques",
			        "inflections": []
			    }
			]
			""";
}

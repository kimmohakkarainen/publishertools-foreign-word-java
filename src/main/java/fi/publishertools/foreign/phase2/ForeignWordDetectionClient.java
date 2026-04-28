package fi.publishertools.foreign.phase2;

import java.util.List;

/**
 * Sends one page of text to a language model and returns the foreign words it detected.
 */
public interface ForeignWordDetectionClient {

	/**
	 * Detects foreign words in {@code pageText}.
	 *
	 * @param pageText the raw text content of a single page
	 * @return non-null list of detected words; empty when none are found
	 * @throws ForeignWordDetectionException when the underlying call or response cannot be processed
	 */
	List<DetectedForeignWord> detect(String pageText);
}

package fi.publishertools.foreign.jobs.phase02foreignwords;

/**
 * Thrown when a Phase02 detection client fails to call the LLM or parse its response.
 */
public class ForeignWordDetectionException extends RuntimeException {

	public ForeignWordDetectionException(String message) {
		super(message);
	}

	public ForeignWordDetectionException(String message, Throwable cause) {
		super(message, cause);
	}
}

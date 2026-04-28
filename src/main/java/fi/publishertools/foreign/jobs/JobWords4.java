package fi.publishertools.foreign.jobs;

/**
 * Shared Words4 job metadata helpers.
 */
public final class JobWords4 {

	private JobWords4() {
	}

	public static String parseDefaultLanguage(Job job) {
		String d = job.getDescription();
		if (d == null || !d.startsWith("defaultLanguage=")) {
			return "en";
		}
		return d.substring("defaultLanguage=".length());
	}
}

package fi.publishertools.foreign.phase1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import fi.publishertools.foreign.jobs.PageText;

/**
 * Splits a UTF-8 document string into {@link PageText} pages (same rules as historical JobWorker split).
 */
public class Phase01PageSplitter {

	public List<PageText> splitIntoPages(byte[] utf8Content) {
		if (utf8Content == null) {
			throw new IllegalArgumentException("Content must not be null");
		}
		String text = new String(utf8Content, StandardCharsets.UTF_8);
		List<PageText> pages = new ArrayList<>();
		if (text.isEmpty()) {
			return pages;
		}

		StringBuilder page = new StringBuilder();
		int wordsInPage = 0;
		boolean waitForPunctuation = false;
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (isWordChar(ch)) {
				int start = i;
				i++;
				while (i < text.length() && isWordChar(text.charAt(i))) {
					i++;
				}
				page.append(text, start, i);
				wordsInPage++;
				if (wordsInPage >= 100) {
					waitForPunctuation = true;
				}
				continue;
			}

			page.append(ch);
			if (waitForPunctuation && isPunctuation(ch)) {
				pages.add(new PageText(pages.size() + 1, page.toString()));
				page.setLength(0);
				wordsInPage = 0;
				waitForPunctuation = false;
			}
			i++;
		}

		if (page.length() > 0) {
			pages.add(new PageText(pages.size() + 1, page.toString()));
		}
		return pages;
	}

	private boolean isWordChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '\'';
	}

	private boolean isPunctuation(char ch) {
		return switch (Character.getType(ch)) {
			case Character.CONNECTOR_PUNCTUATION,
					Character.DASH_PUNCTUATION,
					Character.START_PUNCTUATION,
					Character.END_PUNCTUATION,
					Character.INITIAL_QUOTE_PUNCTUATION,
					Character.FINAL_QUOTE_PUNCTUATION,
					Character.OTHER_PUNCTUATION -> true;
			default -> false;
		};
	}
}

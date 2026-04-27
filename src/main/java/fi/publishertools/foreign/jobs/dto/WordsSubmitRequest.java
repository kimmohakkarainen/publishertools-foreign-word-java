package fi.publishertools.foreign.jobs.dto;

import java.util.List;

public record WordsSubmitRequest(List<WordsSubmitTextItem> text) {
}

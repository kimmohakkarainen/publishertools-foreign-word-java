package fi.publishertools.foreign.phase4;

import java.util.List;

public interface InflectionMergeClient {

	List<MergedInflectionWord> detectInflections(List<String> words);
}

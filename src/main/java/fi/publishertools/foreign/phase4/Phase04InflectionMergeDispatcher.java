package fi.publishertools.foreign.phase4;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Phase04InflectionMergeDispatcher {

	CompletableFuture<List<MergedInflectionWord>> submit(List<String> words);
}

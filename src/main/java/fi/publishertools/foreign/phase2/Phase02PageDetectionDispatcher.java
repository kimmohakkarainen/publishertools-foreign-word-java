package fi.publishertools.foreign.phase2;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Phase02PageDetectionDispatcher {

	CompletableFuture<List<DetectedForeignWord>> submit(String pageText);
}

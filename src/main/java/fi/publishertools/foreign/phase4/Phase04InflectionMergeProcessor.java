package fi.publishertools.foreign.phase4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.publishertools.foreign.jobs.dto.Words4PhaseItem;

@Component
public class Phase04InflectionMergeProcessor {

	private static final Logger log = LoggerFactory.getLogger(Phase04InflectionMergeProcessor.class);
	private final InflectionMergeClient client;

	public Phase04InflectionMergeProcessor(InflectionMergeClient client) {
		this.client = client;
	}

	public List<Words4PhaseItem> mergeInflections(List<Words4PhaseItem> items) {
		if (items == null || items.isEmpty()) {
			return List.of();
		}

		Map<String, List<Words4PhaseItem>> buckets = new LinkedHashMap<>();
		for (Words4PhaseItem item : items) {
			if (item == null || !item.hasWord()) {
				continue;
			}
			String key = firstThree(item.word());
			buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
		}

		List<Words4PhaseItem> result = new ArrayList<>();
		for (List<Words4PhaseItem> bucketItems : buckets.values()) {
			if (bucketItems.size() <= 1) {
				result.addAll(bucketItems);
				continue;
			}
			List<Words4PhaseItem> mergedBucket = tryMergeBucket(bucketItems);
			result.addAll(mergedBucket);
		}
		return result;
	}

	private List<Words4PhaseItem> tryMergeBucket(List<Words4PhaseItem> bucketItems) {
		List<String> words = bucketItems.stream()
				.map(Words4PhaseItem::word)
				.distinct()
				.toList();
		if (words.size() <= 1) {
			return bucketItems;
		}
		List<MergedInflectionWord> merged;
		String threadName = Thread.currentThread().getName();
		long startedAt = System.nanoTime();
		log.info(
				"Inflection LLM call started thread={} bucketWordCount={} attempt={}/{}",
				threadName,
				words.size(),
				1,
				1);
		try {
			merged = client.detectInflections(words);
			long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
			int resultCount = merged == null ? 0 : merged.size();
			log.info(
					"Inflection LLM call finished thread={} bucketWordCount={} resultCount={} elapsedMs={} attempt={}/{}",
					threadName,
					words.size(),
					resultCount,
					elapsedMs,
					1,
					1);
		} catch (Exception e) {
			long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
			log.info(
					"Inflection LLM call failed; using unmerged bucket thread={} bucketWordCount={} elapsedMs={} attempt={}/{} error={}",
					threadName,
					words.size(),
					elapsedMs,
					1,
					1,
					e.getMessage());
			return bucketItems;
		}
		if (merged == null || merged.isEmpty()) {
			return bucketItems;
		}
		Map<String, Words4PhaseItem> itemByLowerWord = new LinkedHashMap<>();
		for (Words4PhaseItem item : bucketItems) {
			itemByLowerWord.putIfAbsent(item.word().toLowerCase(Locale.ROOT), item);
		}

		Set<String> consumed = new LinkedHashSet<>();
		List<Words4PhaseItem> out = new ArrayList<>();
		for (MergedInflectionWord mergedWord : merged) {
			if (mergedWord == null || mergedWord.word() == null || mergedWord.word().isBlank()) {
				continue;
			}
			Set<String> forms = new LinkedHashSet<>();
			forms.add(mergedWord.word());
			if (mergedWord.inflections() != null) {
				forms.addAll(mergedWord.inflections());
			}
			List<Words4PhaseItem> matchedItems = matchItems(forms, itemByLowerWord);
			if (matchedItems.isEmpty()) {
				continue;
			}
			Words4PhaseItem base = chooseBaseItem(mergedWord.word(), matchedItems);
			List<String> inflections = matchedItems.stream()
					.map(Words4PhaseItem::word)
					.filter(w -> !w.equalsIgnoreCase(base.word()))
					.distinct()
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.toList();
			SortedSet<Integer> mergedPages = matchedItems.stream()
					.map(Words4PhaseItem::pages)
					.flatMap((Collection<Integer> pageList) -> pageList.stream())
					.collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
			List<Integer> pages = mergedPages.stream().toList();
			out.add(base.withWordInflectionsAndPages(base.word(), inflections, pages));
			for (Words4PhaseItem matchedItem : matchedItems) {
				consumed.add(matchedItem.word().toLowerCase(Locale.ROOT));
			}
		}

		for (Words4PhaseItem item : bucketItems) {
			String lower = item.word().toLowerCase(Locale.ROOT);
			if (!consumed.contains(lower)) {
				out.add(item);
			}
		}
		return out.stream()
				.sorted(Comparator.comparing(Words4PhaseItem::word, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private static List<Words4PhaseItem> matchItems(Set<String> forms, Map<String, Words4PhaseItem> itemByLowerWord) {
		List<Words4PhaseItem> matchedItems = new ArrayList<>();
		for (String form : forms) {
			if (form == null || form.isBlank()) {
				continue;
			}
			Words4PhaseItem item = itemByLowerWord.get(form.toLowerCase(Locale.ROOT));
			if (item != null) {
				matchedItems.add(item);
			}
		}
		return matchedItems;
	}

	private static Words4PhaseItem chooseBaseItem(String requestedBaseWord, List<Words4PhaseItem> matchedItems) {
		for (Words4PhaseItem item : matchedItems) {
			if (item.word().equalsIgnoreCase(requestedBaseWord)) {
				return item;
			}
		}
		return matchedItems.get(0);
	}

	private static String firstThree(String word) {
		String lowered = word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
		if (lowered.length() <= 3) {
			return lowered;
		}
		return lowered.substring(0, 3);
	}
}

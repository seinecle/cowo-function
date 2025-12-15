package net.clementlevallois.cowo.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.clementlevallois.stopwords.StopWordsRemover;
import net.clementlevallois.stopwords.Stopwords;
import net.clementlevallois.umigon.model.NGram;
import net.clementlevallois.umigon.model.SentenceLike;
import net.clementlevallois.umigon.model.TextFragment;
import net.clementlevallois.umigon.ngram.ops.NGramDuplicatesCleaner;
import net.clementlevallois.umigon.ngram.ops.NGramFinderBisForTextFragments;
import net.clementlevallois.umigon.ngram.ops.SentenceLikeFragmentsDetector;
import net.clementlevallois.umigon.tokenizer.controller.UmigonTokenizer;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.TextCleaningOps;

public class TextMiningService {

    private static final HttpClient SHARED_CLIENT = HttpClient.newHttpClient();
    private static final int MOST_FREQUENT_TERMS = 2_000;

    static {
        UmigonTokenizer.initialize();
    }

    public DataManager process(CowoConfig config, JobNotifier notifier) {
        DataManager dm = new DataManager();

        // 1. Cleaning
        Map<Integer, String> intermediaryMap = TextCleaningOps.doAllCleaningOps(config.mapOfLines, config.removeAccents);
        intermediaryMap = TextCleaningOps.putInLowerCase(intermediaryMap);
        dm.setMapOfLines(new TreeMap<>(intermediaryMap));

        List<String> languages = Arrays.asList(config.languageCodes.split(","));

        // 2. Tokenization
        notifier.notify("🪓 starting tokenization", 15);
        dm.getOriginalStringsPerLine().entrySet().parallelStream().forEach(entry -> {
            List<TextFragment> tokenized = UmigonTokenizer.tokenize(entry.getValue().toLowerCase().trim(), new HashSet<>());

            boolean keep = true;
            if (config.skipContentInParentheses && tokenized.size() >= 2) {
                TextFragment first = tokenized.get(0);
                TextFragment last = tokenized.get(tokenized.size() - 1);
                if (first.getOriginalForm().equals("(") && last.getOriginalForm().equals(")")) {
                    String s = tokenized.stream().map(Object::toString).reduce("", String::concat);
                    if (s.chars().anyMatch(Character::isLowerCase)) {
                        keep = true;
                    } else {
                        keep = false;
                    }
                }
            }
            if (keep) {
                dm.getTextFragmentsPerLine().put(entry.getKey(), tokenized);
            }
        });

        // 3. N-Grams
        notifier.notify("🖇️ entering ngram addition", 20);
        for (Map.Entry<Integer, List<TextFragment>> entry : dm.getTextFragmentsPerLine().entrySet()) {
            Set<String> stringifiedNGrams = new HashSet<>();
            List<NGram> nGramsForOneLine = new ArrayList<>();
            SentenceLikeFragmentsDetector sentenceDetector = new SentenceLikeFragmentsDetector();
            List<SentenceLike> sentenceLikeFragments = sentenceDetector.returnSentenceLikeFragments(entry.getValue());

            for (SentenceLike sentenceLikeFragment : sentenceLikeFragments) {
                List<NGram> ngrams = NGramFinderBisForTextFragments.generateNgramsUpto(sentenceLikeFragment.getNgrams(), config.maxNGram);
                sentenceLikeFragment.setNgrams(ngrams);
                nGramsForOneLine.addAll(ngrams);
            }

            Set<String> uniqueTest = new HashSet<>();
            for (NGram ngram : nGramsForOneLine) {
                String s = ngram.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii);
                if (uniqueTest.add(s)) {
                    dm.getListOfnGramsGlobal().add(ngram);
                    stringifiedNGrams.add(s);
                }
            }
            dm.getCleanedAndStrippedNGramsPerLine().put(entry.getKey(), stringifiedNGrams);
        }

        // 4. Stopwords Init
        notifier.notify("🧹 initializing stopword removal", 25);
        List<StopWordsRemover> stopwordRemovers = initStopwordRemovers(languages, config);

        // 5. Redundant NGrams
        notifier.notify("➿ removing redundant ngrams", 30);
        Multiset<String> multisetNGrams = new Multiset<>();
        dm.getListOfnGramsGlobal().parallelStream()
                .map(n -> n.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii))
                .forEach(multisetNGrams::addOne);

        int minOcc = calculateMinOcc(multisetNGrams.getSize(), config.minTermFreq);
        multisetNGrams.getInternalMap().entrySet().removeIf(e -> e.getValue() < minOcc);

        Set<String> languageStopwords = new HashSet<>();
        for (String lang : languages) {
            languageStopwords.addAll(Stopwords.getStopWords(lang).get("long"));
        }

        NGramDuplicatesCleaner cleaner = new NGramDuplicatesCleaner(languageStopwords);
        Map<String, Integer> goodSet = cleaner.removeDuplicates(multisetNGrams.getInternalMap(), config.maxNGram, true);

        List<NGram> keptNGrams = dm.getListOfnGramsGlobal().parallelStream()
                .filter(n -> goodSet.containsKey(n.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii)))
                .collect(Collectors.toList());
        dm.setListOfnGramsGlobal(keptNGrams);

        // 6. Stopwords Removal Run 1
        notifier.notify("🗑️ removing stopwords (1)", 40);
        removeStopwords(dm, stopwordRemovers, false);

        // 7. Capping
        notifier.notify("🧢 capping to 10k terms", 45);
        Multiset<NGram> capMultiset = new Multiset<>();
        capMultiset.addAllFromListOrSet(dm.getListOfnGramsGlobal());
        dm.setListOfnGramsGlobal(capMultiset.keepMostfrequent(capMultiset, 10_000).toListOfAllOccurrences());

        // 8. Lemmatization
        notifier.notify("💇 entering lemmatization", 50);
        applyLemmatization(dm, languages, config);

        // 9. Stopwords Removal Run 2
        notifier.notify("🧺 second round of stopwords", 60);
        removeStopwords(dm, stopwordRemovers, true);

        // 10. Small words / digits
        notifier.notify("✏️ cleaning small words", 65);
        dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal().parallelStream()
                .filter(n -> n.getOriginalFormLemmatized().length() >= config.minCharNumber
                && !n.getOriginalFormLemmatized().matches(".*\\d.*"))
                .collect(Collectors.toList()));

        // 11. Counting
        notifier.notify("🖩 counting ngrams", 70);
        Map<NGram, Long> topEntries = countAndDeduplicate(dm.getListOfnGramsGlobal(), config.minTermFreq);
        dm.setnGramsAndGlobalCount(topEntries);

        return dm;
    }

    // --- Private Helpers ---
    private List<StopWordsRemover> initStopwordRemovers(List<String> languages, CowoConfig config) {
        List<StopWordsRemover> removers = new ArrayList<>();
        for (String lang : languages) {
            StopWordsRemover swr = new StopWordsRemover(config.minCharNumber, lang);
            if (config.userSuppliedStopwords != null && !config.userSuppliedStopwords.isEmpty()) {
                swr.useUSerSuppliedStopwords(config.userSuppliedStopwords, config.replaceStopwords);
            }
            swr.addWordsToRemove(new HashSet<>());
            swr.addStopWordsToKeep(new HashSet<>());

            if (lang.equals("en")) {
                if (config.isScientificCorpus) {
                    swr.addFieldSpecificStopWords(Stopwords.getScientificStopwordsInEnglish());
                }
                if (config.removeFirstNames) {
                    swr.addFieldSpecificStopWords(Stopwords.getFirstNames("en"));
                }
            } else if (lang.equals("fr")) {
                if (config.isScientificCorpus) {
                    swr.addFieldSpecificStopWords(Stopwords.getScientificStopwordsInFrench());
                }
                if (config.removeFirstNames) {
                    swr.addFieldSpecificStopWords(Stopwords.getFirstNames("fr"));
                }
            }
            removers.add(swr);
        }
        return removers;
    }

    private void removeStopwords(DataManager dm, List<StopWordsRemover> removers, boolean useLemmatized) {
        List<NGram> filtered = new ArrayList<>();
        for (NGram ngram : dm.getListOfnGramsGlobal()) {
            String word = useLemmatized ? ngram.getOriginalFormLemmatized() : ngram.getCleanedAndStrippedNgramIfCondition(false);
            String wordStripped = useLemmatized ? "" : ngram.getCleanedAndStrippedNgramIfCondition(true);

            boolean remove = false;
            for (StopWordsRemover swr : removers) {
                if (swr.shouldItBeRemoved(word) || (!useLemmatized && swr.shouldItBeRemoved(wordStripped))) {
                    remove = true;
                    break;
                }
            }
            if (!remove) {
                filtered.add(ngram);
            }
        }
        dm.setListOfnGramsGlobal(filtered);
    }

    private void applyLemmatization(DataManager dm, List<String> languages, CowoConfig config) {
        if (!config.lemmatize) {
            for (NGram n : dm.getListOfnGramsGlobal()) {
                n.setOriginalFormLemmatized(n.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii));
            }
            return;
        }

        // Prepare map for API
        TreeMap<Integer, String> inputMap = new TreeMap<>();
        Set<String> candidates = new HashSet<>();
        for (NGram n : dm.getListOfnGramsGlobal()) {
            candidates.add(n.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii));
        }
        int i = 0;
        for (String s : candidates) {
            inputMap.put(i++, s);
        }

        Map<Integer, String> result = callLemmatizerApi(inputMap, config.languageCodes);

        // Apply results
        Map<String, String> lemmaMap = dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm();
        for (Integer key : inputMap.keySet()) {
            String in = inputMap.get(key);
            String out = result.getOrDefault(key, in);
            lemmaMap.put(in, out);
        }

        for (NGram n : dm.getListOfnGramsGlobal()) {
            String s = n.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii);
            n.setOriginalFormLemmatized(lemmaMap.getOrDefault(s, s));
        }
    }

    private Map<Integer, String> callLemmatizerApi(Map<Integer, String> input, String langCodes) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(input);
            oos.flush();
            byte[] data = bos.toByteArray();

            URI uri = new URI("http://localhost:9002/api/lemmatizer_light/map/" + langCodes);
            HttpRequest req = HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofByteArray(data)).uri(uri).build();
            HttpResponse<byte[]> resp = SHARED_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());

            if (resp.statusCode() == 200) {
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(resp.body()))) {
                    return (Map<Integer, String>) ois.readObject();
                }
            }
        } catch (Exception e) {
            System.out.println("Lemmatizer API failed");
        }
        return new HashMap<>();
    }

    private Map<NGram, Long> countAndDeduplicate(List<NGram> list, int minFreq) {
        Map<NGram, Long> counts = list.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Function<NGram, String> keyEx = NGram::getOriginalFormLemmatized;
        Map<String, Long> merged = counts.entrySet().parallelStream()
                .collect(Collectors.groupingBy(e -> keyEx.apply(e.getKey()), Collectors.summingLong(Map.Entry::getValue)));

        return merged.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> counts.keySet().stream().filter(n -> keyEx.apply(n).equals(e.getKey())).findFirst().orElse(null),
                        Map.Entry::getValue
                )).entrySet().stream()
                .filter(e -> e.getValue() >= minFreq)
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(MOST_FREQUENT_TERMS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private int calculateMinOcc(int size, int minTermFreq) {
        if (size < 10000) {
            return Math.min(1, minTermFreq);
        }
        if (size < 20000) {
            return Math.min(2, minTermFreq);
        }
        if (size < 30000) {
            return Math.min(3, minTermFreq);
        }
        if (size < 50000) {
            return Math.min(4, minTermFreq);
        }
        return Math.min(5, minTermFreq);
    }
}

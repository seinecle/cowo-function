package net.clementlevallois.cowo.controller;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
import net.clementlevallois.utils.Clock;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.TextCleaningOps;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphFactory;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.plugin.ExporterGEXF;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class CowoFunction_god_class {

    private final int MOST_FREQUENT_TERMS = 2_000;

    // Shared HttpClient for better performance (connection pooling)
    private static final HttpClient SHARED_CLIENT = HttpClient.newHttpClient();
    
    // Global Lock for Gephi operations
    private static final Object GEPHI_LOCK = new Object();

    private Map<Integer, String> mapResultLemmatization = new HashMap<>();
    private boolean flattenToAScii = false;
    private Clock clock;
    
    // FIXED: Removed 'static' to prevent users from overwriting each other's settings
    private boolean silentClock = true;
    private boolean skipContentInParentheses = false;
    private boolean removeLeaves = false;
    
    private String callbackURL = "";
    private String jobId = "";
    private boolean messagesEnabled = false;

    public static void main(String[] args) throws IOException {
        CowoFunction_god_class cowo = new CowoFunction_god_class();
        // Example path - adjust as needed
        Path path = Path.of("C:\\Users\\levallois\\Desktop\\corpus.txt");
        if (!Files.exists(path)) {
            System.out.println("File not found for test: " + path);
            return;
        }
        List<String> readAllLines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
        TreeMap<Integer, String> mapOfLines = new TreeMap<>();
        int i = 0;
        for (String line : readAllLines) {
            mapOfLines.put(i++, line);
        }
        cowo.silentClock = false;
        cowo.setFlattenToAScii(false);
        String lang = "pt";
        int minCharNumber = 3;
        boolean replaceStopwords = false;
        boolean removeFirstNames = true;
        boolean scientific = true;
        cowo.removeLeaves = true;
        int minTermFreq = 4;
        Set<String> userSuppliedStopwords = Set.of("consumer", "consumers", "textual", "marketing");
        int minCooCFreq = 2;
        String correction = "pmi";
        cowo.skipContentInParentheses = false;
        int maxNGram = 4;
        boolean lemmatize = true;
        boolean removeAccents = true;
        String analyze = cowo.analyze(mapOfLines, lang, userSuppliedStopwords, minCharNumber, replaceStopwords, scientific, removeFirstNames, removeAccents, minCooCFreq, minTermFreq, correction, maxNGram, lemmatize);
        Files.writeString(Path.of("C:\\Users\\levallois\\Desktop\\result.gexf"), analyze);
    }

    public void setFlattenToAScii(boolean flattenToAScii) {
        this.flattenToAScii = flattenToAScii;
    }

    public void setSilentClock(boolean silentClock) {
        this.silentClock = silentClock;
    }

    public void setSkipContentInParentheses(boolean skip) {
        this.skipContentInParentheses = skip;
    }

    public void setRemoveLeaves(boolean remove) {
        this.removeLeaves = remove;
    }

    public void setSessionIdAndCallbackURL(String callbackURL, String jobId) {
        this.callbackURL = callbackURL;
        this.jobId = jobId;
        messagesEnabled = true;
    }

    public void setSessionIdAndCallbackURL(String emptyOne, String callbackURL, String jobId) {
        this.callbackURL = callbackURL;
        this.jobId = jobId;
        messagesEnabled = true;
    }

    private void sendMessageBackHome(String message, int progress) {
        if (!messagesEnabled || callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", "INTERMEDIARY");
        joBuilder.add("message", message);
        joBuilder.add("function", "cowo");
        joBuilder.add("progress", progress);
        joBuilder.add("jobId", jobId);
        String joStringPayload = joBuilder.build().toString();
        doTheSend(joStringPayload);
    }

    private void doTheSend(String payload) {
        if (callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        try {
            URI uri = new URI(callbackURL);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .header("Content-Type", "application/json")
                    .uri(uri)
                    .build();

            SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            System.out.println("call back url was: " + callbackURL);
            Exceptions.printStackTrace(ex);
        }
    }

    public String analyze(TreeMap<Integer, String> mapOfLinesParam, String commaSeparatedLanguageCodes, Set<String> userSuppliedStopwords, int minCharNumber, boolean replaceStopwords, boolean isScientificCorpus, boolean removeFirstNames, boolean removeAccents, int minCoocFreq, int minTermFreq, String typeCorrection, int maxNGram, boolean lemmatize) {
        
        // DataManager instance local to this thread
        DataManager dm = new DataManager();
        
        // Initialization (ideally done once globally, but kept here for safety)
        UmigonTokenizer.initialize();

        try {
            Clock globalClock = new Clock("global clock", silentClock);

            Map<Integer, String> intermediaryMap = TextCleaningOps.doAllCleaningOps(mapOfLinesParam, removeAccents);
            intermediaryMap = TextCleaningOps.putInLowerCase(intermediaryMap);
            TreeMap<Integer, String> mapTree = new TreeMap<>(intermediaryMap);
            dm.setMapOfLines(mapTree);

            List<String> languages = Arrays.asList(commaSeparatedLanguageCodes.split(","));

            String message = "🪓 starting tokenization";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 15);

            /* TOKENIZE */
            dm.getOriginalStringsPerLine().entrySet()
                    .parallelStream() // Use parallel stream cautiously
                    .forEach(entry -> {
                        List<TextFragment> tokenized;
                        tokenized = UmigonTokenizer.tokenize(entry.getValue().toLowerCase().trim(), new HashSet<>());
                        if (skipContentInParentheses && tokenized.size() >= 2) {
                            TextFragment first = tokenized.get(0);
                            TextFragment last = tokenized.get(tokenized.size() - 1);
                            if (!(first.getOriginalForm().equals("(") && last.getOriginalForm().equals(")"))) {
                                dm.getTextFragmentsPerLine().put(entry.getKey(), tokenized);
                            } else {
                                String s = tokenized.stream().map(Object::toString).reduce("", String::concat);
                                if (!s.chars().anyMatch(Character::isLowerCase)) {
                                    dm.getTextFragmentsPerLine().put(entry.getKey(), tokenized);
                                }
                            }
                        } else {
                            dm.getTextFragmentsPerLine().put(entry.getKey(), tokenized);
                        }
                    });

            clock.closeAndPrintClock();

            /* ADD NGRAMS */
            message = "🖇️ entering ngram addition";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 20);
            for (Map.Entry<Integer, List<TextFragment>> entry : dm.getTextFragmentsPerLine().entrySet()) {
                Set<String> stringifiedNGrams = new HashSet<>();
                List<NGram> nGramsForOneLine = new ArrayList<>();
                SentenceLikeFragmentsDetector sentenceDetector = new SentenceLikeFragmentsDetector();
                List<SentenceLike> sentenceLikeFragments = sentenceDetector.returnSentenceLikeFragments(entry.getValue());
                for (SentenceLike sentenceLikeFragment : sentenceLikeFragments) {
                    List<NGram> ngrams = NGramFinderBisForTextFragments.generateNgramsUpto(sentenceLikeFragment.getNgrams(), maxNGram);
                    sentenceLikeFragment.setNgrams(ngrams);
                    nGramsForOneLine.addAll(ngrams);
                }

                Set<String> nGramsForUniquenessTest = new HashSet<>();
                for (NGram ngram : nGramsForOneLine) {
                    String stringfiedNGram = ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii);
                    if (nGramsForUniquenessTest.add(stringfiedNGram)) {
                        dm.getListOfnGramsGlobal().add(ngram);
                        stringifiedNGrams.add(stringfiedNGram);
                    }
                }
                dm.getCleanedAndStrippedNGramsPerLine().put(entry.getKey(), stringifiedNGrams);
            }
            clock.closeAndPrintClock();

            /* STOPWORDS REMOVAL INIT */
            message = "🧹 initializing the stopword removal";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 25);

            List<StopWordsRemover> stopwordRemovers = new ArrayList<>();
            for (String language : languages) {
                stopwordRemovers.add(new StopWordsRemover(minCharNumber, language));
            }

            if (userSuppliedStopwords != null && !userSuppliedStopwords.isEmpty()) {
                for (StopWordsRemover swr : stopwordRemovers) {
                    swr.useUSerSuppliedStopwords(userSuppliedStopwords, replaceStopwords);
                }
            }

            for (StopWordsRemover swr : stopwordRemovers) {
                swr.addWordsToRemove(new HashSet<>());
                swr.addStopWordsToKeep(new HashSet<>());
                if (swr.getLanguage().equals("en")) {
                    if (isScientificCorpus) {
                        swr.addFieldSpecificStopWords(Stopwords.getScientificStopwordsInEnglish());
                    }
                    if (removeFirstNames) {
                        swr.addFieldSpecificStopWords(Stopwords.getFirstNames("en"));
                    }
                }
                if (swr.getLanguage().equals("fr")) {
                    if (isScientificCorpus) {
                        swr.addFieldSpecificStopWords(Stopwords.getScientificStopwordsInFrench());
                    }
                    if (removeFirstNames) {
                        swr.addFieldSpecificStopWords(Stopwords.getFirstNames("fr"));
                    }
                }
            }
            clock.closeAndPrintClock();

            message = "➿ creating a multiset to remove redundant ngrams";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 30);

            Multiset<String> multisetOfNGramsSoFarStringified = new Multiset<>();
            dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .map(ngram -> ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii))
                    .forEach(multisetOfNGramsSoFarStringified::addOne);

            clock.closeAndPrintClock();

            /* REMOVE INFREQUENT NGRAMS */
            final int minOcc;
            if (multisetOfNGramsSoFarStringified.getSize() < 10_000) {
                minOcc = Math.min(1, minTermFreq);
            } else if (multisetOfNGramsSoFarStringified.getSize() < 20_000) {
                minOcc = Math.min(2, minTermFreq);
            } else if (multisetOfNGramsSoFarStringified.getSize() < 30_000) {
                minOcc = Math.min(3, minTermFreq);
            } else if (multisetOfNGramsSoFarStringified.getSize() < 50_000) {
                minOcc = Math.min(4, minTermFreq);
            } else {
                minOcc = Math.min(5, minTermFreq);
            }

            multisetOfNGramsSoFarStringified.getInternalMap().entrySet().removeIf(entry -> entry.getValue() < minOcc);

            message = "➿ doing the redundant ngrams removal";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 35);

            Set<String> stopwords = new HashSet<>();
            for (String language : languages) {
                stopwords.addAll(Stopwords.getStopWords(language).get("long"));
            }
            NGramDuplicatesCleaner cleaner = new NGramDuplicatesCleaner(stopwords);
            Map<String, Integer> goodSetWithoutDuplicates = cleaner.removeDuplicates(multisetOfNGramsSoFarStringified.getInternalMap(), maxNGram, true);

            Set<String> stringsToKeep = goodSetWithoutDuplicates.keySet();
            List<NGram> nGramsToKeep = dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram -> stringsToKeep.contains(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii)))
                    .collect(Collectors.toList());
            dm.setListOfnGramsGlobal(nGramsToKeep);

            clock.closeAndPrintClock();

            /* STOPWORDS REMOVAL RUN 1 */
            message = "🗑️ removing stopwords (1)";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 40);

            List<NGram> listOfnGramsGlobal = dm.getListOfnGramsGlobal();
            List<NGram> listOfnGramsGlobalMinusStopwords = new ArrayList<>();

            for (NGram ngram : listOfnGramsGlobal) {
                String wordToTestStripped = ngram.getCleanedAndStrippedNgramIfCondition(true);
                String wordToTest = ngram.getCleanedAndStrippedNgramIfCondition(false);
                boolean removal = false;
                for (StopWordsRemover swr : stopwordRemovers) {
                    if (swr.shouldItBeRemoved(wordToTestStripped) || swr.shouldItBeRemoved(wordToTest)) {
                        removal = true;
                        break;
                    }
                }
                if (!removal) {
                    listOfnGramsGlobalMinusStopwords.add(ngram);
                }
            }
            dm.setListOfnGramsGlobal(listOfnGramsGlobalMinusStopwords);
            clock.closeAndPrintClock();

            /* CAPPING */
            message = "🧢 capping to 10_000 most freq ngrams";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 45);

            listOfnGramsGlobal = dm.getListOfnGramsGlobal();
            Multiset<NGram> multisetToCap = new Multiset<>();
            multisetToCap.addAllFromListOrSet(listOfnGramsGlobal);
            Multiset<NGram> mostFrequentNGrams = multisetToCap.keepMostfrequent(multisetToCap, 10_000);
            dm.setListOfnGramsGlobal(mostFrequentNGrams.toListOfAllOccurrences());
            clock.closeAndPrintClock();

            /* LEMMATIZATION */
            message = "💇 entering lemmatization";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 50);

            if (!lemmatize) {
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    ngram.setOriginalFormLemmatized(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii));
                }
            } else {
                TreeMap<Integer, String> mapInputCleanedAndStripped = new TreeMap<>();
                Set<String> candidatesToLemmatization = new HashSet<>();
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    candidatesToLemmatization.add(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii));
                }
                int idx = 0;
                for (String string : candidatesToLemmatization) {
                    mapInputCleanedAndStripped.put(idx++, string);
                }
                
                // Lemmatization API Call
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                    oos.writeObject(mapInputCleanedAndStripped);
                    oos.flush();
                    byte[] data = bos.toByteArray();

                    if (languages.contains("en") || languages.contains("fr") || languages.contains("es")) {
                        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(data);
                        URI uri = new URI("http://localhost:9002/api/lemmatizer_light/map/" + commaSeparatedLanguageCodes);
                        HttpRequest request = HttpRequest.newBuilder().POST(bodyPublisher).uri(uri).build();
                        
                        // Use Shared Client
                        HttpResponse<byte[]> resp = SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        
                        if (resp.statusCode() == 200) {
                            try (ByteArrayInputStream bis = new ByteArrayInputStream(resp.body());
                                 ObjectInputStream ois = new ObjectInputStream(bis)) {
                                mapResultLemmatization = (Map<Integer, String>) ois.readObject();
                            } catch (ClassNotFoundException ex) {
                                System.out.println("error in deserialization");
                            }
                        } else {
                            System.out.println("lemmatization API returned: " + resp.statusCode());
                            mapResultLemmatization = new HashMap<>();
                        }
                    }
                }
                clock.closeAndPrintClock();

                message = "💇 finishing up lemmatization";
                clock = new Clock(message, silentClock);
                sendMessageBackHome(message, 55);

                for (Integer key : mapInputCleanedAndStripped.keySet()) {
                    String input = mapInputCleanedAndStripped.get(key);
                    String output = (mapResultLemmatization != null) ? mapResultLemmatization.getOrDefault(key, input) : input;
                    dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().put(input, output);
                }
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    String stringifiedNgram = ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii);
                    String lemmatizedForm = dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().getOrDefault(stringifiedNgram, stringifiedNgram);
                    ngram.setOriginalFormLemmatized(lemmatizedForm);
                }
                clock.closeAndPrintClock();
            }

            /* STOPWORDS REMOVAL RUN 2 */
            message = "🧺 second round of stopwords removal";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 60);

            listOfnGramsGlobal = dm.getListOfnGramsGlobal();
            listOfnGramsGlobalMinusStopwords = new ArrayList<>();

            for (NGram ngram : listOfnGramsGlobal) {
                String wordToTest = ngram.getOriginalFormLemmatized();
                boolean removal = false;
                for (StopWordsRemover swr : stopwordRemovers) {
                    if (swr.shouldItBeRemoved(wordToTest)) {
                        removal = true;
                        break;
                    }
                }
                if (!removal) {
                    listOfnGramsGlobalMinusStopwords.add(ngram);
                }
            }
            dm.setListOfnGramsGlobal(listOfnGramsGlobalMinusStopwords);
            clock.closeAndPrintClock();

            /* REMOVE SMALL WORDS & DIGITS */
            message = "✏️ removal of small words and digits";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 65);

            dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram -> ngram.getOriginalFormLemmatized().length() >= minCharNumber && !ngram.getOriginalFormLemmatized().matches(".*\\d.*"))
                    .collect(Collectors.toList()));
            clock.closeAndPrintClock();

            /* COUNTING & DEDUPLICATING */
            message = "🖩counting ngrams";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 70);

            Map<NGram, Long> nGramsAndTheirCounts = dm.getListOfnGramsGlobal().stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            Function<NGram, String> keyExtractor = NGram::getOriginalFormLemmatized;

            Map<String, Long> mergedMap = nGramsAndTheirCounts.entrySet().parallelStream()
                    .collect(Collectors.groupingBy(
                            entry -> keyExtractor.apply(entry.getKey()),
                            Collectors.summingLong(Map.Entry::getValue)
                    ));

            Map<NGram, Long> deduplicatedMap = mergedMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> nGramsAndTheirCounts.keySet().parallelStream()
                                    .filter(ngram -> keyExtractor.apply(ngram).equals(entry.getKey()))
                                    .findFirst().orElse(null),
                            Map.Entry::getValue
                    ));

            Map<NGram, Long> topEntries = deduplicatedMap.entrySet()
                    .parallelStream()
                    .filter(entry -> entry.getValue() >= minTermFreq)
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(MOST_FREQUENT_TERMS)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            dm.setnGramsAndGlobalCount(topEntries);
            clock.closeAndPrintClock();

            /* CO-OCCURRENCES */
            message = "🧠 calculating cooccurrences";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 75);

            Multiset<Cooc> listCoocTotal = new Multiset<>();
            Map<String, NGram> cleanedAndStrippedToNgram = new HashMap<>();
            Map<String, NGram> lemmatizedToNgram = new HashMap<>();

            for (NGram ngram : dm.getnGramsAndGlobalCount().keySet()) {
                cleanedAndStrippedToNgram.put(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii), ngram);
                lemmatizedToNgram.put(ngram.getOriginalFormLemmatized(), ngram);
            }

            dm.getCleanedAndStrippedNGramsPerLine().entrySet().forEach(entry -> {
                Set<String> cleanedAndStrippedStringifiedNGramsForOneLine = entry.getValue();
                Map<String, NGram> ngramsInCurrentLine = new HashMap<>();

                for (String oneNGramForOneLine : cleanedAndStrippedStringifiedNGramsForOneLine) {
                    if (cleanedAndStrippedToNgram.containsKey(oneNGramForOneLine)) {
                        NGram nGramFound = cleanedAndStrippedToNgram.get(oneNGramForOneLine);
                        ngramsInCurrentLine.put(nGramFound.getOriginalFormLemmatized(), nGramFound);
                    } else if (lemmatizedToNgram.containsKey(oneNGramForOneLine)) {
                        NGram nGramFound = lemmatizedToNgram.get(oneNGramForOneLine);
                        ngramsInCurrentLine.put(nGramFound.getOriginalFormLemmatized(), nGramFound);
                    }
                }

                if (ngramsInCurrentLine.size() > 1) {
                    NGram[] arrayNGram = new NGram[ngramsInCurrentLine.size()];
                    try {
                        List<Cooc> coocs = new PerformCombinationsOnNGrams(ngramsInCurrentLine.values().toArray(arrayNGram)).call();
                        listCoocTotal.addAllFromListOrSet(coocs);
                    } catch (InterruptedException | IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
            clock.closeAndPrintClock();

            /* CLEANING COOCS */
            message = "🧹 removing infrequent cooc";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 80);

            Integer nbCoocs = listCoocTotal.getSize();
            int effectiveMinCoocFreq;
            if (nbCoocs < 50_000) {
                effectiveMinCoocFreq = 1;
            } else if (nbCoocs < 80_000) {
                effectiveMinCoocFreq = 2;
            } else if (nbCoocs < 110_000) {
                effectiveMinCoocFreq = 3;
            } else {
                effectiveMinCoocFreq = 4;
            }
            List<Map.Entry<Cooc, Integer>> sortDesckeepAboveMinFreq = listCoocTotal.sortDesckeepAboveMinFreq(listCoocTotal, effectiveMinCoocFreq);

            Multiset<String> coocsStringified = new Multiset<>();
            Multiset<String> nodesInEdgesStringified = new Multiset<>();
            Multiset<Cooc> coocs = new Multiset<>();

            for (Map.Entry<Cooc, Integer> entry : sortDesckeepAboveMinFreq) {
                coocsStringified.addSeveral(entry.getKey().toString(), entry.getValue());
                coocs.addSeveral(entry.getKey(), entry.getValue());
                nodesInEdgesStringified.addOne(entry.getKey().a.getOriginalFormLemmatized());
                nodesInEdgesStringified.addOne(entry.getKey().b.getOriginalFormLemmatized());
            }

            // SAFETY CHECK FOR GIANT GRAPHS
            int numberOfNodes = nodesInEdgesStringified.getElementSet().size();
            int numberOfEdges = coocsStringified.getElementSet().size();
            if (numberOfEdges > 50_000 && numberOfEdges > (numberOfNodes * 50)) {
                int maxNumberEdgesInt = numberOfNodes * 50;
                List<Map.Entry<Cooc, Integer>> sortDesckeepMostfrequent = coocs.sortDesckeepMostfrequent(coocs, maxNumberEdgesInt);
                coocs = new Multiset<>();
                nodesInEdgesStringified = new Multiset<>();
                for (Map.Entry<Cooc, Integer> entry : sortDesckeepMostfrequent) {
                    coocs.addSeveral(entry.getKey(), entry.getValue());
                    nodesInEdgesStringified.addOne(entry.getKey().a.getOriginalFormLemmatized());
                    nodesInEdgesStringified.addOne(entry.getKey().b.getOriginalFormLemmatized());
                }
            }
            clock.closeAndPrintClock();

            /* GEPHI GRAPH CONSTRUCTION - CRITICAL SECTION */
            message = "⚽ adding nodes to graph";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message, 85);

            // Synchronize on the global lock for all Gephi operations
            synchronized (GEPHI_LOCK) {
                ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
                
                // Ensure clean state before starting
                if (pc.getCurrentProject() != null) {
                    pc.closeCurrentProject();
                }

                pc.newProject();
                Workspace workspace = pc.getCurrentWorkspace();
                
                try {
                    GraphModel gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);

                    workspace.getProject().getProjectMetadata().setAuthor("created with nocodefunctions.com");
                    String description = "language: " + commaSeparatedLanguageCodes + "; lemmatization: " + lemmatize;
                    workspace.getProject().getProjectMetadata().setDescription(description);

                    gm.getGraph().clear();

                    Column countTermsColumn = gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
                    Column countPairsColumn = gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
                    if (typeCorrection.equals("pmi")) {
                        gm.getEdgeTable().addColumn("pmi", Float.TYPE);
                    }

                    GraphFactory factory = gm.factory();
                    Graph graphResult = gm.getGraph();
                    
                    Map<String, Node> nodesMap = new HashMap<>();
                    Set<String> removedNodes = new HashSet<>();
                    Set<String> elementSet = nodesInEdgesStringified.getElementSet();

                    // Add Nodes
                    for (Map.Entry<NGram, Long> entry : dm.getnGramsAndGlobalCount().entrySet()) {
                        NGram ngram = entry.getKey();
                        String nodeLabel = ngram.getOriginalFormLemmatized();

                        if (!elementSet.contains(nodeLabel)) {
                            removedNodes.add(nodeLabel);
                            continue;
                        }
                        if (removeLeaves && nodesInEdgesStringified.getCount(nodeLabel) == 1) {
                            removedNodes.add(nodeLabel);
                            continue;
                        }

                        Node node = factory.newNode(nodeLabel);
                        node.setLabel(nodeLabel);
                        node.setAttribute(countTermsColumn, entry.getValue().intValue());
                        graphResult.addNode(node);
                        nodesMap.put(nodeLabel, node);
                    }
                    clock.closeAndPrintClock();

                    message = "🔗 adding edges to graph";
                    clock = new Clock(message, silentClock);
                    sendMessageBackHome(message, 90);

                    // Calculate Weights
                    double maxValuePMI = 0.00001d;
                    float maxValueCountEdges = 0;
                    Map<String, Double> edgesAndTheirPMIWeightsBeforeRescaling = new HashMap<>();

                    for (Cooc cooc : coocs.getElementSet()) {
                        if (removedNodes.contains(cooc.a.getOriginalFormLemmatized()) || removedNodes.contains(cooc.b.getOriginalFormLemmatized())) {
                            continue;
                        }
                        Integer countEdge = coocsStringified.getCount(cooc.toString());
                        Integer freqSource = dm.getnGramsAndGlobalCount().get(cooc.a).intValue();
                        Integer freqTarget = dm.getnGramsAndGlobalCount().get(cooc.b).intValue();

                        double edgeWeightPMI = (double) countEdge / (freqSource * freqTarget);
                        if (edgeWeightPMI > maxValuePMI) {
                            maxValuePMI = edgeWeightPMI;
                        }
                        edgesAndTheirPMIWeightsBeforeRescaling.put(cooc.toString(), edgeWeightPMI);
                        if (countEdge > maxValueCountEdges) {
                            maxValueCountEdges = countEdge;
                        }
                    }

                    // Add Edges
                    for (Cooc cooc : coocs.getElementSet()) {
                        if (removedNodes.contains(cooc.a.getOriginalFormLemmatized()) || removedNodes.contains(cooc.b.getOriginalFormLemmatized())) {
                            continue;
                        }
                        Node nodeSource = nodesMap.get(cooc.a.getOriginalFormLemmatized());
                        Node nodeTarget = nodesMap.get(cooc.b.getOriginalFormLemmatized());

                        if (nodeSource != null && nodeTarget != null) {
                            Integer countEdge = coocsStringified.getCount(cooc.toString());
                            double edgeWeightRescaledToTen;
                            double edgeWeightBeforeRescaling;

                            if (typeCorrection.equals("pmi")) {
                                edgeWeightBeforeRescaling = edgesAndTheirPMIWeightsBeforeRescaling.get(cooc.toString());
                                edgeWeightRescaledToTen = edgeWeightBeforeRescaling * 10 / maxValuePMI;
                            } else {
                                edgeWeightBeforeRescaling = countEdge.doubleValue();
                                edgeWeightRescaledToTen = edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                            }

                            Edge edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                            edge.setAttribute(countPairsColumn, countEdge);
                            if (typeCorrection.equals("pmi")) {
                                edge.setAttribute("pmi", (float) edgeWeightBeforeRescaling);
                            }
                            graphResult.addEdge(edge);
                        }
                    }
                    clock.closeAndPrintClock();

                    message = "🗺️ applying a Force Atlas mapping";
                    sendMessageBackHome(message, 95);
                    clock = new Clock(message, silentClock);
                    apply_FA2_layout(gm);
                    clock.closeAndPrintClock();

                    message = "🛥️ exporting graph";
                    clock = new Clock(message, silentClock);
                    sendMessageBackHome(message, 95);

                    ExportController ec = Lookup.getDefault().lookup(ExportController.class);
                    ExporterGEXF exporterGexf = (ExporterGEXF) ec.getExporter("gexf");
                    exporterGexf.setWorkspace(workspace);
                    exporterGexf.setExportDynamic(false);
                    exporterGexf.setExportPosition(true);
                    exporterGexf.setExportSize(false);
                    exporterGexf.setExportColors(false);
                    exporterGexf.setExportMeta(true);

                    try (StringWriter stringWriter = new StringWriter()) {
                        ec.exportWriter(stringWriter, exporterGexf);
                        return stringWriter.toString();
                    }

                } finally {
                    // CRITICAL: Always close the project to release Gephi locks
                    if (pc.getCurrentProject() != null) {
                        pc.closeCurrentProject();
                    }
                }
            } // End of Synchronized Block
            
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }
        return "error in cowo function";
    }

    private void apply_FA2_layout(GraphModel gm) {
        ForceAtlas2Builder layoutBuilder = new ForceAtlas2Builder();
        ForceAtlas2 layout = new ForceAtlas2(layoutBuilder);
        layout.setGraphModel(gm);
        layout.resetPropertiesValues();

        int threads = Runtime.getRuntime().availableProcessors() * 2 - 1;
        System.out.println("threads being used: " + threads);

        int nodeCount = gm.getGraph().getNodeCount();
        int edgeCount = gm.getGraph().getEdgeCount();
        int totalGraphSize = nodeCount + edgeCount;

        Duration durationLayout;
        if (totalGraphSize < 500) {
            durationLayout = Duration.ofMillis(300);
        } else if (totalGraphSize < 1000) {
            durationLayout = Duration.ofMillis(750);
        } else if (totalGraphSize < 5000) {
            durationLayout = Duration.ofSeconds(2);
        } else {
            durationLayout = Duration.ofSeconds(3);
        }

        layout.setScalingRatio(50d);
        layout.setThreadsCount(threads);
        layout.setAdjustSizes(Boolean.FALSE);
        layout.setJitterTolerance(2d);
        layout.setBarnesHutOptimize(Boolean.TRUE);
        layout.setBarnesHutTheta(2d);

        layout.initAlgo();
        long start = System.currentTimeMillis();
        while (layout.canAlgo()) {
            layout.goAlgo();
            long now = System.currentTimeMillis();
            if ((now - start) > durationLayout.toMillis()) {
                break;
            }
        }
        layout.endAlgo();
    }
}
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
public class CowoFunction {

    private final int MOST_FREQUENT_TERMS = 2_000;

    private Map<Integer, String> mapResultLemmatization = new HashMap();
    private boolean flattenToAScii = false;
    private Clock clock;
    private static boolean silentClock = true;
    private static boolean skipContentInParentheses = false;
    private static boolean removeLeaves = false;
    private String sessionId = "";
    private String callbackURL = "";
    private String dataPersistenceId = "";
    private boolean messagesEnabled = false;

    public static void main(String[] args) throws IOException {
        CowoFunction cowo = new CowoFunction();
        Path path = Path.of("C:\\Users\\levallois\\OneDrive - Aescra Emlyon Business School\\Bureau\\tests\\user provided\\Attachments-Re Error trying to process 50Mb corpus\\corpus_limpo_infopasto_anotado_semcomentarios.txt");
        List<String> readAllLines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
        TreeMap<Integer, String> mapOfLines = new TreeMap();
        int i = 0;
        for (String line : readAllLines) {
            mapOfLines.put(i++, line);
        }
        silentClock = false;
        cowo.setFlattenToAScii(false);
        String lang = "pt";
        int minCharNumber = 3;
        boolean replaceStopwords = false;
        boolean removeFirstNames = true;
        boolean scientific = true;
        removeLeaves = true;
        int minTermFreq = 4;
        Set<String> userSuppliedStopwords = Set.of("consumer", "consumers", "textual", "marketing");
        int minCooCFreq = 2;
        String correction = "pmi";
        skipContentInParentheses = false;
        int maxNGram = 4;
        boolean lemmatize = true;
        boolean removeAccents = true;
        String analyze = cowo.analyze(mapOfLines, lang, userSuppliedStopwords, minCharNumber, replaceStopwords, scientific, removeFirstNames, removeAccents, minCooCFreq, minTermFreq, correction, maxNGram, lemmatize);
        Files.writeString(Path.of("C:\\Users\\levallois\\OneDrive - Aescra Emlyon Business School\\Bureau\\tests\\cowo--min-occ-4-words-removed.gexf"), analyze);
    }

    public void setFlattenToAScii(boolean flattenToAScii) {
        this.flattenToAScii = flattenToAScii;
    }

    public void setSessionIdAndCallbackURL(String sessionId, String callbackURL, String dataPersistenceId) {
        this.sessionId = sessionId;
        this.callbackURL = callbackURL;
        this.dataPersistenceId = dataPersistenceId;
        messagesEnabled = true;
    }

    private void sendMessageBackHome(String message) {
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", "INTERMEDIARY");
        joBuilder.add("message", message);
        joBuilder.add("function", "cowo");
        joBuilder.add("sessionId", sessionId);
        joBuilder.add("dataPersistenceId", dataPersistenceId);
        String joStringPayload = joBuilder.build().toString();
        doTheSend(joStringPayload);
    }

    private void doTheSend(String payload) {
        if (callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI uri = new URI(callbackURL);

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .header("Content-Type", "application/json")
                    .uri(uri)
                    .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            System.out.println("call back url was: " + callbackURL);
            Exceptions.printStackTrace(ex);
        }
    }

    public String analyze(TreeMap<Integer, String> mapOfLinesParam, String commaSeparatedLanguageCodes, Set<String> userSuppliedStopwords, int minCharNumber, boolean replaceStopwords, boolean isScientificCorpus, boolean removeFirstNames, boolean removeAccents, int minCoocFreq, int minTermFreq, String typeCorrection, int maxNGram, boolean lemmatize) {
        ObjectOutputStream oos = null;
        DataManager dm = new DataManager();
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        Workspace workspace = null;

        try {
            Clock globalClock = new Clock("global clock", silentClock);
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();

            Map<Integer, String> intermediaryMap = TextCleaningOps.doAllCleaningOps(mapOfLinesParam, removeAccents);
            intermediaryMap = TextCleaningOps.putInLowerCase(intermediaryMap);
            TreeMap<Integer, String> mapTree = new TreeMap(intermediaryMap);
            dm.setMapOfLines(mapTree);

            List<String> languages = Arrays.asList(commaSeparatedLanguageCodes.split(","));

            String message = "🪓 starting tokenization";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message);

            /* TOKENIZE */
            UmigonTokenizer.initialize();

            dm.getOriginalStringsPerLine().entrySet()
                    .stream()
                    .forEach(entry -> {
                        List<TextFragment> tokenized = null;
                        tokenized = UmigonTokenizer.tokenize(entry.getValue().toLowerCase().trim(), new HashSet());
                        if (skipContentInParentheses && tokenized.size() >= 2) {
                            TextFragment first = tokenized.get(0);
                            TextFragment last = tokenized.get(tokenized.size() - 1);
                            if (!(first.getOriginalForm().equals("(") && last.getOriginalForm().equals(")"))) {
                                dm.getTextFragmentsPerLine().put(entry.getKey(), tokenized);
                            } else {
                                String s = tokenized.stream().map(e -> e.toString()).reduce("", String::concat);
                                if (!s.chars().anyMatch(X -> Character.isLowerCase(X))) {
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
            sendMessageBackHome(message);
            for (Map.Entry<Integer, List<TextFragment>> entry : dm.getTextFragmentsPerLine().entrySet()) {
                Set<String> stringifiedNGrams = new HashSet();
                List<NGram> nGramsForOneLine = new ArrayList();
                SentenceLikeFragmentsDetector sentenceDetector = new SentenceLikeFragmentsDetector();
                List<SentenceLike> sentenceLikeFragments = sentenceDetector.returnSentenceLikeFragments(entry.getValue());
                for (SentenceLike sentenceLikeFragment : sentenceLikeFragments) {
                    List<NGram> ngrams = NGramFinderBisForTextFragments.generateNgramsUpto(sentenceLikeFragment.getNgrams(), maxNGram);
                    sentenceLikeFragment.setNgrams(ngrams);
                    nGramsForOneLine.addAll(ngrams);
                }
                /* removing ngrams that appear several times in a given line.
                experience shows that it is better to avoid that a single line gets a lot of weight
                just because it mentions some terms repeatedly
                 */

                Set<String> nGramsForUniquenessTest = new HashSet();
                for (NGram ngram : nGramsForOneLine) {
                    // using the property that when we add an element to a set, it returns true only if the element was not already present.
                    // convenient to test uniqueness
                    String stringfiedNGram = ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii);
                    if (nGramsForUniquenessTest.add(stringfiedNGram)) {
                        dm.getListOfnGramsGlobal().add(ngram);
                        stringifiedNGrams.add(stringfiedNGram);
                    }
                }
                dm.getCleanedAndStrippedNGramsPerLine().put(entry.getKey(), stringifiedNGrams);
            }
            clock.closeAndPrintClock();

            /* REMOVE STOPWORDS 
            
            stopwords removal - initialization
            
             */
            message = "🧹 initializing the stopword removal";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            List<StopWordsRemover> stopwordRemovers = new ArrayList();
            for (String language : languages) {
                stopwordRemovers.add(new StopWordsRemover(minCharNumber, language));
            }

            if (userSuppliedStopwords != null && !userSuppliedStopwords.isEmpty()) {
                for (StopWordsRemover swr : stopwordRemovers) {
                    swr.useUSerSuppliedStopwords(userSuppliedStopwords, replaceStopwords);
                }
            }

            for (StopWordsRemover swr : stopwordRemovers) {
                swr.addWordsToRemove(new HashSet());
                swr.addStopWordsToKeep(new HashSet());
                if (swr.getLanguage().equals("en")) {
                    if (isScientificCorpus) {
                        Set<String> scientificStopwordsInEnglish = Stopwords.getScientificStopwordsInEnglish();
                        swr.addFieldSpecificStopWords(scientificStopwordsInEnglish);
                    }

                    if (removeFirstNames) {
                        Set<String> englishFirstNames = Stopwords.getFirstNames("en");
                        swr.addFieldSpecificStopWords(englishFirstNames);
                    }
                }
                if (swr.getLanguage().equals("fr")) {
                    if (isScientificCorpus) {
                        Set<String> scientificStopwordsInFrench = Stopwords.getScientificStopwordsInFrench();
                        swr.addFieldSpecificStopWords(scientificStopwordsInFrench);
                    }

                    if (removeFirstNames) {
                        Set<String> frenchFirstNames = Stopwords.getFirstNames("fr");
                        swr.addFieldSpecificStopWords(frenchFirstNames);
                    }
                }
            }

            clock = new Clock(message, silentClock);

            message = "➿ creating a multiset to remove redundant ngrams";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message);

            Multiset<String> multisetOfNGramsSoFarStringified = new Multiset();
            dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .map(ngram -> ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii))
                    .forEach(ngram -> multisetOfNGramsSoFarStringified.addOne(ngram));

            clock.closeAndPrintClock();

            /* REMOVE NGRAMS THAT OVERLAP WITH OTHERS WHILE BEING INFREQUENT */
            final int minOcc;

            /* REMOVE INFREQUENT NGRAMS IN A PREEMPTIVE WAY FOR VERY LARGE NETWORKS */
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

            sendMessageBackHome(message);

            Set<String> stopwords = new HashSet();
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

            /* stopwords removal - run */
            message = "🗑️ removing stopwords (1)";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            
            List<NGram> listOfnGramsGlobal = dm.getListOfnGramsGlobal();
            List<NGram> listOfnGramsGlobalMinusStopwords = new ArrayList();
            
            for (NGram ngram: listOfnGramsGlobal){
                String wordToTestStripped = ngram.getCleanedAndStrippedNgramIfCondition(true);
                String wordToTest = ngram.getCleanedAndStrippedNgramIfCondition(false);
                boolean removal = false;
                for (StopWordsRemover swr : stopwordRemovers) {
                    boolean shouldNgramStrippedBeRemoved = swr.shouldItBeRemoved(wordToTestStripped);
                    boolean shouldNgramBeRemoved = swr.shouldItBeRemoved(wordToTest);
                    if (shouldNgramStrippedBeRemoved || shouldNgramBeRemoved){
                        removal = true;
                        break;
                    }
                }
                if (!removal){
                    listOfnGramsGlobalMinusStopwords.add(ngram);
                }
            }
            dm.setListOfnGramsGlobal(listOfnGramsGlobalMinusStopwords);

            clock.closeAndPrintClock();

            /*
            capping the number of words to the 10,000 most frequent
             because even with the lemmatization step that will occur below
             we will likely end up at well above 9,000 words, which is more than enough for a network representation
             */
            message = "🧢 capping to 10_000 most freq ngrams";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            listOfnGramsGlobal = dm.getListOfnGramsGlobal();
            Multiset<NGram> multisetToCap = new Multiset();
            multisetToCap.addAllFromListOrSet(listOfnGramsGlobal);
            Multiset<NGram> mostFrequentNGrams = multisetToCap.keepMostfrequent(multisetToCap, 10_000);
            dm.setListOfnGramsGlobal(mostFrequentNGrams.toListOfAllOccurrences());
            clock.closeAndPrintClock();

            /* LEMMATIZATION */
            message = "💇 entering lemmatization";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            if (!lemmatize) {
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    String nonLemmatizedNGram = ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii);
                    ngram.setOriginalFormLemmatized(nonLemmatizedNGram);
                }
            } else {
                TreeMap<Integer, String> mapInputCleanedAndStripped = new TreeMap();
                Set<String> candidatesToLemmatization = new HashSet();
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    candidatesToLemmatization.add(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii));
                }
                int i = 0;
                for (String string : candidatesToLemmatization) {
                    mapInputCleanedAndStripped.put(i++, string);
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                oos = new ObjectOutputStream(bos);
                oos.writeObject(mapInputCleanedAndStripped);
                oos.flush();
                byte[] data = bos.toByteArray();

                if (languages.contains("en") | languages.contains("fr") | languages.contains("es")) {

                    HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(data);

                    URI uri = new URI("http://localhost:7002/api/lemmatizer_light/map/" + commaSeparatedLanguageCodes);

                    request = HttpRequest.newBuilder()
                            .POST(bodyPublisher)
                            .uri(uri)
                            .build();

                    HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() == 200) {
                        byte[] body = resp.body();
                        try (
                                ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                            mapResultLemmatization = (Map<Integer, String>) ois.readObject();
                        } catch (IOException | ClassNotFoundException ex) {
                            System.out.println("error in deserialization of lemmatizer maps API result");
                        }
                    } else {
                        System.out.println("lemmatization lightweight maps returned by the API was not a 200 code");
                        mapResultLemmatization = new HashMap();
                    }

                }
                clock.closeAndPrintClock();

                message = "💇 finishing up lemmatization";
                clock = new Clock(message, silentClock);

                sendMessageBackHome(message);

                for (Integer key : mapInputCleanedAndStripped.keySet()) {
                    String input = mapInputCleanedAndStripped.get(key);
                    if (mapResultLemmatization != null && mapResultLemmatization.containsKey(key)) {
                        String output = mapResultLemmatization.get(key);
                        dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().put(input, output);
                    } else {
                        dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().put(input, input);
                    }
                }
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    String stringifiedNgram = ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii);
                    String lemmatizedForm = dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().getOrDefault(stringifiedNgram, stringifiedNgram);
                    ngram.setOriginalFormLemmatized(lemmatizedForm);
                }
                clock.closeAndPrintClock();
            }

            /* stopwords removal - 2nd run on the lemmatized form this time
            
            the reason for this second run is that the lemmatization step has modified terms,
            
            hence creating new forms that might well be in the list of stopwords */
            message = "🧺 second round of stopwords removal";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            
            
            listOfnGramsGlobal = dm.getListOfnGramsGlobal();
            listOfnGramsGlobalMinusStopwords = new ArrayList();
            
            for (NGram ngram: listOfnGramsGlobal){
                String wordToTest = ngram.getOriginalFormLemmatized();
                boolean removal = false;
                for (StopWordsRemover swr : stopwordRemovers) {
                    boolean shouldNgramBeRemoved = swr.shouldItBeRemoved(wordToTest);
                    if (shouldNgramBeRemoved){
                        removal = true;
                        break;
                    }
                }
                if (!removal){
                    listOfnGramsGlobalMinusStopwords.add(ngram);
                }
            }
            dm.setListOfnGramsGlobal(listOfnGramsGlobalMinusStopwords);
            
            clock.closeAndPrintClock();

            /* REMOVE SMALL WORDS, NUMBERS */
            message = "✏️ removal of small words and digits";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram -> ngram.getOriginalFormLemmatized().length() >= minCharNumber && !ngram.getOriginalFormLemmatized().matches(".*\\d.*"))
                    .collect(Collectors.toList()));

            clock.closeAndPrintClock();

            /*
            DEDUPLICATING NGRAMS BECAUSE OF LEMMA / NON LEMMA VERSIONS
            COUNTING THEM IN A MAP
            REMOVING THOSE THAT APPEAR LESS THAN minTermFreq
            KEEPING ONLY THE N MOST FREQUENT
             */
            message = "🖩counting ngrams, merging the lemmatized and non lemmatized versions, keeping only the top terms";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

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
                    .filter(entry -> {
                        boolean keepIt = entry.getValue() >= minTermFreq;
                        return keepIt;
                    })
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(MOST_FREQUENT_TERMS)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            dm.setnGramsAndGlobalCount(topEntries);

            clock.closeAndPrintClock();

            /* COUNTING CO-OCCURRENCES  */
            message = "🧠 calculating cooccurrences";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            Multiset<Cooc> listCoocTotal = new Multiset();

            Map<String, NGram> cleanedAndStrippedToNgram = new HashMap();
            for (NGram ngram : dm.getnGramsAndGlobalCount().keySet()) {
                cleanedAndStrippedToNgram.put(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii), ngram);
            }

            Map<String, NGram> lemmatizedToNgram = new HashMap();
            for (NGram ngram : dm.getnGramsAndGlobalCount().keySet()) {
                lemmatizedToNgram.put(ngram.getOriginalFormLemmatized(), ngram);
            }

            dm.getCleanedAndStrippedNGramsPerLine().entrySet().forEach(entry -> {

                Set<String> cleanedAndStrippedStringifiedNGramsForOneLine = entry.getValue();
                /*
                the use of a concurrent hashmap is to make sure we retrieve all different ngrams in a given line
                using their lemmatized form as a key in the map helps ensure unicity
                 */
                Map<String, NGram> ngramsInCurrentLine = new HashMap();

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

                    // COOC CREATION FOR TERMS IN THE LINE
                    NGram arrayNGram[] = new NGram[ngramsInCurrentLine.size()];
                    List<Cooc> coocs;
                    try {
                        coocs = new PerformCombinationsOnNGrams(ngramsInCurrentLine.values().toArray(arrayNGram)).call();
                        listCoocTotal.addAllFromListOrSet(coocs);
                    } catch (InterruptedException | IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
            clock.closeAndPrintClock();

            /* REMOVING UNFREQUENT COOC  */
            message = "🧹 removing infrequent cooc";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            Integer nbCoocs = listCoocTotal.getSize();
            if (nbCoocs < 50_000) {
                minCoocFreq = 1;
            } else if (nbCoocs < 80_000) {
                minCoocFreq = 2;
            } else if (nbCoocs < 110_000) {
                minCoocFreq = 3;
            } else {
                minCoocFreq = 4;
            }
            List<Map.Entry<Cooc, Integer>> sortDesckeepAboveMinFreq = listCoocTotal.sortDesckeepAboveMinFreq(listCoocTotal, minCoocFreq);

            Multiset<String> coocsStringified = new Multiset();
            Multiset<String> nodesInEdgesStringified = new Multiset();
            Multiset<Cooc> coocs = new Multiset();
            for (Map.Entry<Cooc, Integer> entry : sortDesckeepAboveMinFreq) {
                coocsStringified.addSeveral(entry.getKey().toString(), entry.getValue());
                coocs.addSeveral(entry.getKey(), entry.getValue());
                nodesInEdgesStringified.addOne(entry.getKey().a.getOriginalFormLemmatized());
                nodesInEdgesStringified.addOne(entry.getKey().b.getOriginalFormLemmatized());
            }

            clock.closeAndPrintClock();

            // ADDING CHECKS FOR NETWORKS THAT ARE TOO BIG BECAUSE OF A HUGE NUMBER OF EDGES
            boolean tooManyEdges = false;
            int numberOfNodes = nodesInEdgesStringified.getElementSet().size();
            int numberOfEdges = coocsStringified.getElementSet().size();
            if (numberOfEdges > 50_000 && numberOfEdges > (numberOfNodes * 50)) {
                tooManyEdges = true;
            }
            if (tooManyEdges) {
                int maxNumberEdgesInt = numberOfNodes * 50;
                List<Map.Entry<Cooc, Integer>> sortDesckeepMostfrequent = coocs.sortDesckeepMostfrequent(coocs, maxNumberEdgesInt);
                coocs = new Multiset();
                nodesInEdgesStringified = new Multiset();
                for (Map.Entry<Cooc, Integer> entry : sortDesckeepMostfrequent) {
                    coocs.addSeveral(entry.getKey(), entry.getValue());
                    nodesInEdgesStringified.addOne(entry.getKey().a.getOriginalFormLemmatized());
                    nodesInEdgesStringified.addOne(entry.getKey().b.getOriginalFormLemmatized());
                }
            }

            /* CREATING GEPHI GRAPH  */
            message = "⚽ adding nodes to graph";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            Map<String, Node> nodesMap = new HashMap();

            // Create a new project model without affecting global state
            pc = Lookup.getDefault().lookup(ProjectController.class);
            workspace = pc.newWorkspace(pc.newProject());
            pc.openWorkspace(workspace);  // Open this specific workspace

            workspace.getProject().getProjectMetadata().setAuthor("created with nocodefunctions.com");
            String description
                    = "language: " + commaSeparatedLanguageCodes + "; lemmatization: " + lemmatize + "; minimum number of characters: "
                    + minCharNumber + "; minimunm cooccurrence frequency: " + minCoocFreq + "; type of correction: " + typeCorrection
                    + "; minimum term frequency: " + minTermFreq + "; use science stopwords: "
                    + isScientificCorpus + "; max length of ngrams: " + maxNGram + "; replace with own stopwords: " + replaceStopwords;
            workspace.getProject().getProjectMetadata().setDescription(description);

// Get graph model for THIS workspace specifically
            GraphModel gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);

// Clear any existing tables (just to be extra safe)
            gm.getGraph().clear();

            Column countTermsColumn = gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
            Column countPairsColumn = gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
            if (typeCorrection.equals("pmi")) {
                gm.getEdgeTable().addColumn("pmi", Float.TYPE);
            }
            GraphFactory factory = gm.factory();
            Graph graphResult = gm.getGraph();
            Set<Node> nodes = new HashSet();
            Node node;
            Set<String> elementSet = nodesInEdgesStringified.getElementSet();
            Set<String> removedNodes = new HashSet();

            for (Map.Entry<NGram, Long> entry : dm.getnGramsAndGlobalCount().entrySet()) {
                NGram ngram = entry.getKey();
                String nodeLabel = ngram.getOriginalFormLemmatized();

                // NOT INCLUDING ISOLATED NODES IN FINAL GRAPH
                if (!elementSet.contains(nodeLabel)) {
                    removedNodes.add(nodeLabel);
                    continue;
                }

                if (removeLeaves && nodesInEdgesStringified.getCount(nodeLabel) == 1) {
                    removedNodes.add(nodeLabel);
                    continue;
                }

                Integer count = entry.getValue().intValue();
                if (count < minTermFreq) {
                    System.out.println("error with term " + nodeLabel);
                    System.out.println("freq " + count);
                }
                node = factory.newNode(nodeLabel);

                node.setLabel(nodeLabel);
                node.setAttribute(countTermsColumn, count);
                nodes.add(node);
            }
            graphResult.addAllNodes(nodes);

            for (Node nodeInGraph : graphResult.getNodes()) {
                nodesMap.put((String) nodeInGraph.getId(), nodeInGraph);
            }
            clock.closeAndPrintClock();

            message = "🔗 adding edges to graph";
            clock = new Clock(message, silentClock);

            sendMessageBackHome(message);

            double maxValuePMI = 0.00001d;
            float maxValueCountEdges = 0;
            Map<String, Double> edgesAndTheirPMIWeightsBeforeRescaling = new HashMap();
            Set<Edge> edgesForGraph = new HashSet();
            /*

            Looping through all cooc to compute their PMI
            Also recording the highest PMI value for the rescaling of weights, later, from 0 to 10.

             */
            for (Cooc cooc : coocs.getElementSet()) {
                if (removedNodes.contains(cooc.a.getOriginalFormLemmatized()) || removedNodes.contains(cooc.b.getOriginalFormLemmatized())) {
                    continue;
                }
                Integer countEdge = coocsStringified.getCount(cooc.toString());
                Integer freqSource = dm.getnGramsAndGlobalCount().get(cooc.a).intValue();
                Integer freqTarget = dm.getnGramsAndGlobalCount().get(cooc.b).intValue();

                // THIS IS RECORDING THE HIGHEST PMI WEIGHTED EDGE
                double edgeWeightPMI = (double) countEdge / (freqSource * freqTarget);
                if (edgeWeightPMI > maxValuePMI) {
                    maxValuePMI = edgeWeightPMI;
                }

                edgesAndTheirPMIWeightsBeforeRescaling.put(cooc.toString(), edgeWeightPMI);

                // THIS IS RECORDING THE HIGHEST EDGE COUNT
                if (countEdge > maxValueCountEdges) {
                    maxValueCountEdges = countEdge;
                }
            }
            /* RESCALING EDGE WEIGHTS FROM 0 TO 10  AND ADDING THE EDGES TO THE GRAPH */
            Node nodeSource;
            Node nodeTarget;
            Edge edge;
            Integer countEdge;
            for (Cooc cooc : coocs.getElementSet()) {
                if (removedNodes.contains(cooc.a.getOriginalFormLemmatized()) || removedNodes.contains(cooc.b.getOriginalFormLemmatized())) {
                    continue;
                }
                countEdge = coocsStringified.getCount(cooc.toString());

                double edgeWeightRescaledToTen;
                double edgeWeightBeforeRescaling = 0;
                switch (typeCorrection) {
                    case "pmi":
                        edgeWeightBeforeRescaling = edgesAndTheirPMIWeightsBeforeRescaling.get(cooc.toString());
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValuePMI;
                        break;
                    case "none":
                        edgeWeightBeforeRescaling = countEdge.doubleValue();
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                        break;
                    default:
                        edgeWeightBeforeRescaling = countEdge.doubleValue();
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                }

                nodeSource = nodesMap.get(cooc.a.getOriginalFormLemmatized());
                nodeTarget = nodesMap.get(cooc.b.getOriginalFormLemmatized());
                edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                edge.setAttribute(countPairsColumn, countEdge);
                if (typeCorrection.equals("pmi")) {
                    float edgeWeightBeforeRescalingFloat = (float) edgeWeightBeforeRescaling;
                    edge.setAttribute("pmi", edgeWeightBeforeRescalingFloat);
                }
                edgesForGraph.add(edge);
            }
            graphResult.addAllEdges(edgesForGraph);
            clock.closeAndPrintClock();

            message = "🗺️ applying a Force Atlas mapping for 3 seconds";
            sendMessageBackHome(message);
            clock = new Clock(message, silentClock);
            apply_FA2_layout(gm);
            clock.closeAndPrintClock();

            message = "🛥️ exporting graph";
            clock = new Clock(message, silentClock);
            sendMessageBackHome(message);

            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
            ExporterGEXF exporterGexf = (ExporterGEXF) ec.getExporter("gexf");
            exporterGexf.setWorkspace(workspace);
            exporterGexf.setExportDynamic(false);
            exporterGexf.setExportPosition(true);
            exporterGexf.setExportSize(false);
            exporterGexf.setExportColors(false);
            exporterGexf.setExportMeta(true);
            StringWriter stringWriter = new StringWriter();
            ec.exportWriter(stringWriter, exporterGexf);
            stringWriter.close();
            String resultGexf = stringWriter.toString();
            clock.closeAndPrintClock();
            globalClock.closeAndPrintClock();
            return resultGexf;
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            if (workspace != null) {
                pc.closeCurrentWorkspace();
                pc.closeCurrentProject();
            }
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        System.out.println("error in cowo function");
        return "error in cowo function";

    }

    private void apply_FA2_layout(GraphModel gm) {
        ForceAtlas2Builder layoutBuilder = new ForceAtlas2Builder();
        ForceAtlas2 layout = new ForceAtlas2(layoutBuilder);
        layout.setGraphModel(gm);
        layout.resetPropertiesValues();

        int threads = Runtime.getRuntime().availableProcessors() * 2 - 1;

        System.out.println("threads being used: " + threads);

        layout.setScalingRatio(50d);
        layout.setThreadsCount(threads);
        layout.setAdjustSizes(Boolean.FALSE);
        layout.setJitterTolerance(2d);
        layout.setBarnesHutOptimize(Boolean.TRUE);
        layout.setBarnesHutTheta(2d);

        layout.initAlgo();
        int counterLoops = 1;
        long start = System.currentTimeMillis();
        for (int i = 0; layout.canAlgo(); i++) {
            layout.goAlgo();
            long now = System.currentTimeMillis();
            if ((now - start) / 1000 > 3) {
                break;
            }
        }
        layout.endAlgo();
    }
}

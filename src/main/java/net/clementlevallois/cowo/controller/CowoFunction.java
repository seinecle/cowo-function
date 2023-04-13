package net.clementlevallois.cowo.controller;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphFactory;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.plugin.ExporterGEXF;
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

    private Map<Integer, String> mapResultLemmatization;
    private boolean flattenToAScii = false;
    private Clock clock;
    private static boolean silentClock = true;

    public static void main(String[] args) throws IOException {
        CowoFunction cowo = new CowoFunction();
//        Path path = Path.of("G:\\Mon Drive\\Writing\\Article Umigon FR\\Article for RAM\\bibliometric analysis\\joint zotero plus wos\\20230412 - results - 1.txt");
        Path path = Path.of("C:\\Users\\levallois\\OneDrive - Aescra Emlyon Business School\\Bureau\\tests\\March 2021 tweet text_ March 21.txt");
        List<String> readAllLines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
        TreeMap<Integer, String> mapOfLines = new TreeMap();
        int i = 0;
        for (String line : readAllLines) {
            mapOfLines.put(i++, line);
        }
        silentClock = false;
        cowo.setFlattenToAScii(false);
        String lang = "en";
        int minCharNumber = 3;
        boolean replaceStopwords = false;
        boolean scientific = true;
        int minCooC = 2;
        int minTermFreq = 5;
        String correction = "pmi";
        int maxNGram = 4;
        boolean lemmatize = true;
        String analyze = cowo.analyze(mapOfLines, lang, new HashSet(), minCharNumber, replaceStopwords, scientific, minCooC, minTermFreq, correction, maxNGram, lemmatize);
        Files.writeString(Path.of("C:\\Users\\levallois\\OneDrive - Aescra Emlyon Business School\\Bureau\\tests\\cowo-tst.gexf"), analyze);
    }

    public void setFlattenToAScii(boolean flattenToAScii) {
        this.flattenToAScii = flattenToAScii;
    }

    public String analyze(TreeMap<Integer, String> mapOfLinesParam, String selectedLanguage, Set<String> userSuppliedStopwords, int minCharNumber, boolean replaceStopwords, boolean isScientificCorpus, int minCoocFreq, int minTermFreq, String typeCorrection, int maxNGram, boolean lemmatize) {
        ObjectOutputStream oos = null;
        DataManager dm = new DataManager();
        dm.setMapOfLines(mapOfLinesParam);
        try {
            Clock globalClock = new Clock("global clock", silentClock);
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            clock = new Clock("entering tokenization", silentClock);
            /* TOKENIZE */
            UmigonTokenizer.initialize();

            dm.getOriginalStringsPerLine().entrySet()
                    .parallelStream()
                    .forEach(entry -> {
                        List<TextFragment> tokenized = null;
                        try {
                            tokenized = UmigonTokenizer.tokenize(entry.getValue().toLowerCase(), new HashSet());
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        dm.getTextFragmentsPerLine().put(entry.getKey(), tokenized);
                    });

            clock.closeAndPrintClock();

            /* ADD NGRAMS */
            clock = new Clock("entering ngram addition", silentClock);

            for (Map.Entry<Integer, List<TextFragment>> entry : dm.getTextFragmentsPerLine().entrySet()) {
                Set<String> stringifiedNGrams = new HashSet();
                List<NGram> nGramsForOneLine = new ArrayList();
                List<SentenceLike> sentenceLikeFragments = SentenceLikeFragmentsDetector.returnSentenceLikeFragments(entry.getValue());
                for (SentenceLike sentenceLikeFragment : sentenceLikeFragments) {
                    List<NGram> ngrams = NGramFinderBisForTextFragments.generateNgramsUpto(sentenceLikeFragment.getNgrams(), maxNGram);
                    sentenceLikeFragment.setNgrams(ngrams);
                    nGramsForOneLine.addAll(ngrams);
                }
                /* removing ngrams that appeat several times in a given line.
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
            clock = new Clock("initializing the stopword removal", silentClock);
            Set<String> stopwords = Stopwords.getStopWords(selectedLanguage).get("long");
            StopWordsRemover stopWordsRemover = new StopWordsRemover(minCharNumber, selectedLanguage);
            if (userSuppliedStopwords != null && !userSuppliedStopwords.isEmpty()) {
                stopWordsRemover.useUSerSuppliedStopwords(userSuppliedStopwords, replaceStopwords);
            }
            if (isScientificCorpus) {
                if (selectedLanguage.equals("en")) {
                    Set<String> scientificStopwordsInEnglish = Stopwords.getScientificStopwordsInEnglish();
                    stopWordsRemover.addFieldSpecificStopWords(scientificStopwordsInEnglish);
                }
                if (selectedLanguage.equals("fr")) {
                    Set<String> scientificStopwordsInFrench = Stopwords.getScientificStopwordsInFrench();
                    stopWordsRemover.addFieldSpecificStopWords(scientificStopwordsInFrench);
                }
            }
            stopWordsRemover.addWordsToRemove(new HashSet());
            stopWordsRemover.addStopWordsToKeep(new HashSet());
            clock.closeAndPrintClock();

            /* REMOVE NGRAMS THAT OVERLAP WITH OTHERS WHILE BEING INFREQUENT */
            clock = new Clock("creating a multiset to remove redundant ngrams", silentClock);

            Multiset<String> multisetOfNGramsSoFarStringified = new Multiset();
            dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .map(ngram -> ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii))
                    .forEach(ngram -> multisetOfNGramsSoFarStringified.addOne(ngram));

            clock.closeAndPrintClock();

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

            clock = new Clock("doing the redundant ngrams removal", silentClock);
            NGramDuplicatesCleaner cleaner = new NGramDuplicatesCleaner(stopwords);
            Map<String, Integer> goodSetWithoutDuplicates = cleaner.removeDuplicates(multisetOfNGramsSoFarStringified.getInternalMap(), maxNGram, true);

            Set<String> stringsToKeep = goodSetWithoutDuplicates.keySet();
            dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram -> stringsToKeep.contains(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii)))
                    .collect(Collectors.toList()));

            clock.closeAndPrintClock();

            /* stopwords removal - run */
            clock = new Clock("removing stopwords (1)", silentClock);
            dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram
                            -> !stopWordsRemover.shouldItBeRemoved(ngram.getCleanedAndStrippedNgramIfCondition(true))
                    && !stopWordsRemover.shouldItBeRemoved(ngram.getCleanedAndStrippedNgramIfCondition(false)))
                    .collect(Collectors.toList()));

            clock.closeAndPrintClock();

            /* LEMMATIZATION */
            clock = new Clock("entering lemmatization", silentClock);
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

                if (selectedLanguage.equals("en") | selectedLanguage.equals("fr")) {

                    HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(data);

                    URI uri = new URI("http://localhost:7002/api/lemmatizer_light/map/" + selectedLanguage);

                    request = HttpRequest.newBuilder()
                            .POST(bodyPublisher)
                            .uri(uri)
                            .build();

                    CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
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
                        }
                    }
                    );
                    futures.add(future);
                    CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
                    combinedFuture.join();

                } else {
                    try {
                        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(data);

                        URI uri = new URI("http://localhost:7000/lemmatize/map/" + selectedLanguage);

                        request = HttpRequest.newBuilder()
                                .uri(uri)
                                .POST(bodyPublisher)
                                .build();
                        client = HttpClient.newHttpClient();

                        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        if (response.statusCode() == 200) {
                            byte[] body = response.body();
                            try (
                                    ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                                mapResultLemmatization = (Map<Integer, String>) ois.readObject();
                            } catch (IOException | ClassNotFoundException ex) {
                                System.out.println("error in deserialization of lemmatizer heavyweight multiset ngrams API result");
                            }
                        } else {
                            System.out.println("lemmatization heavyweight multiset ngrams returned by the API was not a 200 code");
                        }

                    } catch (URISyntaxException | IOException | InterruptedException ex) {
                        System.out.println("ex:" + ex.getMessage());
                    }
                }
                clock.closeAndPrintClock();

                clock = new Clock("finishing up lemmatization", silentClock);

                for (Integer key : mapInputCleanedAndStripped.keySet()) {
                    String input = mapInputCleanedAndStripped.get(key);
                    String output = mapResultLemmatization.get(key);
                    dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().put(input, output);
                }
                for (NGram ngram : dm.getListOfnGramsGlobal()) {
                    String stringifiedNgram = ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii);
                    String lemmatizedForm = dm.getStringifiedCleanedAndStrippedNGramToLemmatizedForm().getOrDefault(stringifiedNgram, stringifiedNgram);
                    ngram.setOriginalFormLemmatized(lemmatizedForm);
                }
                clock.closeAndPrintClock();
            }

            /* stopwords removal - 2nd run on the lemmatized form this time
            
            the reason for this second run is that the lemmarization step has modified terms,
            
            hence creating new forms that might well be in the list of stopwords */
            clock = new Clock("second round of stopwords removal", silentClock);

            dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram -> !stopWordsRemover.shouldItBeRemoved(ngram.getOriginalFormLemmatized()))
                    .collect(Collectors.toList()));

            clock.closeAndPrintClock();

            /* REMOVE SMALL WORDS, NUMBERS */
            clock = new Clock("removal of small words and digits", silentClock);

            dm.setListOfnGramsGlobal(dm.getListOfnGramsGlobal()
                    .parallelStream()
                    .filter(ngram -> ngram.getOriginalFormLemmatized().length() >= minCharNumber && !ngram.getOriginalFormLemmatized().matches(".*\\d.*"))
                    .collect(Collectors.toList()));

            clock.closeAndPrintClock();

            /* KEEP ONLY THE 2,000 MOST FREQUENT NGRAMS */
            clock = new Clock("keep only the most frequent terms", silentClock);
            Map<String, Long> countNGramsLemmatized = dm.getListOfnGramsGlobal().stream()
                    .map(NGram::getOriginalFormLemmatized)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            Set<String> topNames = countNGramsLemmatized.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(MOST_FREQUENT_TERMS)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            dm.setnGramsAndGlobalCount(dm.getListOfnGramsGlobal().stream()
                    .filter(ngram -> topNames.contains(ngram.getOriginalFormLemmatized()))
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
            clock.closeAndPrintClock();

            dm.getnGramsAndGlobalCount().entrySet().removeIf(entry -> entry.getValue() < minTermFreq);

            List<Cooc> listCoocForCurrentLine;
            List<Cooc> listCoocForCurrentLineCleaned;
            Multiset<Cooc> listCoocTotal = new Multiset();


            /* COUNTING CO-OCCURRENCES  */
            clock = new Clock("calculating cooccurrences", silentClock);

            for (Integer lineNumber : dm.getCleanedAndStrippedNGramsPerLine().keySet()) {

                Set<String> cleanedAndStrippedStringifiedNGramsForOneLine = dm.getCleanedAndStrippedNGramsPerLine().get(lineNumber);
                Queue<NGram> ngramsInCurrentLine = new ConcurrentLinkedQueue();

                dm.getnGramsAndGlobalCount().keySet().parallelStream()
                        .forEach(ngram -> {
                            if (cleanedAndStrippedStringifiedNGramsForOneLine.contains(ngram.getCleanedAndStrippedNgramIfCondition(flattenToAScii))) {
                                ngramsInCurrentLine.add(ngram);
                            } else if (cleanedAndStrippedStringifiedNGramsForOneLine.contains(ngram.getOriginalFormLemmatized())) {
                                ngramsInCurrentLine.add(ngram);
                            }
                        });

                if (ngramsInCurrentLine.size() < 2) {
                    continue;
                }

                // COOC CREATION FOR TERMS IN THE LINE
                // ALSO HANDLING A WEIRD TF IDF WEIGHTING FOR THE EDGES
                NGram arrayNGram[] = new NGram[ngramsInCurrentLine.size()];
                listCoocForCurrentLine = new ArrayList();
                listCoocForCurrentLineCleaned = new ArrayList();
                List<Cooc> coocs = new PerformCombinationsOnNGrams(ngramsInCurrentLine.toArray(arrayNGram)).call();
                listCoocForCurrentLine.addAll(coocs);
                for (Cooc cooc : listCoocForCurrentLine) {
                    if (cooc.getA() != null && cooc.getB() != null
                            && !cooc.getA().getOriginalFormLemmatized().equals(cooc.getB().getOriginalFormLemmatized())
                            && !cooc.getA().getOriginalFormLemmatized().contains(cooc.getB().getOriginalFormLemmatized())
                            && !cooc.getB().getOriginalFormLemmatized().contains(cooc.getA().getOriginalFormLemmatized())) {

                        listCoocForCurrentLineCleaned.add(cooc);
                    }
                }

                listCoocTotal.addAllFromListOrSet(listCoocForCurrentLineCleaned);
            }
            clock.closeAndPrintClock();

            /* REMOVING UNFREQUENT COOC  */
            clock = new Clock("removing infrequent cooc", silentClock);
            if (listCoocTotal.getSize() < 50_000) {
                minCoocFreq = 2;
            } else if (listCoocTotal.getSize() < 80_000) {
                minCoocFreq = 3;
            } else if (listCoocTotal.getSize() < 110_000) {
                minCoocFreq = 4;
            } else {
                minCoocFreq = 5;
            }
            List<Map.Entry<Cooc, Integer>> sortDesckeepAboveMinFreq = listCoocTotal.sortDesckeepAboveMinFreq(listCoocTotal, minOcc);

            Multiset<String> coocsStringified = new Multiset();
            Multiset<Cooc> coocs = new Multiset();
            for (Map.Entry<Cooc, Integer> entry : sortDesckeepAboveMinFreq) {
                coocsStringified.addOne(entry.getKey().toString());
                coocs.addSeveral(entry.getKey(),entry.getValue());
            }


            clock.closeAndPrintClock();

            /* CREATING GEPHI GRAPH  */
            clock = new Clock("adding nodes to graph", silentClock);

            Map<String, Node> nodesMap = new HashMap();

            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            Workspace workspace = pc.getCurrentWorkspace();
            workspace.getProject().getProjectMetadata().setAuthor("created with nocodefunctions.com");
            String description
                    = "language: " + selectedLanguage + "; lemmatization: " + lemmatize + "; minimum number of characters: "
                    + minCharNumber + "; minimunm cooccurrence frequency: " + minCoocFreq + "; type of correction: " + typeCorrection
                    + "; minimum term frequency: " + minTermFreq + "; use science stopwords: "
                    + isScientificCorpus + "; max length of ngrams: " + maxNGram + "; replace with own stopwords: " + replaceStopwords;
            workspace.getProject().getProjectMetadata().setDescription(description);
            GraphModel gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
            Column countTermsColumn = gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
            Column countPairsColumn = gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
            gm.getEdgeTable().addColumn("countPairsWithPMI", Float.TYPE);
            GraphFactory factory = gm.factory();
            Graph graphResult = gm.getGraph();
            Set<Node> nodes = new HashSet();
            Node node;
            for (Map.Entry<NGram, Long> entry : dm.getnGramsAndGlobalCount().entrySet()) {
                NGram ngram = entry.getKey();
                Integer count = entry.getValue().intValue();
                if (count < minTermFreq) {
                    System.out.println("error with term " + ngram.getCleanedAndStrippedNgram());
                    System.out.println("freq " + count);
                }
                node = factory.newNode(ngram.getOriginalFormLemmatized());
                node.setLabel(ngram.getOriginalFormLemmatized());
                node.setAttribute(countTermsColumn, count);
                nodes.add(node);
            }
            graphResult.addAllNodes(nodes);

            for (Node nodeInGraph : graphResult.getNodes()) {
                nodesMap.put((String) nodeInGraph.getId(), nodeInGraph);
            }
            clock.closeAndPrintClock();
            clock = new Clock("adding edges to graph", silentClock);

            double maxValuePMI = 0.00001d;
            float maxValueCountEdges = 0;
            Map<String, Double> edgesAndTheirPMIWeightsBeforeRescaling = new HashMap();
            Set<Edge> edgesForGraph = new HashSet();
            /*

            Looping through all cooc to compute their PMI
            Also recording the highest PMI value for the rescaling of weights, later, from 0 to 10.
            Also recording the highest TF IDF value for the rescaling of weights, later, from 0 to 10.

             */
            for (Cooc cooc : coocs.getElementSet()) {
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
            /* RESCALING EDGE WEIGHTS FROM 0 TO 10  */
            for (Cooc cooc : coocs.getElementSet()) {
                Integer countEdge = coocsStringified.getCount(cooc.toString());

                double edgeWeightRescaledToTen = 0;

                switch (typeCorrection) {
                    case "pmi" -> {
                        double edgeWeightBeforeRescaling = edgesAndTheirPMIWeightsBeforeRescaling.get(cooc.toString());
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValuePMI;
                    }
                    case "none" -> {
                        double edgeWeightBeforeRescaling = countEdge.doubleValue();
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                    }
                    default -> {
                        double edgeWeightBeforeRescaling = countEdge.doubleValue();
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                    }
                }

                Node nodeSource = nodesMap.get(cooc.a.getOriginalFormLemmatized());
                Node nodeTarget = nodesMap.get(cooc.b.getOriginalFormLemmatized());
                Edge edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                edge.setAttribute(countPairsColumn, countEdge);
                edgesForGraph.add(edge);
            }
            graphResult.addAllEdges(edgesForGraph);
            clock.closeAndPrintClock();
            clock = new Clock("exporting graph", silentClock);

            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
            ExporterGEXF exporterGexf = (ExporterGEXF) ec.getExporter("gexf");
            exporterGexf.setWorkspace(workspace);
            exporterGexf.setExportDynamic(
                    false);
            exporterGexf.setExportPosition(
                    false);
            exporterGexf.setExportSize(
                    false);
            exporterGexf.setExportColors(
                    false);
            exporterGexf.setExportMeta(true);
            StringWriter stringWriter = new StringWriter();
            ec.exportWriter(stringWriter, exporterGexf);
            stringWriter.close();
            String resultGexf = stringWriter.toString();
            clock.closeAndPrintClock();
            globalClock.closeAndPrintClock();
            return resultGexf;
        } catch (URISyntaxException | InterruptedException | IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
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
}

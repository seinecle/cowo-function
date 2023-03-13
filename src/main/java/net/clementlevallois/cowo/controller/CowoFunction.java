/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
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

    private TreeMap<Integer, String> mapOfLines;
    private TreeMap<Integer, List<TextFragment>> mapOfTextFragments = new TreeMap();
    private TreeMap<Integer, List<NGram>> mapOfNGrams = new TreeMap();
//    private TreeMap<Integer, List<SentenceLike>> mapOfSentenceLikeFragments = new TreeMap();
    private TreeMap<Integer, Set<String>> mapOfNGramsStringifiedCleanedForm = new TreeMap();
    private List<NGram> listOfnGramsGlobal = new ArrayList();
    private Multiset<String> nGramsGlobalCleanedLemmatized = new Multiset();
    private Multiset<String> nGramsGlobalCleanedNonLemmatized = new Multiset();
    private Map<Integer, String> mapResultLemmatization;

    public static void main(String[] args) throws IOException {
        CowoFunction cowo = new CowoFunction();
        Path path = Path.of("G:\\Mon Drive\\Writing\\Article Umigon FR\\Article for RAM\\bibliometric analysis\\analyses\\selected abstracts from biz fields.txt");
        List<String> readAllLines = Files.readAllLines(path);
        TreeMap<Integer, String> mapOfLines = new TreeMap();
        int i = 0;
        for (String line : readAllLines) {
            mapOfLines.put(i++, line);
        }
        cowo.analyze(mapOfLines, "en", new HashSet(), 2, false, true, 2, 2, "none", 4);
    }

    public String analyze(TreeMap<Integer, String> mapOfLinesParam, String selectedLanguage, Set<String> userSuppliedStopwords, int minCharNumber, boolean replaceStopwords, boolean isScientificCorpus, int minCoocFreq, int minTermFreq, String typeCorrection, int maxNGram) {
        ObjectOutputStream oos = null;
        try {
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            this.mapOfLines = mapOfLinesParam;

            /* TOKENIZE */
            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                List<TextFragment> tokenized;
                try {
                    tokenized = UmigonTokenizer.tokenize(entry.getValue().toLowerCase(), new HashSet());
                    mapOfTextFragments.put(entry.getKey(), tokenized);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            /* ADD NGRAMS */
            for (Map.Entry<Integer, List<TextFragment>> entry : mapOfTextFragments.entrySet()) {
                Set<String> stringifiedNGrams = new HashSet();
                List<NGram> nGramsForOneLine = new ArrayList();
                List<SentenceLike> sentenceLikeFragments = SentenceLikeFragmentsDetector.returnSentenceLikeFragments(entry.getValue());
                for (SentenceLike sentenceLikeFragment : sentenceLikeFragments) {
                    List<NGram> ngrams = NGramFinderBisForTextFragments.generateNgramsUpto(sentenceLikeFragment.getNgrams(), maxNGram);
                    sentenceLikeFragment.setNgrams(ngrams);
                    nGramsForOneLine.addAll(ngrams);
                }
                // removing duplicate ngrams for this given line. Better to avoid that a single line gets a lot of weight just because it mentions some terms repeatedely
                Set<String> cleanedNGramsForUniquenessTest = new HashSet();
                List<NGram> ngramsUniqueForOneLine = new ArrayList();
                for (NGram ngram : nGramsForOneLine) {
                    // using the property that when we add an element to a set, it returns true only if the element was not already present.
                    // convenient to test uniqueness
                    if (cleanedNGramsForUniquenessTest.add(ngram.getCleanedAndStrippedNgram())) {
                        listOfnGramsGlobal.add(ngram);
                        ngramsUniqueForOneLine.add(ngram);
                        stringifiedNGrams.add(ngram.getCleanedAndStrippedNgram());
                    }
                }
                mapOfNGrams.put(entry.getKey(), ngramsUniqueForOneLine);
                mapOfNGramsStringifiedCleanedForm.put(entry.getKey(), stringifiedNGrams);
            }

            /* REMOVE STOPWORDS */
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
            Iterator<NGram> iteratorGlobalNGrams = listOfnGramsGlobal.iterator();
            while (iteratorGlobalNGrams.hasNext()) {
                NGram nextNgram = iteratorGlobalNGrams.next();
                String nonCleanedAndStrippedNgram = nextNgram.getCleanedAndStrippedNgramIfCondition(false);
                if (stopWordsRemover.shouldItBeRemoved(nonCleanedAndStrippedNgram)) {
                    iteratorGlobalNGrams.remove();
                } else {
                    String cleanedAndStrippedNgram = nextNgram.getCleanedAndStrippedNgramIfCondition(true);
                    if (stopWordsRemover.shouldItBeRemoved(cleanedAndStrippedNgram)) {
                        iteratorGlobalNGrams.remove();
                    }
                }
            }
            Multiset<String> multisetOfNGramsSoFarStringifiedAsCleaned = new Multiset();
            for (NGram ngram : listOfnGramsGlobal) {
                multisetOfNGramsSoFarStringifiedAsCleaned.addOne(ngram.getCleanedAndStrippedNgram());
            }
            NGramDuplicatesCleaner cleaner = new NGramDuplicatesCleaner(stopwords);
            Map<String, Integer> goodSetWithoutDuplicates = cleaner.removeDuplicates(multisetOfNGramsSoFarStringifiedAsCleaned.getInternalMap(), maxNGram, true);

            Set<String> stringsToKeep = goodSetWithoutDuplicates.keySet();
            iteratorGlobalNGrams = listOfnGramsGlobal.iterator();
            while (iteratorGlobalNGrams.hasNext()) {
                NGram next = iteratorGlobalNGrams.next();
                if (!stringsToKeep.contains(next.getCleanedAndStrippedNgram())) {
                    iteratorGlobalNGrams.remove();
                }
            }
            /* LEMMATIZATION */
            Map<String, String> cleanAndStrippedToLemmatizedForm = new HashMap();
            Map<Integer, String> mapInput = new HashMap();
            Set<String> candidatesToLemmatization = new HashSet();
            for (NGram ngram : listOfnGramsGlobal) {
                candidatesToLemmatization.add(ngram.getCleanedAndStrippedNgram());
            }
            int i = 0;
            for (String string : candidatesToLemmatization) {
                mapInput.put(i++, string);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(mapInput);
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

                    URI uri = new URI("http://localhost:7000/lemmatize/multiset/ngrams/" + selectedLanguage);

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
            for (Integer key : mapInput.keySet()) {
                String input = mapInput.get(key);
                String output = mapResultLemmatization.get(key);
                if (!input.equalsIgnoreCase(output)) {
                    cleanAndStrippedToLemmatizedForm.put(input, output);
                }
            }
            for (NGram ngram : listOfnGramsGlobal) {
                String lemmatizedForm = cleanAndStrippedToLemmatizedForm.getOrDefault(ngram.getCleanedAndStrippedNgram(), ngram.getCleanedAndStrippedNgram());
                ngram.setOriginalFormLemmatized(lemmatizedForm);
            }
            /* REMOVE SMALL WORDS, NUMBERS A*/
            iteratorGlobalNGrams = listOfnGramsGlobal.iterator();
            while (iteratorGlobalNGrams.hasNext()) {
                String string = iteratorGlobalNGrams.next().getOriginalFormLemmatized();
                if (string.length() < minCharNumber | string.matches(".*\\d.*")) {
                    iteratorGlobalNGrams.remove();
                }
            }
            /* KEEP ONLY THE MOST FREQUENT WORDS (2000 most frequent is the default) AND THOSE FREEQUENT AT LEAST N TIMES*/
            Multiset<String> lemmatizedForms = new Multiset();
            for (NGram ngram : listOfnGramsGlobal) {
                lemmatizedForms.addOne(ngram.getOriginalFormLemmatized());
            }
            lemmatizedForms = lemmatizedForms.keepMostfrequent(lemmatizedForms, MOST_FREQUENT_TERMS);
            iteratorGlobalNGrams = listOfnGramsGlobal.iterator();
            while (iteratorGlobalNGrams.hasNext()) {
                NGram nextNGram = iteratorGlobalNGrams.next();
                if (!lemmatizedForms.getElementSet().contains(nextNGram.getOriginalFormLemmatized())) {
                    iteratorGlobalNGrams.remove();
                } else {
                    if (lemmatizedForms.getCount(nextNGram.getOriginalFormLemmatized()) < minTermFreq) {
                        iteratorGlobalNGrams.remove();
                    }
                }
            }
            /* CREATE FINAL MULTISETS OF NGRAMS IN CLEANED FORM AND IN CLEANED AND LEMMATIZED FORMS */
            Multiset<String> finalcleanedNGramsMultiset = new Multiset();
            Multiset<String> finalcleanedLemmatizedNGramsMultiset = new Multiset();
            Map<String, NGram> mappingCleanedFormToNGram = new HashMap();
            for (NGram ngram : listOfnGramsGlobal) {
                finalcleanedNGramsMultiset.addOne(ngram.getCleanedAndStrippedNgram());
                finalcleanedLemmatizedNGramsMultiset.addOne(ngram.getOriginalFormLemmatized());
                mappingCleanedFormToNGram.put(ngram.getCleanedAndStrippedNgram(), ngram);
            }
            List<NGram> ngramsInCurrentLine;
            Multiset<String> ngramsInCurrentLineCleanedFormMultiset = new Multiset();
            List<Cooc> listCoocForCurrentLine;
            List<Cooc> listCoocForCurrentLineCleaned;
            List<Cooc> listCoocTotal = new ArrayList();
            Map<String, Integer> countOfDocsContainingAGivenTerm = new HashMap();
            Map<String, Float> tfidfForAnEdge = new HashMap();
            int nbLines = mapOfLines.size();
            /* COUNTING NB OF LINES A SPECIFIC TERM APPEARS IN  */
 /* needed for the TF IDF calculation  */
            for (Integer lineNumber : mapOfNGramsStringifiedCleanedForm.keySet()) {

                Set<String> stringifiedNGramsForOneLine = mapOfNGramsStringifiedCleanedForm.get(lineNumber);

                for (String cleanedNgram : finalcleanedNGramsMultiset.getElementSet()) {
                    if (stringifiedNGramsForOneLine.contains(cleanedNgram)) {
                        Integer countOfLinesContainingThisTerm = countOfDocsContainingAGivenTerm.getOrDefault(cleanedNgram, 0);
                        countOfDocsContainingAGivenTerm.put(cleanedNgram, countOfLinesContainingThisTerm + 1);
                    }
                }
            }
            /* COUNTING CO-OCCURRENCES  */
 /* IT INCLUDES THE CALCLUS OF TF IDF FOR EACH EDGE  */
            for (Integer lineNumber : mapOfNGramsStringifiedCleanedForm.keySet()) {

                Set<String> stringifiedCleanedNGramsForOneLine = mapOfNGramsStringifiedCleanedForm.get(lineNumber);

                ngramsInCurrentLine = new ArrayList();
                int nbTermsInLine = 0;

                for (String cleanedNgram : finalcleanedNGramsMultiset.getElementSet()) {
                    if (stringifiedCleanedNGramsForOneLine.contains(cleanedNgram)) {
                        NGram associatedNgram = mappingCleanedFormToNGram.get(cleanedNgram);
                        ngramsInCurrentLine.add(associatedNgram);
                        ngramsInCurrentLineCleanedFormMultiset.addOne(cleanedNgram);
                        nbTermsInLine++;
                    }
                }

                if (ngramsInCurrentLine.size() < 2) {
                    continue;
                }

                // COOC CREATION FOR TERMS IN THE LINE
                // ALSO HANDLING TF IDF WEIGHTING
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

                        Integer countTermAInCurrLine = ngramsInCurrentLineCleanedFormMultiset.getCount(cooc.getA().getCleanedAndStrippedNgram());
                        Integer countTermBInCurrLine = ngramsInCurrentLineCleanedFormMultiset.getCount(cooc.getB().getCleanedAndStrippedNgram());
                        Integer countLinesContainingTermA = countOfDocsContainingAGivenTerm.get(cooc.getA().getCleanedAndStrippedNgram());
                        Integer countLinesContainingTermB = countOfDocsContainingAGivenTerm.get(cooc.getB().getCleanedAndStrippedNgram());

                        float tfidfTermA = countTermAInCurrLine / nbTermsInLine * (float) Math.log(nbLines / countLinesContainingTermA);
                        float tfidfTermB = countTermBInCurrLine / nbTermsInLine * (float) Math.log(nbLines / countLinesContainingTermB);

                        float edgeWeightWithTFIDFForThisCoocForCurrLine = tfidfTermA + tfidfTermB;

                        Float weightTFIDFForThisCooc = tfidfForAnEdge.get(cooc.toString());
                        if (weightTFIDFForThisCooc == null) {
                            tfidfForAnEdge.put(cooc.toString(), edgeWeightWithTFIDFForThisCoocForCurrLine);
                        } else {
                            tfidfForAnEdge.put(cooc.toString(), weightTFIDFForThisCooc + edgeWeightWithTFIDFForThisCoocForCurrLine);
                        }
                        listCoocForCurrentLineCleaned.add(cooc);

                    }
                }

                listCoocTotal.addAll(listCoocForCurrentLineCleaned);

            }
            /* REMOVING UNFREQUENT COOC  */
            Multiset<String> coocStringified = new Multiset();
            for (Cooc cooc : listCoocTotal) {
                coocStringified.addOne(cooc.toString());
            }
            Iterator<Cooc> iteratorCooc = listCoocTotal.iterator();
            while (iteratorCooc.hasNext()) {
                Cooc nextCooc = iteratorCooc.next();
                if (coocStringified.getCount(nextCooc.toString()) < minCoocFreq) {
                    iteratorCooc.remove();
                }
            }
            /* CREATING GEPHI GRAPH  */
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            Workspace workspace = pc.getCurrentWorkspace();
            GraphModel gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
            Column countTermsColumn = gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
            Column countPairsColumn = gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
            gm.getEdgeTable().addColumn("countPairsWithPMI", Float.TYPE);
            GraphFactory factory = gm.factory();
            Graph graphResult = gm.getGraph();
            Set<Node> nodes = new HashSet();
            Node node;
            for (String finalNodeLabel : finalcleanedLemmatizedNGramsMultiset.getElementSet()) {
                node = factory.newNode(finalNodeLabel);
                node.setLabel(finalNodeLabel);
                node.setAttribute(countTermsColumn, finalcleanedLemmatizedNGramsMultiset.getCount(finalNodeLabel));
                nodes.add(node);
            }
            graphResult.addAllNodes(nodes);
            ////this is to rescale weights from 0 to 10 and apply PMI
//            List<Map.Entry<String, Integer>> sortDesc = setCombinationsTotal.sortDesc(setCombinationsTotal);
//            if (sortDesc.isEmpty()) {
//                return "";
//            }
//            String[] pairEdgeMostOccurrences = sortDesc.get(0).getKey().split(",");
//            Integer countEdgeMax = sortDesc.get(0).getValue();
//            Integer weightSourceOfEdgeMaxCooc = freqNGramsGlobal.getCount(pairEdgeMostOccurrences[0]);
//            Integer weightTargetOfEdgeMaxCooc = freqNGramsGlobal.getCount(pairEdgeMostOccurrences[1]);
            double maxValuePMI = 0.00001d;
            float maxValueTFIDF = 0.000001f;
            float maxValueCountEdges = 0;
            Map<String, Double> edgesAndTheirPMIWeightsBeforeRescaling = new HashMap();
            Set<Edge> edgesForGraph = new HashSet();
            /*

Looping through all cooc to compute their PMI
Also recording the highest PMI value for the rescaling of weights, later, from 0 to 10.
Also recording the highest TF IDF value for the rescaling of weights, later, from 0 to 10.

             */
            for (String coocStringi3fied : coocStringified.getElementSet()) {
                Integer countEdge = coocStringified.getCount(coocStringi3fied);
                String[] summits = coocStringi3fied.split("\\{--\\}");
                Integer freqSource = finalcleanedLemmatizedNGramsMultiset.getCount(summits[0]);
                Integer freqTarget = finalcleanedLemmatizedNGramsMultiset.getCount(summits[1]);

                // THIS IS RECODING THE HIGHEST PMI WEIGHTED EDGE
                double edgeWeightPMI = (double) countEdge / (freqSource * freqTarget);
                if (edgeWeightPMI > maxValuePMI) {
                    maxValuePMI = edgeWeightPMI;
                }
                edgesAndTheirPMIWeightsBeforeRescaling.put(coocStringi3fied, edgeWeightPMI);

                // THIS IS RECORDING THE HIGHEST TFIDF WEIGHTED EDGE
                Float tfidfForThisEdge = tfidfForAnEdge.get(coocStringi3fied);
                if (tfidfForThisEdge > maxValueTFIDF) {
                    maxValueTFIDF = tfidfForThisEdge;
                }

                // THIS IS RECORDING THE HIGHEST EDGE COUNT
                if (countEdge > maxValueCountEdges) {
                    maxValueCountEdges = countEdge;
                }
            }
            /* RESCALING EDGE WEIGHTS FROM 0 TO 10  */
            for (String coocStringi3fied : coocStringified.getElementSet()) {
                Integer countEdge = coocStringified.getCount(coocStringi3fied);
                String[] summits = coocStringi3fied.split("\\{--\\}");

                double edgeWeightRescaledToTen = 0;

                switch (typeCorrection) {
                    case "pmi" -> {
                        double edgeWeightBeforeRescaling = edgesAndTheirPMIWeightsBeforeRescaling.get(coocStringi3fied);
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValuePMI;
                    }
                    case "tfidf" -> {
                        double edgeWeightBeforeRescaling = tfidfForAnEdge.get(coocStringi3fied);
                        edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueTFIDF;
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

                Node nodeSource = graphResult.getNode(summits[0]);
                Node nodeTarget = graphResult.getNode(summits[1]);
                Edge edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                edge.setAttribute(countPairsColumn, countEdge);

                edgesForGraph.add(edge);
            }
            graphResult.addAllEdges(edgesForGraph);
//removing nodes (terms) that have zero connection
            Iterator<Node> iterator = graphResult.getNodes().toCollection().iterator();
            Set<Node> nodesToRemove = new HashSet();
            while (iterator.hasNext()) {
                Node next = iterator.next();
                if (graphResult.getNeighbors(next).toCollection().isEmpty()) {
                    nodesToRemove.add(next);
                }
            }
            graphResult.removeAllNodes(nodesToRemove);
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
            StringWriter stringWriter = new StringWriter();
            ec.exportWriter(stringWriter, exporterGexf);
            stringWriter.close();
            return stringWriter.toString();
        } catch (IOException | URISyntaxException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            try {
                oos.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        System.out.println("error in cowo function");
        return "error in cowo function";

    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package net.clementlevallois.cowo.controller;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.clementlevallois.stopwords.StopWordsRemover;
import net.clementlevallois.stopwords.Stopwords;
import net.clementlevallois.umigon.ngram.ops.NGramDuplicatesCleaner;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.PerformCombinations;
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
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class CowoFunction {

    private final int MOST_FREQUENT_TERMS = 2_000;

    public static void main(String[] args) {
        System.out.println("Hello World!");
    }

    public String analyze(TreeMap<Integer, String> mapOfLines, String selectedLanguage, Set<String> userSuppliedStopwords, int minCharNumber, boolean replaceStopwords, boolean isScientificCorpus, int minCoocFreq, int minTermFreq, String typeCorrection) {
        try {

            /* EXTRACT NGRAMS */
            TextOps textOps = new TextOps();
            Multiset<String> freqNGramsGlobal = textOps.extractNGramsFromMapOfLines(mapOfLines);

            Set<String> stopwords = Stopwords.getStopWords(selectedLanguage).get("long");
            NGramDuplicatesCleaner cleaner = new NGramDuplicatesCleaner(stopwords);
            Map<String, Integer> removeDuplicates = cleaner.removeDuplicates(freqNGramsGlobal.getInternalMap(), 4, true);
            freqNGramsGlobal.setInternalMap(removeDuplicates);


            /* REMOVE STOPWORDS */
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

            Iterator<Map.Entry<String, Integer>> it = freqNGramsGlobal.getInternalMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> next = it.next();
                if (stopWordsRemover.shouldItBeRemoved(next.getKey())) {
                    it.remove();
                }
            }

            /* REMOVE SMALL WORDS AND NUMBERS */
            Iterator<String> itNGrams = freqNGramsGlobal.getElementSet().iterator();
            while (itNGrams.hasNext()) {
                String string = itNGrams.next();
                if (string.length() < minCharNumber | string.matches(".*\\d.*")) {
                    itNGrams.remove();
                }
            }

            /* KEEP ONLY THE MOST FREQUENT WORDS (2000 most frequent is the default) */
            freqNGramsGlobal = freqNGramsGlobal.keepMostfrequent(freqNGramsGlobal, MOST_FREQUENT_TERMS);

            Set<String> ngramsInCurrentLine;
            Multiset<String> ngramsInCurrentLineMultiset = new Multiset();
            Set<String> setCoocForCurrentLine;
            Set<String> setCoocForCurrentLineCleaned;
            Multiset<String> setCoocTotal = new Multiset();
            Map<String, Integer> countOfDocsContainingAGivenTerm = new HashMap();

            Map<String, Float> tfidfForAnEdge = new HashMap();

            int nbLines = mapOfLines.size();


            /* COUNTING NB OF LINES A SPECIFIC TERM APPEARS IN  */
 /* needed for the TF IDF calculation  */
            for (Integer lineNumber : mapOfLines.keySet()) {

                String line = mapOfLines.get(lineNumber);

                for (String term : freqNGramsGlobal.getElementSet()) {
                    if (line.contains(term)) {
                        Integer countOfLinesContainingThisTerm = countOfDocsContainingAGivenTerm.get(term);
                        if (countOfLinesContainingThisTerm == null) {
                            countOfDocsContainingAGivenTerm.put(term, 1);
                        } else {
                            countOfDocsContainingAGivenTerm.put(term, countOfLinesContainingThisTerm + 1);
                        }
                    }
                }
            }

            /* COUNTING CO-OCCURRENCES  */
 /* IT INCLUDES THE CALCLUS OF TF IDF FOR EACH EDGE  */
            for (Integer lineNumber : mapOfLines.keySet()) {

                String line = mapOfLines.get(lineNumber);
                ngramsInCurrentLine = new HashSet();
                int nbTermsInLine = 0;

                for (String term : freqNGramsGlobal.getElementSet()) {
                    if (line.contains(term)) {
                        ngramsInCurrentLine.add(term);
                        ngramsInCurrentLineMultiset.addOne(term);
                        nbTermsInLine++;
                    }
                }

                if (ngramsInCurrentLine.size() < 2) {
                    continue;
                }

                // COOC CREATION FOR TERMS IN THE LINE
                // ALSO HANDLING TF IDF WEIGHTING
                String arrayWords[] = new String[ngramsInCurrentLine.size()];
                setCoocForCurrentLine = new HashSet();
                setCoocForCurrentLineCleaned = new HashSet();
                setCoocForCurrentLine.addAll(new PerformCombinations(ngramsInCurrentLine.toArray(arrayWords)).call());
                for (String pairOcc : setCoocForCurrentLine) {
                    String[] pair = pairOcc.split(",");

                    if (pair.length == 2
                            & !pair[0].trim().equals(pair[1].trim())
                            & !pair[0].contains(pair[1])
                            & !pair[1].contains(pair[0])) {

                        Integer countTermAInCurrLine = ngramsInCurrentLineMultiset.getCount(pair[0]);
                        Integer countTermBInCurrLine = ngramsInCurrentLineMultiset.getCount(pair[1]);
                        Integer countLinesContainingTermA = countOfDocsContainingAGivenTerm.get(pair[0]);
                        Integer countLinesContainingTermB = countOfDocsContainingAGivenTerm.get(pair[1]);

                        float tfidfTermA = countTermAInCurrLine / nbTermsInLine * (float) Math.log(nbLines / countLinesContainingTermA);
                        float tfidfTermB = countTermBInCurrLine / nbTermsInLine * (float) Math.log(nbLines / countLinesContainingTermB);

                        float edgeWeightWithTFIDFForThisCoocForCurrLine = tfidfTermA + tfidfTermB;

                        Float weightTFIDFForThisCooc = tfidfForAnEdge.get(pairOcc);
                        if (weightTFIDFForThisCooc == null) {
                            tfidfForAnEdge.put(pairOcc, edgeWeightWithTFIDFForThisCoocForCurrLine);
                        } else {
                            tfidfForAnEdge.put(pairOcc, weightTFIDFForThisCooc + edgeWeightWithTFIDFForThisCoocForCurrLine);
                        }
                        setCoocForCurrentLineCleaned.add(pairOcc);

                    }
                }

                setCoocTotal.addAllFromListOrSet(setCoocForCurrentLineCleaned);

            }

            /* REMOVING UNFREQUENT COOC  */
            List<Map.Entry<String, Integer>> sortDesckeepAboveMinFreq = setCoocTotal.sortDesckeepAboveMinFreq(setCoocTotal, minCoocFreq);
            Multiset<String> temp = new Multiset();
            for (Map.Entry entry : sortDesckeepAboveMinFreq) {
                temp.addSeveral((String) entry.getKey(), (Integer) entry.getValue());
            }
            setCoocTotal = new Multiset();
            setCoocTotal.addAllFromMultiset(temp);

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
            for (Map.Entry<String, Integer> entry : freqNGramsGlobal.getInternalMap().entrySet()) {
                node = factory.newNode(entry.getKey());
                node.setLabel(entry.getKey());
                node.setAttribute(countTermsColumn, entry.getValue());
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
            for (String edgeToCreate : setCoocTotal.getElementSet()) {
                String[] pair = edgeToCreate.split(",");
                Integer countEdge = setCoocTotal.getCount(edgeToCreate);
                Integer freqSource = freqNGramsGlobal.getCount(pair[0]);
                Integer freqTarget = freqNGramsGlobal.getCount(pair[1]);

                // THIS IS RECODING THE HIGHEST PMI WEIGHTED EDGE
                double edgeWeightPMI = (double) countEdge / (freqSource * freqTarget);
                if (edgeWeightPMI > maxValuePMI) {
                    maxValuePMI = edgeWeightPMI;
                }
                edgesAndTheirPMIWeightsBeforeRescaling.put(edgeToCreate, edgeWeightPMI);

                // THIS IS RECORDING THE HIGHEST TFIDF WEIGHTED EDGE
                Float tfidfForThisEdge = tfidfForAnEdge.get(edgeToCreate);
                if (tfidfForThisEdge > maxValueTFIDF) {
                    maxValueTFIDF = tfidfForThisEdge;
                }

                // THIS IS RECORDING THE HIGHEST EDGE COUNT
                if (countEdge > maxValueCountEdges) {
                    maxValueCountEdges = countEdge;
                }
            }

            /* RESCALING EDGE WEIGHTS FROM 0 TO 10  */
            for (String edgeToCreate : setCoocTotal.getElementSet()) {
                String[] pair = edgeToCreate.split(",");
                Integer countEdge = setCoocTotal.getCount(edgeToCreate);
                double edgeWeightRescaledToTen = 0;

                switch (typeCorrection) {
                    case "pmi" -> {
                            double edgeWeightBeforeRescaling = edgesAndTheirPMIWeightsBeforeRescaling.get(edgeToCreate);
                            edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValuePMI;
                        }
                    case "tfidf" -> {
                            double edgeWeightBeforeRescaling = tfidfForAnEdge.get(edgeToCreate);
                            edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueTFIDF;
                        }
                    case "none"  ->  {
                            double edgeWeightBeforeRescaling = countEdge.doubleValue();
                            edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                        }
                    default -> {
                            double edgeWeightBeforeRescaling = countEdge.doubleValue();
                            edgeWeightRescaledToTen = (double) edgeWeightBeforeRescaling * 10 / maxValueCountEdges;
                    }
                }
                
                Node nodeSource = graphResult.getNode(pair[0]);
                Node nodeTarget = graphResult.getNode(pair[1]);
                Edge edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                edge.setAttribute(countPairsColumn, countEdge);

                edgesForGraph.add(edge);
            }

            graphResult.addAllEdges(edgesForGraph);

//        System.out.println("graph contains " + graphResult.getNodeCount() + " nodes");
//        System.out.println("graph contains " + graphResult.getEdgeCount() + " edges");
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
            exporterGexf.setExportDynamic(false);

            StringWriter stringWriter = new StringWriter();
            ec.exportWriter(stringWriter, exporterGexf);
            stringWriter.close();
            return stringWriter.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;

    }
}

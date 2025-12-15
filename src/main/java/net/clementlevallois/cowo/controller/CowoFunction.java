package net.clementlevallois.cowo.controller;

import java.util.Set;
import java.util.TreeMap;

public class CowoFunction {

    private String callbackUrl = "";
    private String jobId = "";

    // Main kept for testing purposes
    public static void main(String[] args) {
        // Example usage:
        // CowoConfig config = new CowoConfig();
        // config.mapOfLines = ...;
        // config.languageCodes = "en";
        // new CowoFunction().analyze(config);
    }

    /**
     * Primary entry point using the Configuration Object.
     */
    public String analyze(CowoConfig config) {
        // 1. Setup Services
        // We pass the stored callbackUrl and jobId to the notifier
        JobNotifier notifier = new JobNotifier(this.callbackUrl, this.jobId);
        TextMiningService textService = new TextMiningService();
        GephiGraphBuilder graphService = new GephiGraphBuilder();

        // 2. Execute Pipeline
        try {
            DataManager dm = textService.process(config, notifier);
            return graphService.build(dm, config, notifier);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error during analysis: " + e.getMessage();
        }
    }

    /**
     * Legacy wrapper to maintain backward compatibility.
     * It builds the config object and calls the main analyze method.
     */
    public String analyze(TreeMap<Integer, String> lines, String langCodes, Set<String> userStopwords, 
                          int minChar, boolean repStop, boolean isSci, boolean rmNames, boolean rmAccents, 
                          int minCooc, int minTerm, String corr, int maxNGram, boolean lemmatize) {
        
        CowoConfig config = new CowoConfig();
        config.mapOfLines = lines;
        config.languageCodes = langCodes;
        config.userSuppliedStopwords = userStopwords;
        config.minCharNumber = minChar;
        config.replaceStopwords = repStop;
        config.isScientificCorpus = isSci;
        config.removeFirstNames = rmNames;
        config.removeAccents = rmAccents;
        config.minCoocFreq = minCooc;
        config.minTermFreq = minTerm;
        config.typeCorrection = corr;
        config.maxNGram = maxNGram;
        config.lemmatize = lemmatize;

        return analyze(config);
    }
    
    public void setSessionIdAndCallbackURL(String url, String id) {
        this.callbackUrl = url;
        this.jobId = id;
    }
}
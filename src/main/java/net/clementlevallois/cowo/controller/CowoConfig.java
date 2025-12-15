package net.clementlevallois.cowo.controller;

import java.util.Set;
import java.util.TreeMap;

public class CowoConfig {
    // Input Data
    public TreeMap<Integer, String> mapOfLines;
    public String languageCodes; // e.g., "en,fr"
    
    // Text Cleaning Options
    public Set<String> userSuppliedStopwords;
    public boolean replaceStopwords = false;
    public int minCharNumber = 3;
    public boolean isScientificCorpus = false;
    public boolean removeFirstNames = false;
    public boolean removeAccents = true;
    public boolean removeLeaves = false;
    public boolean skipContentInParentheses = false;
    public boolean flattenToAscii = false;
    public int maxNGram = 4;
    public boolean lemmatize = true;

    // Graph Options
    public int minCoocFreq = 2;
    public int minTermFreq = 4;
    public String typeCorrection = "pmi"; // "pmi" or "none"
}
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.cowo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.clementlevallois.umigon.model.NGram;
import net.clementlevallois.umigon.model.TextFragment;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class DataManager {

    private Map<Integer, String> originalStringsPerLine;
    private ConcurrentHashMap<Integer, List<TextFragment>> textFragmentsPerLine;
    private Map<Integer, Set<String>> cleanedAndStrippedNGramsPerLine;
    private List<NGram> listOfnGramsGlobal;
    private Map<NGram, Long> nGramsAndGlobalCount;
    private Map<String, String> stringifiedCleanedAndStrippedNGramToLemmatizedForm;

    private Map<String, NGram> mappingNonLemmatizedFormToNGram = new HashMap();
    private Map<String, NGram> mappingLemmatizedFormToNGram = new HashMap();
    private Multiset<Cooc> listCoocTotal;

    public DataManager() {
        originalStringsPerLine = new TreeMap();
        cleanedAndStrippedNGramsPerLine = new TreeMap();
        textFragmentsPerLine = new ConcurrentHashMap();
        listOfnGramsGlobal = new ArrayList();
        stringifiedCleanedAndStrippedNGramToLemmatizedForm = new HashMap();
        nGramsAndGlobalCount = new HashMap();
    }

    public Map<Integer, String> getOriginalStringsPerLine() {
        return originalStringsPerLine;
    }

    public void setMapOfLines(TreeMap<Integer, String> originalStringsPerLine) {
        this.originalStringsPerLine = originalStringsPerLine;
    }

    public ConcurrentHashMap<Integer, List<TextFragment>> getTextFragmentsPerLine() {
        return textFragmentsPerLine;
    }

    public void setTextFragmentsPerLine(ConcurrentHashMap<Integer, List<TextFragment>> textFragmentsPerLine) {
        this.textFragmentsPerLine = textFragmentsPerLine;
    }

    public Map<Integer, Set<String>> getCleanedAndStrippedNGramsPerLine() {
        return cleanedAndStrippedNGramsPerLine;
    }

    public void setCleanedAndStrippedNGramsPerLine(TreeMap<Integer, Set<String>> cleanedAndStrippedNGramsPerLine) {
        this.cleanedAndStrippedNGramsPerLine = cleanedAndStrippedNGramsPerLine;
    }

    public List<NGram> getListOfnGramsGlobal() {
        return listOfnGramsGlobal;
    }

    public void setListOfnGramsGlobal(List<NGram> listOfnGramsGlobal) {
        this.listOfnGramsGlobal = listOfnGramsGlobal;
    }

    public Map<String, String> getStringifiedCleanedAndStrippedNGramToLemmatizedForm() {
        return stringifiedCleanedAndStrippedNGramToLemmatizedForm;
    }

    public void setStringifiedCleanedAndStrippedNGramToLemmatizedForm(Map<String, String> stringifiedCleanedAndStrippedNGramToLemmatizedForm) {
        this.stringifiedCleanedAndStrippedNGramToLemmatizedForm = stringifiedCleanedAndStrippedNGramToLemmatizedForm;
    }

    public Map<String, NGram> getMappingNonLemmatizedFormToNGram() {
        return mappingNonLemmatizedFormToNGram;
    }

    public void setMappingNonLemmatizedFormToNGram(Map<String, NGram> mappingNonLemmatizedFormToNGram) {
        this.mappingNonLemmatizedFormToNGram = mappingNonLemmatizedFormToNGram;
    }

    public Map<String, NGram> getMappingLemmatizedFormToNGram() {
        return mappingLemmatizedFormToNGram;
    }

    public void setMappingLemmatizedFormToNGram(Map<String, NGram> mappingLemmatizedFormToNGram) {
        this.mappingLemmatizedFormToNGram = mappingLemmatizedFormToNGram;
    }

    public Map<NGram, Long> getnGramsAndGlobalCount() {
        return nGramsAndGlobalCount;
    }

    public void setnGramsAndGlobalCount(Map<NGram, Long> nGramsAndGlobalCount) {
        this.nGramsAndGlobalCount = nGramsAndGlobalCount;
    }

    public void setListCoocTotal(Multiset<Cooc> listCoocTotal) {
        this.listCoocTotal = listCoocTotal;
    }

    public Multiset<Cooc> getListCoocTotal() {
        return listCoocTotal;
    }

}

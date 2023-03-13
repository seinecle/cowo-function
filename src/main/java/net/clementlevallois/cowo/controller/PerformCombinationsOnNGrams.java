/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.cowo.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.clementlevallois.umigon.model.NGram;
import net.clementlevallois.utils.CombinationGenerator;

/**
 *
 * @author LEVALLOIS
 */
public class PerformCombinationsOnNGrams {

    private final NGram[] table;

    public PerformCombinationsOnNGrams (NGram[] table) throws InterruptedException, IOException {
        this.table = table;

    }

    public List<Cooc> call() throws InterruptedException, IOException {

        int i = 0;
        List<Cooc> list = new ArrayList();

        //finds all pairs (2) of the brands
        int[] indices;

        CombinationGenerator x = new CombinationGenerator(table.length, 2);

        
        while (x.hasMore()) {
            indices = x.getNext();
            Cooc cooc = new Cooc(table[indices[0]],table[indices[1]]);

            // save all these pairs in a set
            list.add(cooc);

        }
        return list;

    }

}

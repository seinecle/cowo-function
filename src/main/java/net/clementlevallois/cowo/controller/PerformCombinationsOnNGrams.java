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
    private final CombinationGenerator x;

    public PerformCombinationsOnNGrams(NGram[] table) throws InterruptedException, IOException {
        this.table = table;
        this.x = new CombinationGenerator(table.length, 2);
    }

    public List<Cooc> call() throws InterruptedException, IOException {
        // Pre-size the list to the exact number of combinations
        List<Cooc> list = new ArrayList<>(x.getTotal().intValue());
        int[] indices;
        while (x.hasMore()) {
            indices = x.getNext();
            list.add(new Cooc(table[indices[0]], table[indices[1]]));
        }
        return list;
    }
}

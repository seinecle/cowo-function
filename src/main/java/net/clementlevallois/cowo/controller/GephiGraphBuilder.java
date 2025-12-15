package net.clementlevallois.cowo.controller;

import java.io.StringWriter;
import java.util.*;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.umigon.model.NGram;
import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.plugin.ExporterGEXF;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public class GephiGraphBuilder {

    private static final Object GEPHI_LOCK = new Object();

    public String build(DataManager dm, CowoConfig config, JobNotifier notifier) {

        // 1. Calculate Co-occurrences (Safe to do outside lock)
        notifier.notify("🧠 calculating cooccurrences", 75);
        Multiset<Cooc> listCoocTotal = calculateCoocs(dm, config);

        // 2. Filter Edges
        notifier.notify("🧹 cleaning edges", 80);
        Multiset<Cooc> finalCoocs = filterCoocs(listCoocTotal);

        Multiset<String> coocsStringified = new Multiset<>();
        Multiset<String> nodesInEdgesStringified = new Multiset<>();
        for (Cooc c : finalCoocs.getElementSet()) {
            int count = finalCoocs.getCount(c);
            coocsStringified.addSeveral(c.toString(), count);
            nodesInEdgesStringified.addOne(c.a.getOriginalFormLemmatized());
            nodesInEdgesStringified.addOne(c.b.getOriginalFormLemmatized());
        }

        // 3. Gephi Critical Section
        notifier.notify("⚽ adding nodes to graph", 85);

        synchronized (GEPHI_LOCK) {
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            if (pc.getCurrentProject() != null) {
                pc.closeCurrentProject();
            }
            pc.newProject();
            Workspace workspace = pc.getCurrentWorkspace();

            try {
                GraphModel gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
                workspace.getProject().getProjectMetadata().setAuthor("nocodefunctions.com");
                gm.getGraph().clear();

                // Setup Columns
                Column countTermsCol = gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
                Column countPairsCol = gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
                if ("pmi".equals(config.typeCorrection)) {
                    gm.getEdgeTable().addColumn("pmi", Float.TYPE);
                }

                Graph graph = gm.getGraph();
                Map<String, Node> nodesMap = new HashMap<>();
                Set<String> removedNodes = new HashSet<>();
                Set<String> validNodeLabels = nodesInEdgesStringified.getElementSet();

                // Add Nodes
                for (Map.Entry<NGram, Long> entry : dm.getnGramsAndGlobalCount().entrySet()) {
                    String label = entry.getKey().getOriginalFormLemmatized();
                    if (!validNodeLabels.contains(label)) {
                        removedNodes.add(label);
                        continue;
                    }
                    if (config.removeLeaves && nodesInEdgesStringified.getCount(label) == 1) {
                        removedNodes.add(label);
                        continue;
                    }

                    Node n = gm.factory().newNode(label);
                    n.setLabel(label);
                    n.setAttribute(countTermsCol, entry.getValue().intValue());
                    graph.addNode(n);
                    nodesMap.put(label, n);
                }

                // Add Edges
                notifier.notify("🔗 adding edges", 90);
                buildEdges(graph, finalCoocs, coocsStringified, nodesMap, removedNodes, dm, config, countPairsCol);

                // Layout
                notifier.notify("🗺️ applying layout", 95);
                applyLayout(gm);

                // Export
                notifier.notify("🛥️ exporting", 98);
                return export(workspace);

            } catch (Exception e) {
                Exceptions.printStackTrace(e);
                return "Error in graph generation";
            } finally {
                if (pc.getCurrentProject() != null) {
                    pc.closeCurrentProject();
                }
            }
        }
    }

    private Multiset<Cooc> calculateCoocs(DataManager dm, CowoConfig config) {
        Multiset<Cooc> total = new Multiset<>();
        Map<String, NGram> lookup = new HashMap<>();
        for (NGram n : dm.getnGramsAndGlobalCount().keySet()) {
            lookup.put(n.getCleanedAndStrippedNgramIfCondition(config.flattenToAscii), n);
            lookup.put(n.getOriginalFormLemmatized(), n);
        }

        dm.getCleanedAndStrippedNGramsPerLine().values().forEach(lineGrams -> {
            Map<String, NGram> lineMap = new HashMap<>();
            for (String s : lineGrams) {
                if (lookup.containsKey(s)) {
                    NGram n = lookup.get(s);
                    lineMap.put(n.getOriginalFormLemmatized(), n);
                }
            }
            if (lineMap.size() > 1) {
                try {
                    List<Cooc> coocs = new PerformCombinationsOnNGrams(lineMap.values().toArray(new NGram[0])).call();
                    total.addAllFromListOrSet(coocs);
                } catch (Exception e) {
                    Exceptions.printStackTrace(e);
                }
            }
        });
        return total;
    }

    private Multiset<Cooc> filterCoocs(Multiset<Cooc> raw) {
        int min = 1;
        if (raw.getSize() > 110_000) {
            min = 4;
        } else if (raw.getSize() > 80_000) {
            min = 3;
        } else if (raw.getSize() > 50_000) {
            min = 2;
        }

        List<Map.Entry<Cooc, Integer>> sorted = raw.sortDesckeepAboveMinFreq(raw, min);
        Multiset<Cooc> filtered = new Multiset<>();
        for (Map.Entry<Cooc, Integer> e : sorted) {
            filtered.addSeveral(e.getKey(), e.getValue());
        }
        return filtered;
    }

    private void buildEdges(Graph graph, Multiset<Cooc> coocs, Multiset<String> counts, Map<String, Node> nodes, Set<String> removed, DataManager dm, CowoConfig config, Column countCol) {
        double maxPmi = 0.00001;
        float maxCount = 0;
        Map<String, Double> pmiMap = new HashMap<>();

        // 1. Calc stats
        for (Cooc c : coocs.getElementSet()) {
            if (removed.contains(c.a.getOriginalFormLemmatized()) || removed.contains(c.b.getOriginalFormLemmatized())) {
                continue;
            }
            int count = counts.getCount(c.toString());
            long freqA = dm.getnGramsAndGlobalCount().get(c.a);
            long freqB = dm.getnGramsAndGlobalCount().get(c.b);
            double pmi = (double) count / (freqA * freqB);
            if (pmi > maxPmi) {
                maxPmi = pmi;
            }
            if (count > maxCount) {
                maxCount = count;
            }
            pmiMap.put(c.toString(), pmi);
        }

        // 2. Create Edges
        for (Cooc c : coocs.getElementSet()) {
            if (removed.contains(c.a.getOriginalFormLemmatized()) || removed.contains(c.b.getOriginalFormLemmatized())) {
                continue;
            }
            Node n1 = nodes.get(c.a.getOriginalFormLemmatized());
            Node n2 = nodes.get(c.b.getOriginalFormLemmatized());
            if (n1 == null || n2 == null) {
                continue;
            }

            int count = counts.getCount(c.toString());
            double weight;
            if ("pmi".equals(config.typeCorrection)) {
                weight = pmiMap.get(c.toString()) * 10 / maxPmi;
            } else {
                weight = (double) count * 10 / maxCount;
            }

            Edge e = graph.getModel().factory().newEdge(n1, n2, 0, weight, false);
            e.setAttribute(countCol, count);
            if ("pmi".equals(config.typeCorrection)) {
                e.setAttribute("pmi", (float) pmiMap.get(c.toString()).doubleValue());
            }
            graph.addEdge(e);
        }
    }

    private void applyLayout(GraphModel gm) {
        ForceAtlas2 layout = new ForceAtlas2(new ForceAtlas2Builder());
        layout.setGraphModel(gm);
        layout.resetPropertiesValues();
        layout.setScalingRatio(50d);
        layout.setAdjustSizes(false);
        layout.setBarnesHutOptimize(true);
        layout.initAlgo();

        long start = System.currentTimeMillis();
        // Dynamic duration based on graph size could be added here
        while (layout.canAlgo() && (System.currentTimeMillis() - start) < 3000) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }

    private String export(Workspace ws) {
        try (StringWriter sw = new StringWriter()) {
            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
            ExporterGEXF exporter = (ExporterGEXF) ec.getExporter("gexf");
            exporter.setWorkspace(ws);
            exporter.setExportDynamic(false);
            exporter.setExportPosition(true);
            ec.exportWriter(sw, exporter);
            return sw.toString();
        } catch (Exception e) {
            return "";
        }
    }
}

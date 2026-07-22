package li.selman.modulithdocsmarkdown;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A black-box parser for the Mermaid definitions {@link ModulithMermaid} produces via Structurizr's
 * {@code MermaidDiagramExporter}, used only by tests to assert on node/edge topology without depending on the exact
 * rendering (colors, styling, subgraph nesting).
 * <p>
 * Node definitions look like {@code 3["<div style='font-weight: bold'>Inventory</div>..."]} and edges look
 * like {@code 4-- "<div>uses</div>..." -->3} (solid) or {@code 4-. "..." .->3} (dashed) — see
 * {@code MermaidDiagramExporter#writeElement} and {@code #writeRelationship}.
 */
record MermaidGraph(Map<String, String> nodeLabels, Set<String> edgesByLabel) {

    private static final Pattern NODE = Pattern.compile("^(\\S+)\\[\"<div style='font-weight: bold'>(.*?)</div>");
    private static final Pattern EDGE = Pattern.compile("^(\\S+)-[-.]\\s+\".*?\"\\s+[-.]->(\\S+)\\s*$");

    static MermaidGraph parse(String definition) {

        var labels = new HashMap<String, String>();
        var edges = new java.util.ArrayList<String[]>();

        definition.lines().forEach(line -> {
            var node = NODE.matcher(line.strip());

            if (node.find()) {
                labels.put(node.group(1), node.group(2));
                return;
            }

            var edge = EDGE.matcher(line.strip());

            if (edge.matches()) {
                edges.add(new String[] {edge.group(1), edge.group(2)});
            }
        });

        var edgesByLabel = edges.stream()
                .map(pair -> labels.get(pair[0]) + "->" + labels.get(pair[1]))
                .collect(Collectors.toSet());

        return new MermaidGraph(labels, edgesByLabel);
    }
}

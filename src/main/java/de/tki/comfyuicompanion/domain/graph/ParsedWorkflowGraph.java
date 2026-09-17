package de.tki.comfyuicompanion.domain.graph;

import java.util.List;

/**
 * Domain representation of a parsed visual workflow graph, containing nodes and links
 * ready for rendering in a visual graph panel.
 *
 * @param nodes The visual nodes in the workflow
 * @param links The visual connections between node slots
 */
public record ParsedWorkflowGraph(List<VisualNode> nodes, List<VisualLink> links) {

    /**
     * Visual node representation for layout and rendering.
     */
    public static class VisualNode {
        public long id;
        public String type;
        public String title;
        public double x;
        public double y;
        public double width = 140;
        public double height = 50;

        // Scaled coordinates for drawing
        public double drawX;
        public double drawY;

        public VisualNode() {}

        public VisualNode(long id, String type, String title, double x, double y) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Visual link representation connecting an origin node to a target node.
     */
    public static class VisualLink {
        public long id;
        public long originId;
        public long targetId;
        public int originSlot;
        public int targetSlot;

        public VisualLink() {}

        public VisualLink(long id, long originId, long targetId, int originSlot, int targetSlot) {
            this.id = id;
            this.originId = originId;
            this.targetId = targetId;
            this.originSlot = originSlot;
            this.targetSlot = targetSlot;
        }
    }
}

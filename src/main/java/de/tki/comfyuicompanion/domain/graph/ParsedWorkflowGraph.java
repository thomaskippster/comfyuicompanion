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

        /**
         * Constructs an empty VisualNode.
         */
        public VisualNode() {}

        /**
         * Constructs a VisualNode with layout properties.
         *
         * @param id    the node identifier
         * @param type  the class type
         * @param title the display title
         * @param x     the x coordinate
         * @param y     the y coordinate
         */
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

        /**
         * Constructs an empty VisualLink.
         */
        public VisualLink() {}

        /**
         * Constructs a VisualLink with connection properties.
         *
         * @param id         the link identifier
         * @param originId   the origin node identifier
         * @param targetId   the target node identifier
         * @param originSlot the origin slot index
         * @param targetSlot the target slot index
         */
        public VisualLink(long id, long originId, long targetId, int originSlot, int targetSlot) {
            this.id = id;
            this.originId = originId;
            this.targetId = targetId;
            this.originSlot = originSlot;
            this.targetSlot = targetSlot;
        }
    }
}

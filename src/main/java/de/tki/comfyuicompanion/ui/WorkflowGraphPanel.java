package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.graph.ParsedWorkflowGraph;
import de.tki.comfyuicompanion.domain.graph.ParsedWorkflowGraph.VisualLink;
import de.tki.comfyuicompanion.domain.graph.ParsedWorkflowGraph.VisualNode;
import de.tki.comfyuicompanion.service.WorkflowGraphParser;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visual Swing component rendering ComfyUI nodes and links as an interactive technical node graph.
 */
public class WorkflowGraphPanel extends JPanel {
    private String workflowJson;
    private final List<VisualNode> nodes = new ArrayList<>();
    private final List<VisualLink> links = new ArrayList<>();
    private final WorkflowGraphParser parser;
    
    public WorkflowGraphPanel() {
        this(new WorkflowGraphParser());
    }

    public WorkflowGraphPanel(WorkflowGraphParser parser) {
        this.parser = parser != null ? parser : new WorkflowGraphParser();
        updateUI();
        setOpaque(true);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (com.formdev.flatlaf.FlatLaf.isLafDark()) {
            setBackground(new Color(20, 21, 24));
        } else {
            setBackground(new Color(245, 247, 250));
        }
    }
    
    public void setWorkflowJson(String json) {
        this.workflowJson = json;
        parseWorkflow();
        repaint();
    }
    
    private void parseWorkflow() {
        nodes.clear();
        links.clear();
        if (workflowJson == null || workflowJson.trim().isEmpty()) return;
        
        ParsedWorkflowGraph graph = parser.parse(workflowJson);
        nodes.addAll(graph.nodes());
        links.addAll(graph.links());
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        boolean isDark = com.formdev.flatlaf.FlatLaf.isLafDark();
        
        // Draw grid background to make it look technical and premium
        g2.setColor(isDark ? new Color(25, 27, 32) : new Color(230, 235, 242));
        int gridSize = 25;
        for (int x = 0; x < w; x += gridSize) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += gridSize) {
            g2.drawLine(0, y, w, y);
        }
        
        if (nodes.isEmpty()) {
            g2.setColor(isDark ? new Color(150, 160, 180) : new Color(100, 110, 120));
            g2.setFont(new Font("Inter", Font.PLAIN, 14));
            String msg = "No workflow loaded or invalid workflow JSON.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            return;
        }
        
        // Calculate scaling factors
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        for (VisualNode vn : nodes) {
            if (vn.x < minX) minX = vn.x;
            if (vn.y < minY) minY = vn.y;
            if (vn.x + vn.width > maxX) maxX = vn.x + vn.width;
            if (vn.y + vn.height > maxY) maxY = vn.y + vn.height;
        }
        
        double graphW = maxX - minX;
        double graphH = maxY - minY;
        if (graphW == 0) graphW = 1;
        if (graphH == 0) graphH = 1;
        
        double padding = 40.0;
        double scaleX = (w - padding * 2) / graphW;
        double scaleY = (h - padding * 2) / graphH;
        double scale = Math.min(scaleX, scaleY);
        
        // Don't scale up too much for tiny graphs
        if (scale > 1.2) scale = 1.2;
        
        // Center the scaled graph
        double offsetX = (w - graphW * scale) / 2 - minX * scale;
        double offsetY = (h - graphH * scale) / 2 - minY * scale;
        
        // Update nodes' scaled draw coordinates
        Map<Long, VisualNode> nodeMap = new HashMap<>();
        for (VisualNode vn : nodes) {
            vn.drawX = vn.x * scale + offsetX;
            vn.drawY = vn.y * scale + offsetY;
            nodeMap.put(vn.id, vn);
        }
        
        // 1. Draw Links (bezier curves with abgerundete connector points!)
        g2.setStroke(new BasicStroke(2.0f));
        for (VisualLink vl : links) {
            VisualNode src = nodeMap.get(vl.originId);
            VisualNode dest = nodeMap.get(vl.targetId);
            if (src == null || dest == null) continue;
            
            // Calculate start and end coordinates
            double startX = src.drawX + src.width * scale;
            double startY = src.drawY + (src.height * scale) / 2;
            
            double endX = dest.drawX;
            double endY = dest.drawY + (dest.height * scale) / 2;
            
            // Soft glowing cyan/turquoise connection line
            g2.setColor(isDark ? new Color(0, 220, 255, 140) : new Color(0, 120, 150, 140));
            
            // Draw a smooth bezier curve (horizontal cubic curve)
            double controlOffset = Math.abs(endX - startX) / 2.0;
            if (controlOffset < 30) controlOffset = 30;
            
            Path2D.Double curve = new Path2D.Double();
            curve.moveTo(startX, startY);
            curve.curveTo(startX + controlOffset, startY, endX - controlOffset, endY, endX, endY);
            g2.draw(curve);
            
            // Draw connector points (abgerundete Knotenpunkte)
            g2.setColor(isDark ? new Color(0, 255, 255) : new Color(0, 120, 150));
            g2.fill(new Ellipse2D.Double(startX - 4, startY - 4, 8, 8));
            g2.fill(new Ellipse2D.Double(endX - 4, endY - 4, 8, 8));
        }
        
        // 2. Draw Nodes (rounded glassmorphism cards!)
        g2.setFont(new Font("Inter", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        
        for (VisualNode vn : nodes) {
            double nx = vn.drawX;
            double ny = vn.drawY;
            double nw = vn.width * scale;
            double nh = vn.height * scale;
            
            // Frosted Glass Effect: Semi-transparent background
            g2.setColor(isDark ? new Color(30, 33, 40, 210) : new Color(255, 255, 255, 210));
            g2.fill(new RoundRectangle2D.Double(nx, ny, nw, nh, 12, 12));
            
            // Thin glowing cyan/turquoise border highlight
            g2.setStroke(new BasicStroke(1.0f));
            g2.setColor(isDark ? new Color(0, 240, 255, 70) : new Color(0, 120, 150, 70));
            g2.draw(new RoundRectangle2D.Double(nx, ny, nw, nh, 12, 12));
            
            // Subtly lighter shine at the top edge of the card (3D light edge)
            g2.setColor(isDark ? new Color(255, 255, 255, 30) : new Color(255, 255, 255, 120));
            g2.draw(new Line2D.Double(nx + 6, ny, nx + nw - 6, ny));
            
            // Text Label
            g2.setColor(isDark ? new Color(230, 240, 255) : new Color(30, 30, 30));
            String text = vn.title;
            if (fm.stringWidth(text) > nw - 10) {
                // Truncate text if too long
                text = text.substring(0, Math.min(text.length(), 15)) + "...";
            }
            g2.drawString(text, (int) (nx + 10), (int) (ny + nh / 2 + 4));
            
            // Draw node ID badge
            g2.setFont(new Font("Inter", Font.PLAIN, 9));
            g2.setColor(isDark ? new Color(0, 255, 255, 150) : new Color(0, 120, 150, 150));
            g2.drawString("#" + vn.id, (int) (nx + nw - fm.stringWidth("#" + vn.id) - 10), (int) (ny + nh - 6));
            g2.setFont(new Font("Inter", Font.BOLD, 11));
        }
    }
}

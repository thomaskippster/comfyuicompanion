package de.tki.comfymodels.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowGraphPanel extends JPanel {
    private String workflowJson;
    private final List<VisualNode> nodes = new ArrayList<>();
    private final List<VisualLink> links = new ArrayList<>();
    
    private static class VisualNode {
        long id;
        String type;
        String title;
        double x, y;
        double width = 140;
        double height = 50;
        
        // Scaled coordinates for drawing
        double drawX, drawY;
    }
    
    private static class VisualLink {
        long id;
        long originId;
        long targetId;
        int originSlot;
        int targetSlot;
    }
    
    public WorkflowGraphPanel() {
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
        
        try {
            JSONObject root = new JSONObject(workflowJson);
            
            // ComfyUI workflow format can have nodes directly at root or under extra_data, 
            // but standard format is {"nodes": [...], "links": [...]}
            if (root.has("nodes")) {
                JSONArray nodesArray = root.getJSONArray("nodes");
                Map<Long, VisualNode> nodeMap = new HashMap<>();
                
                for (int i = 0; i < nodesArray.length(); i++) {
                    JSONObject n = nodesArray.getJSONObject(i);
                    VisualNode vn = new VisualNode();
                    vn.id = n.getLong("id");
                    vn.type = n.optString("type", "Unknown");
                    vn.title = n.optString("title", vn.type);
                    
                    if (n.has("pos")) {
                        JSONArray pos = n.getJSONArray("pos");
                        vn.x = pos.getDouble(0);
                        vn.y = pos.getDouble(1);
                    } else {
                        vn.x = 0;
                        vn.y = 0;
                    }
                    
                    if (n.has("size")) {
                        JSONArray size = n.getJSONArray("size");
                        vn.width = size.getDouble(0);
                        vn.height = size.getDouble(1);
                        if (vn.width < 100) vn.width = 120;
                        if (vn.height < 40) vn.height = 40;
                    }
                    
                    nodes.add(vn);
                    nodeMap.put(vn.id, vn);
                }
                
                // Parse links. ComfyUI has a "links" array at root: [ [id, origin_id, origin_slot, target_id, target_slot, type], ... ]
                if (root.has("links")) {
                    JSONArray linksArray = root.getJSONArray("links");
                    for (int i = 0; i < linksArray.length(); i++) {
                        JSONArray l = linksArray.getJSONArray(i);
                        if (l.length() >= 5) {
                            VisualLink vl = new VisualLink();
                            vl.id = l.getLong(0);
                            vl.originId = l.getLong(1);
                            vl.originSlot = l.getInt(2);
                            vl.targetId = l.getLong(3);
                            vl.targetSlot = l.getInt(4);
                            links.add(vl);
                        }
                    }
                } else {
                    // Fallback link construction if no links array exists (API format)
                    // In API format, nodes are key-value: {"1": {"inputs": {"model": ["4", 0]}}}
                    // Let's check inputs of all nodes
                    for (VisualNode vn : nodes) {
                        // Find the original JSON object for this node to scan inputs
                        for (int i = 0; i < nodesArray.length(); i++) {
                            JSONObject n = nodesArray.getJSONObject(i);
                            if (n.getLong("id") == vn.id) {
                                JSONObject inputs = n.optJSONObject("inputs");
                                if (inputs != null) {
                                    for (String key : inputs.keySet()) {
                                        Object val = inputs.get(key);
                                        if (val instanceof JSONArray) {
                                            JSONArray linkArr = (JSONArray) val;
                                            if (linkArr.length() == 2) {
                                                try {
                                                    long originId = Long.parseLong(linkArr.getString(0));
                                                    VisualLink vl = new VisualLink();
                                                    vl.originId = originId;
                                                    vl.targetId = vn.id;
                                                    vl.originSlot = 0;
                                                    vl.targetSlot = 0;
                                                    links.add(vl);
                                                } catch (NumberFormatException ignored) {}
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            } else {
                // If it is API format directly: {"3": {"inputs": {...}, "class_type": "KSampler"}}
                // Let's parse keys as node IDs
                Map<Long, VisualNode> nodeMap = new HashMap<>();
                int idx = 0;
                for (String key : root.keySet()) {
                    try {
                        long id = Long.parseLong(key);
                        JSONObject n = root.getJSONObject(key);
                        VisualNode vn = new VisualNode();
                        vn.id = id;
                        vn.type = n.optString("class_type", "Unknown");
                        vn.title = vn.type;
                        
                        // Fake coordinates for grid layout if positions are missing
                        vn.x = (idx % 4) * 250;
                        vn.y = (idx / 4) * 150;
                        idx++;
                        
                        nodes.add(vn);
                        nodeMap.put(vn.id, vn);
                    } catch (NumberFormatException ignored) {}
                }
                
                // Parse API format links
                for (VisualNode vn : nodes) {
                    JSONObject n = root.getJSONObject(String.valueOf(vn.id));
                    JSONObject inputs = n.optJSONObject("inputs");
                    if (inputs != null) {
                        for (String inputKey : inputs.keySet()) {
                            Object val = inputs.get(inputKey);
                            if (val instanceof JSONArray) {
                                JSONArray arr = (JSONArray) val;
                                if (arr.length() == 2) {
                                    try {
                                        long originId = Long.parseLong(arr.getString(0));
                                        VisualLink vl = new VisualLink();
                                        vl.originId = originId;
                                        vl.targetId = vn.id;
                                        vl.originSlot = arr.getInt(1);
                                        vl.targetSlot = 0;
                                        links.add(vl);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Invalid JSON or format
        }
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

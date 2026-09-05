package de.tki.comfymodels.ui;

import de.tki.comfymodels.ui.icons.AppIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class DownloadManagerTableCellRendererTest {

    private DownloadManagerTableCellRenderer renderer;
    private JTable table;
    private DefaultTableModel model;

    @BeforeEach
    public void setUp() {
        renderer = new DownloadManagerTableCellRenderer();
        String[] columns = {"Select", "Type", "Name", "Size", "AI Source", "Target Path", "URL", "Status"};
        model = new DefaultTableModel(columns, 0);
        table = new JTable(model);
        table.setDefaultRenderer(String.class, renderer);
    }

    @Test
    public void testStripEmojisSanitizesAllLegacyUnicodeGlyphs() {
        assertEquals("MODEL LIST MATCH", DownloadManagerTableCellRenderer.stripEmojis("📂 MODEL LIST MATCH"));
        assertEquals("Context: ltx (Community)", DownloadManagerTableCellRenderer.stripEmojis("✨ Context: ltx (👥 Community)"));
        assertEquals("AI Verified: black-forest-labs", DownloadManagerTableCellRenderer.stripEmojis("🧠 AI Verified: black-forest-labs"));
        assertEquals("AI Predicted: PonyDiffusion", DownloadManagerTableCellRenderer.stripEmojis("🎯 AI Predicted: PonyDiffusion"));
        assertEquals("UPDATE: New Version", DownloadManagerTableCellRenderer.stripEmojis("⭐ UPDATE: New Version"));
        assertEquals("Already exists", DownloadManagerTableCellRenderer.stripEmojis("✅ Already exists"));
        assertEquals("Known Good", DownloadManagerTableCellRenderer.stripEmojis("✅ Known Good"));
        assertEquals("Archived", DownloadManagerTableCellRenderer.stripEmojis("📦 Archived"));
        assertEquals("Size Mismatch", DownloadManagerTableCellRenderer.stripEmojis("🔄 Size Mismatch"));
        assertEquals("Error: Connection reset", DownloadManagerTableCellRenderer.stripEmojis("❌ Error: Connection reset"));
        assertEquals("Searching Official: flux", DownloadManagerTableCellRenderer.stripEmojis("🔍 Searching Official: flux"));
        assertEquals("Normal Text Without Emojis", DownloadManagerTableCellRenderer.stripEmojis("Normal Text Without Emojis"));
        assertEquals("", DownloadManagerTableCellRenderer.stripEmojis(""));
        assertEquals("", DownloadManagerTableCellRenderer.stripEmojis(null));
    }

    @Test
    public void testResolveAiSourcePresentations() {
        var p1 = DownloadManagerTableCellRenderer.resolveAiSource("📂 MODEL LIST MATCH");
        assertEquals(AppIcon.FOLDER, p1.icon());
        assertEquals("MODEL LIST MATCH", p1.cleanText());

        var p2 = DownloadManagerTableCellRenderer.resolveAiSource("✨ Context: ltx (👥 Community)");
        assertEquals(AppIcon.SUGGEST, p2.icon());
        assertEquals("Context: ltx (Community)", p2.cleanText());

        var p3 = DownloadManagerTableCellRenderer.resolveAiSource("🧠 AI Verified: black-forest-labs");
        assertEquals(AppIcon.AI, p3.icon());
        assertEquals("AI Verified: black-forest-labs", p3.cleanText());

        var p4 = DownloadManagerTableCellRenderer.resolveAiSource("🎯 AI Predicted: stabilityai");
        assertEquals(AppIcon.AI, p4.icon());
        assertEquals("AI Predicted: stabilityai", p4.cleanText());

        var p5 = DownloadManagerTableCellRenderer.resolveAiSource("👥 Community");
        assertEquals(AppIcon.USER, p5.icon());
        assertEquals("Community", p5.cleanText());

        var p6 = DownloadManagerTableCellRenderer.resolveAiSource("⭐ UPDATE: v2.0");
        assertEquals(AppIcon.REFRESH, p6.icon());
        assertEquals("UPDATE: v2.0", p6.cleanText());
    }

    @Test
    public void testResolveStatusPresentations() {
        var s1 = DownloadManagerTableCellRenderer.resolveStatus("✅ Already exists");
        assertEquals(AppIcon.CHECK, s1.icon());
        assertEquals("Already exists", s1.cleanText());
        assertEquals(DownloadManagerTableCellRenderer.COLOR_SUCCESS, s1.textColor());

        var s2 = DownloadManagerTableCellRenderer.resolveStatus("✅ Known Good");
        assertEquals(AppIcon.CHECK, s2.icon());
        assertEquals("Known Good", s2.cleanText());
        assertEquals(DownloadManagerTableCellRenderer.COLOR_KNOWN_GOOD, s2.textColor());

        var s3 = DownloadManagerTableCellRenderer.resolveStatus("📦 Archived");
        assertEquals(AppIcon.ARCHIVE, s3.icon());
        assertEquals("Archived", s3.cleanText());
        assertEquals(DownloadManagerTableCellRenderer.COLOR_ARCHIVE, s3.textColor());

        var s4 = DownloadManagerTableCellRenderer.resolveStatus("📦 Restoring (45%)...");
        assertEquals(AppIcon.ARCHIVE, s4.icon());
        assertEquals("Restoring (45%)...", s4.cleanText());

        var s5 = DownloadManagerTableCellRenderer.resolveStatus("🔄 Size Mismatch");
        assertEquals(AppIcon.REFRESH, s5.icon());
        assertEquals("Size Mismatch", s5.cleanText());
        assertEquals(DownloadManagerTableCellRenderer.COLOR_ERROR, s5.textColor());

        var s6 = DownloadManagerTableCellRenderer.resolveStatus("❌ Error: 404 Not Found");
        assertEquals(AppIcon.CLOSE, s6.icon());
        assertEquals("Error: 404 Not Found", s6.cleanText());
        assertEquals(DownloadManagerTableCellRenderer.COLOR_ERROR, s6.textColor());

        var s7 = DownloadManagerTableCellRenderer.resolveStatus("🔍 Searching Official: model");
        assertEquals(AppIcon.SEARCH, s7.icon());
        assertEquals("Searching Official: model", s7.cleanText());

        var s8 = DownloadManagerTableCellRenderer.resolveStatus("Downloading 45.2% (12.5 MB/s)");
        assertEquals(AppIcon.DOWNLOAD, s8.icon());
        assertEquals("Downloading 45.2% (12.5 MB/s)", s8.cleanText());

        var s9 = DownloadManagerTableCellRenderer.resolveStatus("Idle");
        assertNull(s9.icon());
        assertEquals("Idle", s9.cleanText());
    }

    @Test
    public void testTableCellRendererComponentRendersCorrectSvgIconsAndText() {
        model.addRow(new Object[]{
                true,
                "vae",
                "ltx-2.3-22b-distilled-fp8.safetensors",
                "27.50GB",
                "📂 MODEL LIST MATCH",
                "models/checkpoints/LTX",
                "https://huggingface.co/example",
                "✅ Known Good"
        });

        // Column 4 (AI Source)
        Component compAiSource = renderer.getTableCellRendererComponent(table, model.getValueAt(0, 4), false, false, 0, 4);
        assertTrue(compAiSource instanceof JLabel);
        JLabel lblAiSource = (JLabel) compAiSource;
        assertNotNull(lblAiSource.getIcon(), "AI Source must have an SVG icon");
        assertEquals("MODEL LIST MATCH", lblAiSource.getText(), "AI Source text must be clean without emoji");

        // Column 7 (Status)
        Component compStatus = renderer.getTableCellRendererComponent(table, model.getValueAt(0, 7), false, false, 0, 7);
        assertTrue(compStatus instanceof JLabel);
        JLabel lblStatus = (JLabel) compStatus;
        assertNotNull(lblStatus.getIcon(), "Status must have an SVG icon");
        assertEquals("Known Good", lblStatus.getText(), "Status text must be clean without emoji");

        // Column 2 (Name)
        Component compName = renderer.getTableCellRendererComponent(table, model.getValueAt(0, 2), false, false, 0, 2);
        JLabel lblName = (JLabel) compName;
        assertNull(lblName.getIcon(), "Regular text columns should not display icons");
        assertEquals("ltx-2.3-22b-distilled-fp8.safetensors", lblName.getText());
    }
}

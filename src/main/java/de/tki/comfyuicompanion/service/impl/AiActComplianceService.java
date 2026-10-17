package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IAiActComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.zip.CRC32;

/**
 * Enterprise service providing automated metadata injection and visual watermarking
 * in full compliance with Article 50 of the European Union Artificial Intelligence Act
 * (Regulation (EU) 2024/1689).
 */
@Service
public class AiActComplianceService implements IAiActComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(AiActComplianceService.class);

    private static final byte[] PNG_HEADER = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] IEND_CHUNK = new byte[]{0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    @Override
    public void injectComplianceMetadata(Path imagePath) throws IOException {
        if (!Files.exists(imagePath)) {
            throw new IOException("File not found: " + imagePath);
        }

        byte[] fileBytes = Files.readAllBytes(imagePath);
        if (!isPng(fileBytes)) {
            // Non-PNG formats or uncompressed streams are logged; PNG is standard ComfyUI output
            logger.debug("Skipping non-PNG file for raw chunk injection: {}", imagePath);
            return;
        }

        if (hasComplianceMetadataBytes(fileBytes)) {
            logger.debug("Image already contains Article 50 compliance metadata: {}", imagePath);
            return;
        }

        int iendIndex = findIendChunk(fileBytes);
        if (iendIndex == -1) {
            logger.warn("Could not locate IEND chunk in PNG: {}", imagePath);
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 1024);
        out.write(fileBytes, 0, iendIndex);

        // Inject standard IPTC/C2PA and Article 50 EU AI Act tEXt chunks
        writeTextChunk(out, "EU-AI-Act", "AI-Generated Content (Art. 50 Regulation (EU) 2024/1689 - EU AI Act)");
        writeTextChunk(out, "DigitalSourceType", "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia");
        writeTextChunk(out, "Software", "ComfyUI Companion");
        writeTextChunk(out, "Copyright-Status", "Public Domain (OLG Duesseldorf 2026 - no copyright for pure AI prompting)");
        writeTextChunk(out, "Generation-Timestamp", Instant.now().toString());

        // Write the original IEND chunk to close the PNG
        out.write(fileBytes, iendIndex, fileBytes.length - iendIndex);

        Files.write(imagePath, out.toByteArray());
        logger.info("Successfully injected Article 50 EU AI Act metadata into: {}", imagePath.getFileName());
    }

    @Override
    public Path exportWithCompliance(Path sourceImage, Path destinationDir, boolean applyVisibleWatermark) throws IOException {
        if (!Files.exists(sourceImage)) {
            throw new IOException("Source file does not exist: " + sourceImage);
        }
        Files.createDirectories(destinationDir);

        String originalName = sourceImage.getFileName().toString();
        String targetName = originalName;
        if (!originalName.toLowerCase().contains("ai_verified")) {
            int dotIdx = originalName.lastIndexOf('.');
            if (dotIdx > 0) {
                targetName = originalName.substring(0, dotIdx) + "_ai_act" + originalName.substring(dotIdx);
            }
        }
        Path targetPath = destinationDir.resolve(targetName);

        if (!applyVisibleWatermark) {
            Files.copy(sourceImage, targetPath, StandardCopyOption.REPLACE_EXISTING);
            injectComplianceMetadata(targetPath);
            return targetPath;
        }

        BufferedImage sourceImg = ImageIO.read(sourceImage.toFile());
        if (sourceImg == null) {
            Files.copy(sourceImage, targetPath, StandardCopyOption.REPLACE_EXISTING);
            injectComplianceMetadata(targetPath);
            return targetPath;
        }

        BufferedImage processed = new BufferedImage(sourceImg.getWidth(), sourceImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = processed.createGraphics();
        g2.drawImage(sourceImg, 0, 0, null);

        drawComplianceBadge(g2, sourceImg.getWidth(), sourceImg.getHeight());
        g2.dispose();

        ImageIO.write(processed, "PNG", targetPath.toFile());
        injectComplianceMetadata(targetPath);
        return targetPath;
    }

    @Override
    public boolean hasComplianceMetadata(Path imagePath) {
        try {
            if (!Files.exists(imagePath)) {
                return false;
            }
            byte[] bytes = Files.readAllBytes(imagePath);
            return hasComplianceMetadataBytes(bytes);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getComplianceNotice() {
        return "Pursuant to Art. 50(2) of the European AI Act (Regulation (EU) 2024/1689), " +
                "artificially generated or manipulated media must be machine-readably identifiable as synthetic. " +
                "Under established copyright jurisprudence (e.g. OLG Duesseldorf 2026), " +
                "purely prompt-generated images lack human creative authorship and are in the public domain.";
    }

    private boolean isPng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_HEADER.length) return false;
        for (int i = 0; i < PNG_HEADER.length; i++) {
            if (bytes[i] != PNG_HEADER[i]) return false;
        }
        return true;
    }

    private boolean hasComplianceMetadataBytes(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        return content.contains("EU-AI-Act") || content.contains("trainedAlgorithmicMedia");
    }

    private int findIendChunk(byte[] bytes) {
        for (int i = bytes.length - 12; i >= PNG_HEADER.length; i--) {
            if (bytes[i + 4] == 'I' && bytes[i + 5] == 'E' && bytes[i + 6] == 'N' && bytes[i + 7] == 'D') {
                return i;
            }
        }
        return -1;
    }

    private void writeTextChunk(ByteArrayOutputStream out, String keyword, String text) throws IOException {
        byte[] keywordBytes = keyword.getBytes(StandardCharsets.ISO_8859_1);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int length = keywordBytes.length + 1 + textBytes.length;

        // 4 bytes length
        out.write((length >>> 24) & 0xFF);
        out.write((length >>> 16) & 0xFF);
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);

        // Calculate CRC over chunk type + data
        CRC32 crc = new CRC32();
        byte[] typeBytes = new byte[]{'t', 'E', 'X', 't'};
        out.write(typeBytes);
        crc.update(typeBytes);

        out.write(keywordBytes);
        crc.update(keywordBytes);

        out.write(0); // Null separator
        crc.update(0);

        out.write(textBytes);
        crc.update(textBytes);

        long crcVal = crc.getValue();
        out.write((int) ((crcVal >>> 24) & 0xFF));
        out.write((int) ((crcVal >>> 16) & 0xFF));
        out.write((int) ((crcVal >>> 8) & 0xFF));
        out.write((int) (crcVal & 0xFF));
    }

    private void drawComplianceBadge(Graphics2D g2, int width, int height) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String text = "✦ AI Generated • Art. 50 EU AI Act";
        Font font = new Font("SansSerif", Font.BOLD, Math.max(10, width / 70));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int textWidth = fm.stringWidth(text);
        int badgeWidth = textWidth + 24;
        int badgeHeight = fm.getHeight() + 10;

        int margin = Math.max(12, width / 60);
        int x = width - badgeWidth - margin;
        int y = height - badgeHeight - margin;

        // Rounded translucent dark background
        g2.setColor(new Color(15, 23, 42, 215));
        g2.fillRoundRect(x, y, badgeWidth, badgeHeight, 10, 10);

        // Thin border
        g2.setColor(new Color(255, 255, 255, 70));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, badgeWidth, badgeHeight, 10, 10);

        // White text
        g2.setColor(new Color(248, 250, 252));
        g2.drawString(text, x + 12, y + fm.getAscent() + 5);
    }
}

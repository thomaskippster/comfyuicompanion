package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.service.impl.AiActComplianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiActComplianceServiceTest {

    private AiActComplianceService complianceService;

    @BeforeEach
    void setUp() {
        complianceService = new AiActComplianceService();
    }

    @Test
    void shouldInjectComplianceMetadataIntoPngFile(@TempDir Path tempDir) throws IOException {
        Path testPng = tempDir.resolve("test_image.png");
        createDummyPng(testPng, 100, 100);

        assertThat(complianceService.hasComplianceMetadata(testPng)).isFalse();

        complianceService.injectComplianceMetadata(testPng);

        assertThat(complianceService.hasComplianceMetadata(testPng)).isTrue();

        byte[] bytes = Files.readAllBytes(testPng);
        String str = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(str).contains("EU-AI-Act");
        assertThat(str).contains("trainedAlgorithmicMedia");
        assertThat(str).contains("ComfyUI Companion");
        assertThat(str).contains("OLG Duesseldorf 2026");
    }

    @Test
    void shouldExportWithWatermarkAndMetadata(@TempDir Path tempDir) throws IOException {
        Path sourcePng = tempDir.resolve("original.png");
        Path exportDir = tempDir.resolve("export");
        createDummyPng(sourcePng, 300, 200);

        Path exported = complianceService.exportWithCompliance(sourcePng, exportDir, true);

        assertThat(Files.exists(exported)).isTrue();
        assertThat(complianceService.hasComplianceMetadata(exported)).isTrue();

        BufferedImage readImg = ImageIO.read(exported.toFile());
        assertThat(readImg).isNotNull();
        assertThat(readImg.getWidth()).isEqualTo(300);
        assertThat(readImg.getHeight()).isEqualTo(200);
    }

    private void createDummyPng(Path path, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, width, height);
        g2.dispose();
        ImageIO.write(img, "PNG", path.toFile());
    }
}

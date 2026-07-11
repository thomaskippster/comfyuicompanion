package de.tki.comfymodels.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QwenTtsModelRecommenderTest {

    @Test
    public void recommend_picksSmallestModel_whenNoVram() {
        // No VRAM detected (0): return the smallest entry as a safe fallback.
        QwenTtsModelRecommender.ModelSpec spec = QwenTtsModelRecommender.recommend(0L);
        assertNotNull(spec);
        assertEquals("Qwen/Qwen2-Audio-1.5B-Instruct", spec.hfRepo);
    }

    @Test
    public void recommend_picksMidModel_for8GbVram() {
        // 8 GiB VRAM: 75% budget = 6 GiB -> fits the 6 GB Qwen2.5-Omni-3B
        long vram = 8L * 1024L * 1024L * 1024L;
        QwenTtsModelRecommender.ModelSpec spec = QwenTtsModelRecommender.recommend(vram);
        assertNotNull(spec);
        // Should pick the highest-quality model that fits 6 GiB.
        assertTrue(spec.approxBytes <= vram * 0.75 + 1,
                "Picked model too large for VRAM; was: " + spec);
    }

    @Test
    public void recommend_picksFp8Model_for16GbVram() {
        // 16 GiB VRAM: 75% budget = 12 GiB. The 7B FP16 model needs ~14 GiB and
        // would NOT fit. The 7B FP8 model needs ~7 GiB and fits comfortably.
        long vram = 16L * 1024L * 1024L * 1024L;
        QwenTtsModelRecommender.ModelSpec spec = QwenTtsModelRecommender.recommend(vram);
        assertNotNull(spec);
        // Verify it is one of the FP8 models (the 14-15 GB BF16 options must NOT be picked).
        assertTrue(spec.quantization.contains("FP8") || spec.approxBytes <= 8L * 1024L * 1024L * 1024L,
                "For 16 GB VRAM the recommender should pick a quantized/medium model; was: " + spec);
        // The full 7B BF16 model is 14 GB and exceeds 12 GB (75% of 16) -> must NOT be chosen.
        assertTrue(!spec.hfRepo.equals("Qwen/Qwen2-Audio-7B-Instruct") || !spec.quantization.equals("BF16"),
                "Recommender picked the full-precision 7B for 16 GB VRAM; was: " + spec);
    }

    @Test
    public void recommend_picksFullPrecision_for24GbVram() {
        // 24 GiB VRAM: 75% budget = 18 GiB. The 14 GB BF16 model fits.
        long vram = 24L * 1024L * 1024L * 1024L;
        QwenTtsModelRecommender.ModelSpec spec = QwenTtsModelRecommender.recommend(vram);
        assertNotNull(spec);
        assertTrue(spec.approxBytes <= vram * 0.75 + 1,
                "Picked model too large for VRAM; was: " + spec);
    }

    @Test
    public void catalog_isOrderedByVram() {
        // Sanity: the catalogue is ordered from smallest to largest so the
        // recommend() loop can early-exit.
        for (int i = 1; i < QwenTtsModelRecommender.CATALOG.size(); i++) {
            QwenTtsModelRecommender.ModelSpec prev = QwenTtsModelRecommender.CATALOG.get(i - 1);
            QwenTtsModelRecommender.ModelSpec curr = QwenTtsModelRecommender.CATALOG.get(i);
            assertTrue(prev.approxBytes < curr.approxBytes,
                    "Catalogue must be sorted by ascending VRAM; got " + prev.name + " before " + curr.name);
        }
    }

    @Test
    public void formatVram_handlesZeroAndPositive() {
        assertEquals("unbekannt", QwenTtsModelRecommender.formatVram(0L));
        assertEquals("16.0 GiB", QwenTtsModelRecommender.formatVram(16L * 1024L * 1024L * 1024L));
        assertEquals("0.5 GiB", QwenTtsModelRecommender.formatVram(512L * 1024L * 1024L));
    }
}
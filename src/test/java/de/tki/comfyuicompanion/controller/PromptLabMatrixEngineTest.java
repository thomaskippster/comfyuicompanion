package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.controller.PromptLabMatrixEngine.MatrixCell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PromptLabMatrixEngine} verifying dynamic prompt expansion and XY matrix cell generation.
 */
public class PromptLabMatrixEngineTest {

    @Test
    public void testHasMatrixSyntaxAndExpansion() {
        String template = "{cyberpunk|steampunk|watercolor} {cat|fox} in the forest";
        assertThat(PromptLabMatrixEngine.hasMatrixSyntax(template)).isTrue();
        assertThat(PromptLabMatrixEngine.hasMatrixSyntax("normal prompt without braces")).isFalse();

        List<String> expanded = PromptLabMatrixEngine.expandPromptVariations(template);
        assertThat(expanded).containsExactly(
                "cyberpunk cat in the forest",
                "cyberpunk fox in the forest",
                "steampunk cat in the forest",
                "steampunk fox in the forest",
                "watercolor cat in the forest",
                "watercolor fox in the forest"
        );
    }

    @Test
    public void testBuildMatrixCellsWithCfgSweep() {
        List<MatrixCell> cells = PromptLabMatrixEngine.buildMatrixCells(
                "{neon|pastel} city",
                "4.0, 7.5",
                7.0,
                20,
                2,
                1000L
        );

        // 2 prompts * 2 CFGs * 2 seeds = 8 cells
        assertThat(cells).hasSize(8);
        assertThat(cells.get(0).positivePrompt()).isEqualTo("neon city");
        assertThat(cells.get(0).cfg()).isEqualTo(4.0);
        assertThat(cells.get(0).seed()).isEqualTo(1000L);
        assertThat(cells.get(1).seed()).isEqualTo(1001L);
    }
}

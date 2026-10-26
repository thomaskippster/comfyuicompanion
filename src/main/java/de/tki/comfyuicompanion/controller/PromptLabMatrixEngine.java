package de.tki.comfyuicompanion.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands dynamic prompt matrix expressions (such as {@code "{cyberpunk|steampunk|watercolor} cat"})
 * and multi-parameter sweeps (CFG scales, step counts, seeds) into discrete execution cells.
 */
public class PromptLabMatrixEngine {
    private static final Logger logger = LoggerFactory.getLogger(PromptLabMatrixEngine.class);

    private static final int MAX_COMBINATIONS = 16;
    private static final Pattern BRACE_GROUP_PATTERN = Pattern.compile("\\{([^{}]+)\\}");

    /**
     * Represents a single cell in a Prompt Matrix / XY-Grid batch execution.
     *
     * @param index          zero-based cell index
     * @param label          concise human-readable cell label for the comparison grid
     * @param positivePrompt expanded positive prompt for this cell
     * @param cfg            CFG scale for this cell
     * @param steps          sampling steps for this cell
     * @param seed           generation seed for this cell
     */
    public record MatrixCell(
            int index,
            String label,
            String positivePrompt,
            double cfg,
            int steps,
            long seed
    ) {}

    /**
     * Checks whether a prompt string contains dynamic matrix syntax {@code {option1|option2}}.
     *
     * @param prompt the prompt text to inspect
     * @return {@code true} if at least one brace group with pipe-separated alternatives exists
     */
    public static boolean hasMatrixSyntax(String prompt) {
        if (prompt == null || !prompt.contains("{") || !prompt.contains("|") || !prompt.contains("}")) {
            return false;
        }
        Matcher m = BRACE_GROUP_PATTERN.matcher(prompt);
        while (m.find()) {
            if (m.group(1).contains("|")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Expands all {@code {a|b|c}} choice groups in a prompt into their Cartesian product,
     * capped at {@link #MAX_COMBINATIONS} variations.
     *
     * @param templatePrompt prompt containing optional {@code {a|b}} groups
     * @return ordered list of expanded prompt strings
     */
    public static List<String> expandPromptVariations(String templatePrompt) {
        if (templatePrompt == null || templatePrompt.isBlank()) {
            return Collections.singletonList("");
        }
        List<String> current = new ArrayList<>();
        current.add(templatePrompt.trim());

        while (true) {
            boolean expandedAny = false;
            List<String> next = new ArrayList<>();

            for (String item : current) {
                Matcher m = BRACE_GROUP_PATTERN.matcher(item);
                if (m.find() && m.group(1).contains("|")) {
                    expandedAny = true;
                    String prefix = item.substring(0, m.start());
                    String suffix = item.substring(m.end());
                    String[] options = m.group(1).split("\\|");
                    for (String opt : options) {
                        String trimmedOpt = opt.trim();
                        if (!trimmedOpt.isEmpty() && next.size() < MAX_COMBINATIONS) {
                            next.add((prefix + trimmedOpt + suffix).replaceAll("\\s{2,}", " ").trim());
                        }
                    }
                } else {
                    next.add(item);
                }
            }

            current = next;
            if (!expandedAny || current.size() >= MAX_COMBINATIONS) {
                break;
            }
        }

        Set<String> unique = new LinkedHashSet<>(current);
        return new ArrayList<>(unique);
    }

    /**
     * Builds a complete list of {@link MatrixCell} items combining expanded prompt variations,
     * comma-separated CFG values, and seed variations.
     *
     * @param promptTemplate prompt template (may include {@code {a|b}} syntax)
     * @param cfgValuesCsv   comma-separated CFG values (e.g. {@code "4.0, 7.0"}) or single value
     * @param defaultCfg     fallback CFG scale if CSV is blank
     * @param defaultSteps   sampling steps
     * @param seedCount      number of distinct seeds per prompt/CFG pair (1..4)
     * @param baseSeed       starting seed value
     * @return list of matrix cells capped at {@link #MAX_COMBINATIONS}
     */
    public static List<MatrixCell> buildMatrixCells(String promptTemplate,
                                                    String cfgValuesCsv,
                                                    double defaultCfg,
                                                    int defaultSteps,
                                                    int seedCount,
                                                    long baseSeed) {
        List<String> prompts = expandPromptVariations(promptTemplate);
        List<Double> cfgs = parseCfgList(cfgValuesCsv, defaultCfg);
        int clampedSeeds = Math.max(1, Math.min(4, seedCount));

        List<MatrixCell> cells = new ArrayList<>();
        int idx = 0;

        for (String p : prompts) {
            for (double cfg : cfgs) {
                for (int s = 0; s < clampedSeeds; s++) {
                    if (idx >= MAX_COMBINATIONS) {
                        return cells;
                    }
                    long cellSeed = baseSeed + s;
                    String shortPrompt = p.length() > 36 ? p.substring(0, 33) + "..." : p;
                    String label = String.format(java.util.Locale.US, "#%d: %s (CFG %.1f)", idx + 1, shortPrompt, cfg);
                    cells.add(new MatrixCell(idx, label, p, cfg, defaultSteps, cellSeed));
                    idx++;
                }
            }
        }
        return cells;
    }

    /**
     * Parses a comma-separated list of CFG values into doubles.
     *
     * @param csv        comma-separated numbers
     * @param defaultCfg fallback value if none are valid
     * @return list of CFG values
     */
    public static List<Double> parseCfgList(String csv, double defaultCfg) {
        List<Double> result = new ArrayList<>();
        if (csv != null && !csv.isBlank()) {
            for (String part : csv.split("[,;]")) {
                try {
                    double val = Double.parseDouble(part.trim());
                    if (val >= 0.0 && val <= 50.0) {
                        result.add(val);
                    }
                } catch (NumberFormatException ignored) {
            logger.trace("Invalid numeric format: {}", ignored.getMessage());
        }
            }
        }
        if (result.isEmpty()) {
            result.add(defaultCfg);
        }
        return result;
    }
}

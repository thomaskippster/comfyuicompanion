package de.tki.comfyuicompanion.ui.workflow;

import com.formdev.flatlaf.FlatClientProperties;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * Enterprise reusable search input component with document-driven debounce timer.
 * Captures all text mutations (keystrokes, clipboard paste, undo) and coalesces search
 * triggers to prevent redundant UI filtering and remote API query spam.
 *
 * @author Senior Java Developer
 */
public class DebouncedSearchField extends JTextField {

    private static final int DEFAULT_DEBOUNCE_MS = 300;

    private final Timer debounceTimer;
    private Consumer<String> searchCallback;

    /**
     * Constructs a DebouncedSearchField with placeholder and default 300ms debounce interval.
     *
     * @param placeholder placeholder text
     */
    public DebouncedSearchField(String placeholder) {
        this(placeholder, 15, DEFAULT_DEBOUNCE_MS);
    }

    /**
     * Constructs a DebouncedSearchField with placeholder, column width, and debounce interval.
     *
     * @param placeholder placeholder text
     * @param columns     number of columns
     * @param debounceMs  debounce delay in milliseconds
     */
    public DebouncedSearchField(String placeholder, int columns, int debounceMs) {
        super(columns);

        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder != null ? placeholder : "Search...");
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, SvgIconFactory.get(AppIcon.SEARCH, 13));
        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        putClientProperty(FlatClientProperties.STYLE, "arc: 8");

        this.debounceTimer = new Timer(debounceMs, e -> {
            if (searchCallback != null) {
                searchCallback.accept(getText() != null ? getText().trim() : "");
            }
        });
        this.debounceTimer.setRepeats(false);

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                restartTimer();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                restartTimer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                restartTimer();
            }
        });
    }

    /**
     * Sets the consumer callback invoked with the sanitized search text when debounce expires.
     *
     * @param searchCallback consumer of trimmed search text
     */
    public void setOnSearch(Consumer<String> searchCallback) {
        this.searchCallback = searchCallback;
    }

    /**
     * Sets a simple runnable callback invoked when debounce expires.
     *
     * @param callback action to execute
     */
    public void setOnSearch(Runnable callback) {
        this.searchCallback = text -> {
            if (callback != null) {
                callback.run();
            }
        };
    }

    /**
     * Immediately triggers the search callback without waiting for the debounce delay.
     */
    public void triggerSearchImmediately() {
        debounceTimer.stop();
        if (searchCallback != null) {
            searchCallback.accept(getText() != null ? getText().trim() : "");
        }
    }

    private void restartTimer() {
        if (debounceTimer.isRunning()) {
            debounceTimer.restart();
        } else {
            debounceTimer.start();
        }
    }
}

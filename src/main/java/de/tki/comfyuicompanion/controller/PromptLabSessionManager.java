package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages saving and loading Prompt Lab sessions and UI control states to/from persistent configuration.
 */
public class PromptLabSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabSessionManager.class);

    private final ConfigService configService;

    public PromptLabSessionManager(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Serializes the current PromptLabView UI controls into JSON and saves them via ConfigService.
     *
     * @param view the PromptLabView instance
     */
    public void saveSession(PromptLabView view) {
        if (configService == null || view == null) return;
        try {
            JSONObject session = new JSONObject();
            if (view.getPromptEnvCombo() != null) {
                session.put("environment_index", view.getPromptEnvCombo().getSelectedIndex());
            }
            session.put("photorealistic", view.getChkPhotorealistic().isSelected());
            session.put("oil_painting", view.getChkOil().isSelected());
            session.put("unreal_engine", view.getChkEngine().isSelected());
            session.put("anime", view.getChkAnime().isSelected());
            session.put("dark_fantasy", view.getChkFantasy().isSelected());
            session.put("watercolor", view.getChkSketch().isSelected());
            if (view.getPromptModelCombo() != null && view.getPromptModelCombo().getSelectedItem() != null) {
                session.put("model", view.getPromptModelCombo().getSelectedItem());
            }
            if (view.getPromptWidthSpinner() != null) {
                session.put("width", view.getPromptWidthSpinner().getValue());
            }
            if (view.getPromptHeightSpinner() != null) {
                session.put("height", view.getPromptHeightSpinner().getValue());
            }
            if (view.getPromptStepsSpinner() != null) {
                session.put("steps", view.getPromptStepsSpinner().getValue());
            }
            if (view.getPromptCfgSpinner() != null) {
                session.put("cfg", view.getPromptCfgSpinner().getValue());
            }
            configService.savePromptLabSession(session);
        } catch (Exception e) {
            logger.error("Failed to save Prompt Lab session: {}", e.getMessage());
        }
    }

    /**
     * Loads a persisted session from ConfigService and populates the controls in PromptLabView.
     *
     * @param view the PromptLabView instance
     */
    public void loadSession(PromptLabView view) {
        if (configService == null || view == null) return;
        try {
            JSONObject session = configService.getPromptLabSession();
            if (session == null) return;

            if (session.has("environment_index") && view.getPromptEnvCombo() != null) {
                int idx = session.getInt("environment_index");
                if (idx >= 0 && idx < view.getPromptEnvCombo().getItemCount()) {
                    view.getPromptEnvCombo().setSelectedIndex(idx);
                }
            }
            if (session.has("photorealistic")) view.getChkPhotorealistic().setSelected(session.getBoolean("photorealistic"));
            if (session.has("oil_painting")) view.getChkOil().setSelected(session.getBoolean("oil_painting"));
            if (session.has("unreal_engine")) view.getChkEngine().setSelected(session.getBoolean("unreal_engine"));
            if (session.has("anime")) view.getChkAnime().setSelected(session.getBoolean("anime"));
            if (session.has("dark_fantasy")) view.getChkFantasy().setSelected(session.getBoolean("dark_fantasy"));
            if (session.has("watercolor")) view.getChkSketch().setSelected(session.getBoolean("watercolor"));

            if (session.has("model") && view.getPromptModelCombo() != null) {
                String model = session.getString("model");
                boolean found = false;
                for (int i = 0; i < view.getPromptModelCombo().getItemCount(); i++) {
                    if (model.equals(view.getPromptModelCombo().getItemAt(i))) {
                        view.getPromptModelCombo().setSelectedIndex(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    view.getPromptModelCombo().addItem(model);
                    view.getPromptModelCombo().setSelectedItem(model);
                }
            }
            if (session.has("width") && view.getPromptWidthSpinner() != null) {
                Object wVal = session.get("width");
                if (wVal instanceof Number n) {
                    view.getPromptWidthSpinner().setValue(n.intValue());
                }
            }
            if (session.has("height") && view.getPromptHeightSpinner() != null) {
                Object hVal = session.get("height");
                if (hVal instanceof Number n) {
                    view.getPromptHeightSpinner().setValue(n.intValue());
                }
            }
            if (session.has("steps") && view.getPromptStepsSpinner() != null) {
                Object stepsVal = session.get("steps");
                if (stepsVal instanceof Number n) {
                    view.getPromptStepsSpinner().setValue(n.intValue());
                }
            }
            if (session.has("cfg") && view.getPromptCfgSpinner() != null) {
                Object cfgVal = session.get("cfg");
                if (cfgVal instanceof Number n) {
                    view.getPromptCfgSpinner().setValue(n.doubleValue());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load Prompt Lab session: {}", e.getMessage());
        }
    }
}

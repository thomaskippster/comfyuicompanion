package de.tki.comfyuicompanion.ui.panel;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ProfileManager;
import de.tki.comfyuicompanion.ui.GlassPanel;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dedicated UI panel for displaying, selecting, and managing ComfyUI launch profiles.
 * Supports CRUD actions (New, Edit, Duplicate, Delete) integrated with {@link ProfileManager}.
 */
public class LaunchProfilePanel extends GlassPanel {

    private final JFrame parentFrame;
    private final ConfigService configService;
    private final ProfileManager profileManager;
    private final DefaultListModel<LaunchProfile> profileListModel = new DefaultListModel<>();
    private final JList<LaunchProfile> profileList = new JList<>(profileListModel);
    private final JLabel descLabel = new JLabel("Select a profile to view details.");

    private Consumer<LaunchProfile> onProfileSelected;

    /**
     * Constructs a new LaunchProfilePanel.
     *
     * @param parentFrame the application parent frame for dialogs
     * @param configService the configuration service
     * @param profileManager the launch profile manager
     */
    public LaunchProfilePanel(JFrame parentFrame,
                              ConfigService configService,
                              ProfileManager profileManager) {
        this(parentFrame, configService, profileManager, null);
    }

    /**
     * Constructs a new LaunchProfilePanel with a selection callback.
     *
     * @param parentFrame the application parent frame for dialogs
     * @param configService the configuration service
     * @param profileManager the launch profile manager
     * @param onProfileSelected consumer notified when a profile is selected
     */
    public LaunchProfilePanel(JFrame parentFrame,
                              ConfigService configService,
                              ProfileManager profileManager,
                              Consumer<LaunchProfile> onProfileSelected) {
        this.parentFrame = parentFrame;
        this.configService = configService;
        this.profileManager = profileManager;
        this.onProfileSelected = onProfileSelected;

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(320, 0));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Launch Profiles");
        profilesHeader.putClientProperty("FlatLaf.styleClass", "h3");
        add(profilesHeader, BorderLayout.NORTH);

        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                LaunchProfile p = (LaunchProfile) value;
                Component c = super.getListCellRendererComponent(list, " " + (p != null ? p.name() : ""), index, isSelected, cellHasFocus);
                if (c instanceof JLabel label) {
                    label.setPreferredSize(new Dimension(0, 35));
                    label.setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 14));
                }
                return c;
            }
        });

        profileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                if (configService != null) {
                    configService.setActiveProfile(selected.id());
                }
                updateSelectedProfileInfo(selected);
                if (onProfileSelected != null) {
                    onProfileSelected.accept(selected);
                }
            }
        });

        JScrollPane profileScroll = new JScrollPane(profileList);
        profileScroll.setBorder(BorderFactory.createEmptyBorder());
        profileScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        add(profileScroll, BorderLayout.CENTER);

        JPanel profileButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        profileButtons.setOpaque(false);

        JButton addBtn = new JButton("Add", SvgIconFactory.get(AppIcon.ADD));
        JButton removeBtn = new JButton("Remove", SvgIconFactory.get(AppIcon.REMOVE));

        addBtn.addActionListener(e -> onAddProfile());
        removeBtn.addActionListener(e -> onDeleteProfile());

        profileButtons.add(addBtn);
        profileButtons.add(removeBtn);

        add(profileButtons, BorderLayout.SOUTH);
    }

    private void onAddProfile() {
        JTextField nameField = new JTextField();
        JTextField argsField = new JTextField("--listen, --port, 8188");
        Object[] message = { "Profile Name:", nameField, "Arguments (comma-separated):", argsField };
        int option = JOptionPane.showConfirmDialog(parentFrame, message, "Add Launch Profile", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            List<String> args = java.util.Arrays.stream(argsField.getText().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            LaunchProfile newProfile = new LaunchProfile(
                    UUID.randomUUID().toString(), nameField.getText().trim(), "User-defined profile.",
                    false, "python", args, new HashMap<>()
            );
            List<LaunchProfile> profiles = new ArrayList<>(profileManager.loadProfiles());
            profiles.add(newProfile);
            saveAndRefresh(profiles, newProfile);
        }
    }

    private void onEditProfile() {
        LaunchProfile selected = profileList.getSelectedValue();
        if (selected == null) return;
        if ("default".equals(selected.id())) {
            JOptionPane.showMessageDialog(parentFrame, "Cannot edit the default profile.", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextField nameField = new JTextField(selected.name());
        JTextField descField = new JTextField(selected.description() != null ? selected.description() : "");
        String currentArgs = selected.cliArguments() != null ? String.join(", ", selected.cliArguments()) : "";
        JTextField argsField = new JTextField(currentArgs);
        Object[] message = {
                "Profile Name:", nameField,
                "Description:", descField,
                "Arguments (comma-separated):", argsField
        };
        int option = JOptionPane.showConfirmDialog(parentFrame, message, "Edit Launch Profile", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            List<String> args = java.util.Arrays.stream(argsField.getText().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            LaunchProfile updated = new LaunchProfile(
                    selected.id(), nameField.getText().trim(), descField.getText().trim(),
                    selected.executeInWsl(), selected.pythonPath(), args, selected.envVars()
            );
            List<LaunchProfile> profiles = new ArrayList<>(profileManager.loadProfiles());
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).id().equals(selected.id())) {
                    profiles.set(i, updated);
                    break;
                }
            }
            saveAndRefresh(profiles, updated);
        }
    }

    private void onDuplicateProfile() {
        LaunchProfile selected = profileList.getSelectedValue();
        if (selected == null) return;
        String newName = selected.name() + " (Copy)";
        LaunchProfile copy = new LaunchProfile(
                UUID.randomUUID().toString(),
                newName,
                selected.description(),
                selected.executeInWsl(),
                selected.pythonPath(),
                selected.cliArguments() != null ? new ArrayList<>(selected.cliArguments()) : new ArrayList<>(),
                selected.envVars() != null ? new HashMap<>(selected.envVars()) : new HashMap<>()
        );
        List<LaunchProfile> profiles = new ArrayList<>(profileManager.loadProfiles());
        profiles.add(copy);
        saveAndRefresh(profiles, copy);
    }

    private void onDeleteProfile() {
        LaunchProfile selected = profileList.getSelectedValue();
        if (selected != null) {
            if ("default".equals(selected.id())) {
                JOptionPane.showMessageDialog(parentFrame, "Cannot delete default profiles.", "Information", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(parentFrame, "Delete profile '" + selected.name() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                List<LaunchProfile> profiles = new ArrayList<>(profileManager.loadProfiles());
                profiles.removeIf(p -> p.id().equals(selected.id()));
                saveAndRefresh(profiles, null);
            }
        }
    }

    private void saveAndRefresh(List<LaunchProfile> profiles, LaunchProfile targetToSelect) {
        try {
            profileManager.saveProfiles(profiles);
            refreshProfiles();
            if (targetToSelect != null) {
                profileList.setSelectedValue(targetToSelect, true);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parentFrame, "Error saving profile: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Reloads all launch profiles from the profile manager and updates the list model.
     */
    public void refreshProfiles() {
        profileListModel.clear();
        List<LaunchProfile> profiles = profileManager != null ? profileManager.loadProfiles() : List.of();

        if (profiles.isEmpty() && profileManager != null) {
            LaunchProfile def = new LaunchProfile(
                    "default",
                    "Default (Local)",
                    "Starts ComfyUI with standard local settings (--listen 127.0.0.1).",
                    false, "python",
                    List.of("--listen", "127.0.0.1", "--port", "8188"), new HashMap<>()
            );
            profiles = new ArrayList<>(profiles);
            profiles.add(def);
            try {
                profileManager.saveProfiles(profiles);
            } catch (IOException ignored) {}
        }

        for (LaunchProfile p : profiles) {
            profileListModel.addElement(p);
        }

        String activeId = configService != null ? configService.getActiveProfile() : null;
        int targetIdx = -1;
        for (int i = 0; i < profileListModel.size(); i++) {
            if (profileListModel.get(i).id().equals(activeId)) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx == -1 && !profileListModel.isEmpty()) {
            targetIdx = 0;
        }
        if (targetIdx != -1) {
            profileList.setSelectedIndex(targetIdx);
            updateSelectedProfileInfo(profileListModel.get(targetIdx));
        }
    }

    /**
     * Updates the profile description label with details from the selected profile.
     *
     * @param selected the selected launch profile
     */
    public void updateSelectedProfileInfo(LaunchProfile selected) {
        if (selected == null) return;
        if (descLabel != null) {
            String desc = selected.description();
            if (desc == null || desc.isBlank()) {
                desc = "No description provided for this profile.";
            }
            descLabel.setText("<html><body style='width: 280px;'>" + desc + "</body></html>");
        }
    }

    /**
     * Gets the currently selected launch profile.
     *
     * @return the selected profile or null
     */
    public LaunchProfile getSelectedProfile() {
        return profileList.getSelectedValue();
    }

    /**
     * Gets the profile selection list.
     *
     * @return the profile list component
     */
    public JList<LaunchProfile> getProfileList() {
        return profileList;
    }

    /**
     * Gets the profile list model.
     *
     * @return the profile list model
     */
    public DefaultListModel<LaunchProfile> getProfileListModel() {
        return profileListModel;
    }

    /**
     * Gets the profile description label.
     *
     * @return the description label
     */
    public JLabel getDescLabel() {
        return descLabel;
    }

    /**
     * Sets the selection callback invoked when a profile is selected.
     *
     * @param onProfileSelected consumer notified on selection change
     */
    public void setOnProfileSelected(Consumer<LaunchProfile> onProfileSelected) {
        this.onProfileSelected = onProfileSelected;
    }
}

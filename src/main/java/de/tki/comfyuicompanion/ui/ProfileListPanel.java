package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Dedicated UI panel for displaying and managing ComfyUI launch profiles.
 */
public class ProfileListPanel extends GlassPanel {

    private final DefaultListModel<LaunchProfile> profileListModel;
    private final JList<LaunchProfile> profileList;
    private final JButton addProfileBtn;
    private final JButton removeProfileBtn;

    public ProfileListPanel(Consumer<LaunchProfile> onProfileSelected,
                            Runnable onAddProfile,
                            Consumer<LaunchProfile> onRemoveProfile) {
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(320, 0));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Startprofile");
        profilesHeader.putClientProperty("FlatLaf.styleClass", "h3");
        add(profilesHeader, BorderLayout.NORTH);

        profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                LaunchProfile p = (LaunchProfile) value;
                Component c = super.getListCellRendererComponent(list, " " + (p != null ? p.name() : ""), index, isSelected, cellHasFocus);
                if (c instanceof JLabel) {
                    ((JLabel) c).setPreferredSize(new Dimension(0, 35));
                    ((JLabel) c).setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 14));
                }
                return c;
            }
        });

        profileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            if (onProfileSelected != null) {
                onProfileSelected.accept(profileList.getSelectedValue());
            }
        });

        JScrollPane profileScroll = new JScrollPane(profileList);
        profileScroll.setBorder(BorderFactory.createEmptyBorder());
        profileScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        add(profileScroll, BorderLayout.CENTER);

        JPanel profileButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        profileButtons.setOpaque(false);
        addProfileBtn = new JButton("Add", SvgIconFactory.get(AppIcon.ADD));
        removeProfileBtn = new JButton("Remove", SvgIconFactory.get(AppIcon.REMOVE));
        profileButtons.add(addProfileBtn);
        profileButtons.add(removeProfileBtn);
        add(profileButtons, BorderLayout.SOUTH);

        addProfileBtn.addActionListener(e -> {
            if (onAddProfile != null) onAddProfile.run();
        });

        removeProfileBtn.addActionListener(e -> {
            if (onRemoveProfile != null) onRemoveProfile.accept(profileList.getSelectedValue());
        });
    }

    public DefaultListModel<LaunchProfile> getModel() {
        return profileListModel;
    }

    public JList<LaunchProfile> getList() {
        return profileList;
    }

    public JButton getAddButton() {
        return addProfileBtn;
    }

    public JButton getRemoveButton() {
        return removeProfileBtn;
    }
}

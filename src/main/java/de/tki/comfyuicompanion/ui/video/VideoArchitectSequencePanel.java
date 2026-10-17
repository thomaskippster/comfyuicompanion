package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Timeline sequence panel containing toolbar action buttons and the sequence card list.
 */
public class VideoArchitectSequencePanel extends JPanel {

    private final DefaultListModel<Scene> timelineListModel = new DefaultListModel<>();
    private final JList<Scene> timelineListView;
    private final JScrollPane timelineScroll;

    private final JButton addSceneBtn;
    private final JButton editSceneBtn;
    private final JButton visualFilterBtn;
    private final JButton playVideoBtn;
    private final JButton moveUpBtn;
    private final JButton moveDownBtn;
    private final JButton deleteBtn;
    private final JButton clearListBtn;

    /**
     * Constructs a new VideoArchitectSequencePanel with the specified action callbacks.
     *
     * @param onAddScene       callback when Add Scene button is clicked
     * @param onEditScene      callback when Edit Scene is triggered
     * @param onVisualFilter   callback when OpenCV visual filter is clicked
     * @param onPlayVideo      callback when Play Video is clicked
     * @param onMoveScene      callback when Up/Down reordering is clicked
     * @param onDeleteScene    callback when Delete button is clicked
     * @param onClearAll       callback when Clear All is clicked
     * @param onSceneSelected  callback when a scene item is selected
     */
    public VideoArchitectSequencePanel(Runnable onAddScene,
                                      Consumer<Scene> onEditScene,
                                      Consumer<Scene> onVisualFilter,
                                      Runnable onPlayVideo,
                                      Consumer<Integer> onMoveScene,
                                      Runnable onDeleteScene,
                                      Runnable onClearAll,
                                      Consumer<Scene> onSceneSelected) {
        setLayout(new BorderLayout(8, 8));
        setOpaque(false);

        timelineListView = new JList<>(timelineListModel);
        timelineListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        timelineListView.setCellRenderer(new SceneCardCellRenderer());
        timelineListView.setBackground(ThemeManager.isDarkMode() ? new Color(15, 17, 22) : Color.WHITE);
        timelineListView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Scene selected = timelineListView.getSelectedValue();
                if (selected != null) {
                    if (onSceneSelected != null) onSceneSelected.accept(selected);
                    if (e.getClickCount() == 2 && onEditScene != null) {
                        onEditScene.accept(selected);
                    }
                }
            }
        });

        timelineScroll = new JScrollPane(timelineListView);
        timelineScroll.setOpaque(false);
        timelineScroll.getViewport().setOpaque(false);
        timelineScroll.getViewport().setBackground(ThemeManager.isDarkMode() ? new Color(15, 17, 22) : Color.WHITE);
        timelineScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.setOpaque(false);

        addSceneBtn = new JButton("Add Scene", SvgIconFactory.get(AppIcon.ADD));
        addSceneBtn.addActionListener(e -> { if (onAddScene != null) onAddScene.run(); });
        toolbar.add(addSceneBtn);

        editSceneBtn = new JButton("Edit", SvgIconFactory.get(AppIcon.EDIT));
        editSceneBtn.addActionListener(e -> {
            Scene selected = timelineListView.getSelectedValue();
            if (onEditScene != null) onEditScene.accept(selected);
        });
        toolbar.add(editSceneBtn);

        visualFilterBtn = new JButton("Visual Filter (OpenCV)", SvgIconFactory.get(AppIcon.PALETTE));
        visualFilterBtn.setToolTipText("Adjust frame contrast and brightness with native OpenCV real-time preview.");
        visualFilterBtn.addActionListener(e -> {
            Scene selected = timelineListView.getSelectedValue();
            if (onVisualFilter != null) onVisualFilter.accept(selected);
        });
        toolbar.add(visualFilterBtn);

        playVideoBtn = new JButton("Play Video", SvgIconFactory.get(AppIcon.PLAY));
        playVideoBtn.addActionListener(e -> { if (onPlayVideo != null) onPlayVideo.run(); });
        toolbar.add(playVideoBtn);

        moveUpBtn = new JButton("Up");
        moveUpBtn.addActionListener(e -> { if (onMoveScene != null) onMoveScene.accept(-1); });
        toolbar.add(moveUpBtn);

        moveDownBtn = new JButton("Down");
        moveDownBtn.addActionListener(e -> { if (onMoveScene != null) onMoveScene.accept(1); });
        toolbar.add(moveDownBtn);

        deleteBtn = new JButton("Delete", SvgIconFactory.get(AppIcon.DELETE));
        deleteBtn.addActionListener(e -> { if (onDeleteScene != null) onDeleteScene.run(); });
        toolbar.add(deleteBtn);

        clearListBtn = new JButton("Clear All");
        clearListBtn.addActionListener(e -> { if (onClearAll != null) onClearAll.run(); });
        toolbar.add(clearListBtn);

        add(toolbar, BorderLayout.NORTH);
        add(timelineScroll, BorderLayout.CENTER);
    }

    public DefaultListModel<Scene> getTimelineListModel() { return timelineListModel; }
    public JList<Scene> getTimelineListView() { return timelineListView; }
    public JScrollPane getTimelineScroll() { return timelineScroll; }
    public JButton getAddSceneBtn() { return addSceneBtn; }
    public JButton getEditSceneBtn() { return editSceneBtn; }
    public JButton getVisualFilterBtn() { return visualFilterBtn; }
    public JButton getPlayVideoBtn() { return playVideoBtn; }
    public JButton getMoveUpBtn() { return moveUpBtn; }
    public JButton getMoveDownBtn() { return moveDownBtn; }
    public JButton getDeleteBtn() { return deleteBtn; }
    public JButton getClearListBtn() { return clearListBtn; }
}

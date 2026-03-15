package org.intellij.lang.xslfo.preview;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import icons.XslFoIcons;
import org.intellij.lang.xslfo.run.XslFoRunConfiguration;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

public class PdfPreviewPanel extends JPanel {

  private final Project project;
  private final VirtualFile file;
  private JComboBox<XslFoRunConfiguration> myConfigurationCombo;
  private JComboBox<String> myXmlInputCombo;
  private JButton myGoToXmlButton;

  public PdfPreviewPanel(Project project, VirtualFile file) {
    this.project = project;
    this.file = file;
    initUI();
  }

  private void initUI() {
    setLayout(new BorderLayout());

    List<XslFoRunConfiguration> matchingConfigurations = findMatchingConfigurations();
    if (matchingConfigurations.isEmpty()) {
      add(new JLabel("no launch configurations for template available", SwingConstants.CENTER),
          BorderLayout.CENTER);
      return;
    }

    myConfigurationCombo = new ComboBox<>(
        matchingConfigurations.toArray(new XslFoRunConfiguration[0]));
    myConfigurationCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      JLabel label = new JLabel(value != null ? value.getName() : "");
      if (isSelected) {
        label.setOpaque(true);
        label.setBackground(list.getSelectionBackground());
        label.setForeground(list.getSelectionForeground());
      }
      return label;
    });

    JButton editButton = new JButton(AllIcons.Actions.Edit);
    editButton.setToolTipText("Edit launch configuration");
    editButton.addActionListener(e -> editSelectedConfiguration());

    myXmlInputCombo = new ComboBox<>();
    myXmlInputCombo.setRenderer(new XmlPathRenderer(myXmlInputCombo));
    myGoToXmlButton = new JButton(AllIcons.Actions.EditSource);
    myGoToXmlButton.setToolTipText("Go to selected XML input file");
    myGoToXmlButton.addActionListener(e -> goToSelectedXmlInput());

    myConfigurationCombo.addActionListener(e -> updateXmlInputsForSelectedConfiguration());
    updateXmlInputsForSelectedConfiguration();

    JPanel controls = new JPanel(new GridBagLayout());
    int iconCellWidth = Math.max(XslFoIcons.FopLogo.getIconWidth(), AllIcons.FileTypes.Xml.getIconWidth());
    int iconCellHeight = Math.max(XslFoIcons.FopLogo.getIconHeight(), AllIcons.FileTypes.Xml.getIconHeight());
    Dimension iconCellSize = new Dimension(iconCellWidth, iconCellHeight);

    JLabel fopIconLabel = new JLabel(XslFoIcons.FopLogo, SwingConstants.CENTER);
    fopIconLabel.setPreferredSize(iconCellSize);
    JLabel xmlIconLabel = new JLabel(AllIcons.FileTypes.Xml, SwingConstants.CENTER);
    xmlIconLabel.setPreferredSize(iconCellSize);

    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    c.insets = JBUI.insets(0, 0, 8, 8);
    c.anchor = GridBagConstraints.WEST;
    c.gridx = 0;
    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;
    controls.add(fopIconLabel, c);

    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    controls.add(myConfigurationCombo, c);

    c.gridx = 2;
    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;
    c.insets = JBUI.insets(0, 0, 8, 0);
    controls.add(editButton, c);

    c.gridy = 1;
    c.gridx = 0;
    c.insets = JBUI.insets(0, 0, 0, 8);
    controls.add(xmlIconLabel, c);

    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    controls.add(myXmlInputCombo, c);

    c.gridx = 2;
    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;
    c.insets = JBUI.insets(0, 0, 0, 0);
    controls.add(myGoToXmlButton, c);

    add(controls, BorderLayout.NORTH);
  }

  private void updateXmlInputsForSelectedConfiguration() {
    String currentSelection = (String) myXmlInputCombo.getSelectedItem();
    List<String> xmlInputs = getXmlInputPathsForSelectedConfiguration();
    myXmlInputCombo.setModel(new DefaultComboBoxModel<>(xmlInputs.toArray(new String[0])));
    if (xmlInputs.isEmpty()) {
      myXmlInputCombo.setEnabled(false);
      myGoToXmlButton.setEnabled(false);
      return;
    }
    myXmlInputCombo.setEnabled(true);
    myGoToXmlButton.setEnabled(true);
    if (currentSelection != null && xmlInputs.contains(currentSelection)) {
      myXmlInputCombo.setSelectedItem(currentSelection);
    } else {
      myXmlInputCombo.setSelectedIndex(0);
    }
  }

  private List<String> getXmlInputPathsForSelectedConfiguration() {
    XslFoRunConfiguration selected = (XslFoRunConfiguration) myConfigurationCombo.getSelectedItem();
    if (selected == null) {
      return List.of();
    }
    return selected.getSettings().getXmlInputFilesPointers().stream()
        .map(pointer -> pointer != null ? pointer.getPresentableUrl() : null)
        .filter(path -> path != null && !path.isBlank())
        .distinct()
        .toList();
  }

  private void editSelectedConfiguration() {
    XslFoRunConfiguration selected = (XslFoRunConfiguration) myConfigurationCombo.getSelectedItem();
    if (selected == null) {
      return;
    }
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).findSettings(selected);
    if (settings == null) {
      Messages.showWarningDialog(project,
          "Could not open launch configuration editor for selection.",
          "XSL-FO Preview");
      return;
    }
    RunDialog.editConfiguration(project, settings, "Edit Launch Configuration");
    updateXmlInputsForSelectedConfiguration();
  }

  private void goToSelectedXmlInput() {
    String selectedPath = (String) myXmlInputCombo.getSelectedItem();
    if (selectedPath == null || selectedPath.isBlank()) {
      return;
    }
    String normalized = FileUtil.toSystemIndependentName(selectedPath);
    VirtualFile selectedFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(normalized);
    if (selectedFile == null) {
      Messages.showWarningDialog(project,
          "Could not locate selected XML input file:\n" + selectedPath,
          "XSL-FO Preview");
      return;
    }
    FileEditorManager.getInstance(project).openFile(selectedFile, true);
  }

  private static String leftEllipsis(String text, FontMetrics metrics, int availableWidth) {
    if (text == null || text.isEmpty() || availableWidth <= 0) {
      return text;
    }
    if (metrics.stringWidth(text) <= availableWidth) {
      return text;
    }
    String ellipsis = "...";
    int ellipsisWidth = metrics.stringWidth(ellipsis);
    if (ellipsisWidth >= availableWidth) {
      return ellipsis;
    }
    int firstVisibleIndex = text.length() - 1;
    while (firstVisibleIndex > 0
        && metrics.stringWidth(ellipsis + text.substring(firstVisibleIndex)) <= availableWidth) {
      firstVisibleIndex--;
    }
    if (metrics.stringWidth(ellipsis + text.substring(firstVisibleIndex)) > availableWidth
        && firstVisibleIndex < text.length() - 1) {
      firstVisibleIndex++;
    }
    return ellipsis + text.substring(firstVisibleIndex);
  }

  private static final class XmlPathRenderer extends DefaultListCellRenderer {
    private final JComboBox<String> owner;

    private XmlPathRenderer(JComboBox<String> owner) {
      this.owner = owner;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
          cellHasFocus);
      String fullPath = value instanceof String ? (String) value : "";
      int availableWidth = index >= 0 ? list.getWidth() : owner.getWidth();
      if (availableWidth > 0) {
        int padding = JBUI.scale(18);
        label.setText(leftEllipsis(fullPath, label.getFontMetrics(label.getFont()),
            Math.max(availableWidth - padding, 0)));
      } else {
        label.setText(fullPath);
      }
      label.setToolTipText(fullPath.isBlank() ? null : fullPath);
      return label;
    }
  }

  private List<XslFoRunConfiguration> findMatchingConfigurations() {
    List<XslFoRunConfiguration> matches = new ArrayList<>();
    for (RunConfiguration configuration : RunManager.getInstance(project).getAllConfigurationsList()) {
      if (!(configuration instanceof XslFoRunConfiguration xslFoRunConfiguration)) {
        continue;
      }
      VirtualFile xsltFile = xslFoRunConfiguration.findXsltFile();
      if (xsltFile != null && xsltFile.equals(file)) {
        matches.add(xslFoRunConfiguration);
      }
    }
    return matches;
  }
}

package org.intellij.lang.xslfo.preview;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import icons.XslFoIcons;
import org.intellij.lang.xslfo.run.XslFoRunConfiguration;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public class PdfPreviewPanel extends JPanel {

  private final Project project;
  private final VirtualFile file;

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

    JComboBox<XslFoRunConfiguration> comboBox = new ComboBox<>(
        matchingConfigurations.toArray(new XslFoRunConfiguration[0]));
    comboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      JLabel label = new JLabel(value != null ? value.getName() : "");
      if (isSelected) {
        label.setOpaque(true);
        label.setBackground(list.getSelectionBackground());
        label.setForeground(list.getSelectionForeground());
      }
      return label;
    });

    JPanel selectorRow = new JPanel(new BorderLayout(8, 0));
    selectorRow.add(new JLabel(XslFoIcons.FopLogo), BorderLayout.WEST);
    selectorRow.add(comboBox, BorderLayout.CENTER);

    JButton editButton = new JButton(AllIcons.Actions.Edit);
    editButton.setToolTipText("Edit launch configuration");
    editButton.addActionListener(e -> editSelectedConfiguration(comboBox));
    selectorRow.add(editButton, BorderLayout.EAST);

    add(selectorRow, BorderLayout.NORTH);
  }

  private void editSelectedConfiguration(JComboBox<XslFoRunConfiguration> comboBox) {
    XslFoRunConfiguration selected = (XslFoRunConfiguration) comboBox.getSelectedItem();
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

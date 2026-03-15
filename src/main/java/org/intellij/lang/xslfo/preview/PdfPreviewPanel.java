package org.intellij.lang.xslfo.preview;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import com.intellij.util.messages.MessageBusConnection;
import icons.XslFoIcons;
import org.intellij.lang.xslfo.run.XslFoRunConfigType;
import org.intellij.lang.xslfo.run.XslFoPreviewRenderer;
import org.intellij.lang.xslfo.run.XslFoRunConfiguration;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PdfPreviewPanel extends JPanel {

  private final Project project;
  private final VirtualFile file;
  private final PdfBoxViewerPanel myPdfViewerPanel = new PdfBoxViewerPanel();
  private final AtomicLong myRenderRequestCounter = new AtomicLong(0);
  private final JTabbedPane myResultTabs = new JTabbedPane();
  private final JTextArea myDiagnosticsArea = new JTextArea();

  private JComboBox<XslFoRunConfiguration> myConfigurationCombo;
  private JComboBox<String> myXmlInputCombo;
  private JButton myGoToXmlButton;
  private boolean myUpdatingXmlInputs;
  private File myRenderedPreviewFile;
  private MessageBusConnection mySaveListenerConnection;

  public PdfPreviewPanel(Project project, VirtualFile file) {
    this.project = project;
    this.file = file;
    initUI();
    registerAutoRefreshOnSave();
  }

  private void initUI() {
    setLayout(new BorderLayout());

    List<XslFoRunConfiguration> matchingConfigurations = findMatchingConfigurations();
    if (matchingConfigurations.isEmpty()) {
      add(createNoConfigurationsPanel(), BorderLayout.CENTER);
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
    makeCompactIconButton(editButton);

    myXmlInputCombo = new ComboBox<>();
    myXmlInputCombo.setRenderer(new XmlPathRenderer(myXmlInputCombo));

    myGoToXmlButton = new JButton(AllIcons.Actions.EditSource);
    myGoToXmlButton.setToolTipText("Go to selected XML input file");
    myGoToXmlButton.addActionListener(e -> goToSelectedXmlInput());
    makeCompactIconButton(myGoToXmlButton);

    myConfigurationCombo.addActionListener(e -> {
      if (myUpdatingXmlInputs) {
        return;
      }
      updateXmlInputsForSelectedConfiguration();
      renderPreviewAsync();
    });
    myXmlInputCombo.addActionListener(e -> {
      if (myUpdatingXmlInputs) {
        return;
      }
      renderPreviewAsync();
    });

    updateXmlInputsForSelectedConfiguration();

    JPanel controls = new JPanel(new GridBagLayout());
    int iconCellWidth = Math.max(
        XslFoIcons.FopLogo.getIconWidth(),
        AllIcons.FileTypes.Xml.getIconWidth());
    int iconCellHeight = Math.max(
        XslFoIcons.FopLogo.getIconHeight(),
        AllIcons.FileTypes.Xml.getIconHeight());
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
    myDiagnosticsArea.setEditable(false);
    myDiagnosticsArea.setLineWrap(true);
    myDiagnosticsArea.setWrapStyleWord(true);
    myDiagnosticsArea.setText("No warnings or errors.");

    myResultTabs.addTab("Preview", myPdfViewerPanel);
    myResultTabs.addTab("Messages", new JScrollPane(myDiagnosticsArea));

    add(myResultTabs, BorderLayout.CENTER);
    myPdfViewerPanel.setRefreshAction(this::renderPreviewAsync);
    scheduleInitialRender();
  }

  private void updateXmlInputsForSelectedConfiguration() {
    myUpdatingXmlInputs = true;
    try {
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
    } finally {
      myUpdatingXmlInputs = false;
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
    renderPreviewAsync();
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

  private JPanel createNoConfigurationsPanel() {
    JPanel panel = new JPanel(new GridBagLayout()); // centers the content panel

    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

    JLabel label = new JLabel("No launch configuration available for this template.");
    label.setAlignmentX(Component.CENTER_ALIGNMENT);

    JButton createButton = new JButton("Create Launch Configuration");
    createButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    createButton.addActionListener(e -> createLaunchConfigurationFromPreview());

    content.add(label);
    content.add(Box.createVerticalStrut(8)); // spacing
    content.add(createButton);

    panel.add(content);

    return panel;
  }

  private void createLaunchConfigurationFromPreview() {
    RunManager runManager = RunManager.getInstance(project);
    ConfigurationFactory factory = XslFoRunConfigType.getInstance().getConfigurationFactories()[0];
    String suggestedName = file.getNameWithoutExtension() + " (XSL-FO)";
    RunnerAndConfigurationSettings settings = runManager.createConfiguration(suggestedName, factory);

    RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof XslFoRunConfiguration xslFoRunConfiguration)) {
      Messages.showErrorDialog(project,
          "Could not create an XSL-FO launch configuration.",
          "XSL-FO Preview");
      return;
    }
    xslFoRunConfiguration.setXsltFile(file);

    boolean accepted = RunDialog.editConfiguration(project, settings, "Create Launch Configuration");
    if (!accepted) {
      return;
    }

    runManager.addConfiguration(settings);
    runManager.setSelectedConfiguration(settings);
    rebuildUi();
  }

  private void rebuildUi() {
    removeAll();
    initUI();
    revalidate();
    repaint();
  }

  private void registerAutoRefreshOnSave() {
    mySaveListenerConnection = project.getMessageBus().connect();
    mySaveListenerConnection.subscribe(FileDocumentManagerListener.TOPIC,
        new FileDocumentManagerListener() {
          @Override
          public void afterDocumentSaved(Document document) {
            VirtualFile savedFile = FileDocumentManager.getInstance().getFile(document);
            if (savedFile == null || !isRelevantSavedFile(savedFile)) {
              return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
              if (!project.isDisposed()) {
                renderPreviewAsync();
              }
            });
          }
        });
  }

  private boolean isRelevantSavedFile(VirtualFile savedFile) {
    if (savedFile.equals(file)) {
      return true;
    }
    if (myConfigurationCombo == null) {
      return false;
    }
    XslFoRunConfiguration selected =
        (XslFoRunConfiguration) myConfigurationCombo.getSelectedItem();
    if (selected == null) {
      return false;
    }

    String savedPath = FileUtil.toSystemIndependentName(savedFile.getPath());
    String xsltPath = selected.getSettings().getXsltFilePointer() != null
        ? selected.getSettings().getXsltFilePointer().getPresentableUrl()
        : null;
    if (isSamePath(savedPath, xsltPath)) {
      return true;
    }

    return selected.getSettings().getXmlInputFilesPointers().stream()
        .map(pointer -> pointer != null ? pointer.getPresentableUrl() : null)
        .filter(path -> path != null && !path.isBlank())
        .anyMatch(path -> isSamePath(savedPath, path));
  }

  private static boolean isSamePath(String leftPath, String rightPath) {
    if (leftPath == null || rightPath == null) {
      return false;
    }
    return FileUtil.pathsEqual(
        FileUtil.toSystemIndependentName(leftPath),
        FileUtil.toSystemIndependentName(rightPath));
  }

  private void renderPreviewAsync() {
    if (project.isDisposed()) {
      return;
    }
    if (myConfigurationCombo == null || myXmlInputCombo == null) {
      return;
    }
    if (!project.isInitialized()) {
      myPdfViewerPanel.showLoading("Preview will be available after project initialization.");
      return;
    }

    XslFoRunConfiguration selectedConfiguration =
        (XslFoRunConfiguration) myConfigurationCombo.getSelectedItem();
    String selectedXmlInput = (String) myXmlInputCombo.getSelectedItem();
    if (selectedConfiguration == null) {
      myPdfViewerPanel.showError("No launch configuration selected.");
      return;
    }
    if (selectedXmlInput == null || selectedXmlInput.isBlank()) {
      myPdfViewerPanel.showError("Selected launch configuration has no XML input.");
      return;
    }

    int preferredPageIndex = myPdfViewerPanel.getCurrentPageIndex();
    long requestId = myRenderRequestCounter.incrementAndGet();
    myPdfViewerPanel.showLoading("Rendering preview...");
    myDiagnosticsArea.setText("Rendering preview...");

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      XslFoPreviewRenderer.PreviewRenderResult renderResult =
          XslFoPreviewRenderer.renderPreview(selectedConfiguration, selectedXmlInput);
      ApplicationManager.getApplication().invokeLater(
          () -> applyRenderResult(requestId, preferredPageIndex, renderResult));
    });
  }

  private void scheduleInitialRender() {
    if (project.isDisposed()) {
      return;
    }
    if (project.isInitialized()) {
      renderPreviewAsync();
      return;
    }
    StartupManager.getInstance(project).runAfterOpened(() -> {
      if (!project.isDisposed()) {
        renderPreviewAsync();
      }
    });
  }

  private void applyRenderResult(long requestId, int preferredPageIndex,
                                 XslFoPreviewRenderer.PreviewRenderResult renderResult) {
    File renderedPdf = renderResult.outputFile();
    if (requestId != myRenderRequestCounter.get()) {
      deletePreviewFile(renderedPdf);
      return;
    }

    updateDiagnosticsArea(renderResult.diagnostics());

    if (!renderResult.success()) {
      myPdfViewerPanel.showError("Preview rendering failed. See Messages tab.");
      myResultTabs.setSelectedIndex(1);
      deletePreviewFile(renderedPdf);
      return;
    }

    if (renderedPdf == null || !renderedPdf.exists()) {
      myPdfViewerPanel.showError("Preview rendering finished without output. See Messages tab.");
      myResultTabs.setSelectedIndex(1);
      return;
    }

    try {
      myPdfViewerPanel.setPdfFile(renderedPdf, preferredPageIndex);
      replaceRenderedPreviewFile(renderedPdf);
      myResultTabs.setSelectedIndex(0);
    } catch (IOException e) {
      deletePreviewFile(renderedPdf);
      myPdfViewerPanel.showError("Could not load rendered preview PDF. See Messages tab.");
      myDiagnosticsArea.append("\n[ERROR] Could not load rendered preview PDF: " + e.getMessage());
      myResultTabs.setSelectedIndex(1);
    }
  }

  private void updateDiagnosticsArea(List<XslFoPreviewRenderer.PreviewRenderMessage> diagnostics) {
    if (diagnostics == null || diagnostics.isEmpty()) {
      myDiagnosticsArea.setText("No warnings or errors.");
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (XslFoPreviewRenderer.PreviewRenderMessage diagnostic : diagnostics) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append('[').append(diagnostic.severity().name()).append("] ").append(diagnostic.message());
    }
    myDiagnosticsArea.setText(sb.toString());
    myDiagnosticsArea.setCaretPosition(0);
  }

  private void replaceRenderedPreviewFile(File newPreviewFile) {
    if (myRenderedPreviewFile != null && !myRenderedPreviewFile.equals(newPreviewFile)) {
      deletePreviewFile(myRenderedPreviewFile);
    }
    myRenderedPreviewFile = newPreviewFile;
  }

  private static void deletePreviewFile(File fileToDelete) {
    if (fileToDelete == null) {
      return;
    }
    try {
      if (fileToDelete.exists()) {
        fileToDelete.delete();
      }
    } catch (SecurityException ignore) {
      // Ignore cleanup failures for temporary preview files.
    }
  }

  private static void makeCompactIconButton(JButton button) {
    Icon icon = button.getIcon();
    int iconWidth = icon != null ? icon.getIconWidth() : 16;
    int iconHeight = icon != null ? icon.getIconHeight() : 16;
    int side = Math.max(iconWidth, iconHeight) + JBUI.scale(8);
    Dimension size = new Dimension(side, side);
    button.setMargin(JBUI.insets(2));
    button.setPreferredSize(size);
    button.setMinimumSize(size);
    button.setMaximumSize(size);
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

  public void dispose() {
    myRenderRequestCounter.incrementAndGet();
    if (mySaveListenerConnection != null) {
      mySaveListenerConnection.disconnect();
      mySaveListenerConnection = null;
    }
    myPdfViewerPanel.dispose();
    deletePreviewFile(myRenderedPreviewFile);
    myRenderedPreviewFile = null;
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

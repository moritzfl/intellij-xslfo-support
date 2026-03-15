package org.intellij.lang.xslfo.run.editor;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.intellij.lang.xslfo.XslFoSettings;
import org.intellij.lang.xslfo.run.ExecutionMode;
import org.intellij.lang.xslfo.run.OutputFormat;
import org.intellij.lang.xslfo.run.SettingsFileMode;
import org.intellij.lang.xslfo.run.XslFoRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Settings editor for XSL-FO run configurations.
 */
public class XslFoRunConfigurationEditor extends SettingsEditor<XslFoRunConfiguration> {

  private final Project myProject;

  private XsltFileField myXsltFile;
  private XmlInputFileField myXmlInputFile;
  private JPanel myComponent;
  private TextFieldWithBrowseButton myOutputFile;
  private JCheckBox myOpenOutputFile;
  private JCheckBox myUseTemporaryFiles;

  private JRadioButton myUsePluginOutputFormatRadio;
  private JRadioButton myUseCustomOutputFormatRadio;
  private JComboBox<OutputFormat> myOutputFormatCombo;

  private JRadioButton myUsePluginExecutionRadio;
  private JRadioButton myUseBundledFopRadio;
  private JRadioButton myUseBinaryFopRadio;
  private JLabel myExternalFopInfoLabel;
  private TextFieldWithBrowseButton myFopInstallationDir;

  private JRadioButton myUsePluginConfig;
  private JRadioButton myUseEmptyConfig;
  private JRadioButton myUseConfigFile;
  private TextFieldWithBrowseButton myUserConfigLocation;

  public XslFoRunConfigurationEditor(Project project) {
    this.myProject = project;
    buildUi();

    FileChooserDescriptor outputDescriptor =
        FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    myOutputFile.addActionListener(
        e -> FileChooser.chooseFile(outputDescriptor, myProject, null, file -> {
          if (file != null) {
            myOutputFile.setText(file.getPath().replace('/', java.io.File.separatorChar));
          }
        }));
    myUseTemporaryFiles.addActionListener(e -> updateComponentsState());

    myOutputFormatCombo.removeAllItems();
    for (OutputFormat f : OutputFormat.values()) {
      myOutputFormatCombo.addItem(f);
    }
    ButtonGroup outputGroup = new ButtonGroup();
    outputGroup.add(myUsePluginOutputFormatRadio);
    outputGroup.add(myUseCustomOutputFormatRadio);
    myUsePluginOutputFormatRadio.addActionListener(e -> updateComponentsState());
    myUseCustomOutputFormatRadio.addActionListener(e -> updateComponentsState());

    FileChooserDescriptor dirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myFopInstallationDir.addActionListener(
        e -> FileChooser.chooseFile(dirDescriptor, myProject, null, file -> {
          if (file != null) {
            myFopInstallationDir.setText(file.getPath().replace('/', java.io.File.separatorChar));
          }
        }));
    addStateUpdateDocumentListener(myFopInstallationDir);

    FileChooserDescriptor configFileDescriptor =
        new FileChooserDescriptor(true, false, false, false, false, true);
    myUserConfigLocation.addActionListener(
        e -> FileChooser.chooseFile(configFileDescriptor, myProject, null, file -> {
          if (file != null) {
            myUserConfigLocation.setText(file.getPath().replace('/', java.io.File.separatorChar));
          }
        }));
    addStateUpdateDocumentListener(myUserConfigLocation);

    ButtonGroup executionGroup = new ButtonGroup();
    executionGroup.add(myUsePluginExecutionRadio);
    executionGroup.add(myUseBundledFopRadio);
    executionGroup.add(myUseBinaryFopRadio);
    applyBundledFopVersionLabel();

    ButtonGroup configGroup = new ButtonGroup();
    configGroup.add(myUsePluginConfig);
    configGroup.add(myUseEmptyConfig);
    configGroup.add(myUseConfigFile);

    myUsePluginExecutionRadio.addActionListener(e -> updateComponentsState());
    myUseBundledFopRadio.addActionListener(e -> updateComponentsState());
    myUseBinaryFopRadio.addActionListener(e -> updateComponentsState());
    myUsePluginConfig.addActionListener(e -> updateComponentsState());
    myUseEmptyConfig.addActionListener(e -> updateComponentsState());
    myUseConfigFile.addActionListener(e -> updateComponentsState());

    refreshPluginOutputFormatLabel();
    updateComponentsState();
  }

  private void buildUi() {
    myXsltFile = new XsltFileField(myProject);
    myXmlInputFile = new XmlInputFileField(myProject, myXsltFile);
    myOutputFile = new TextFieldWithBrowseButton();
    myOpenOutputFile = new JCheckBox("Open saved file after execution", true);
    myUseTemporaryFiles = new JCheckBox("Use temporary file to save generated document");

    myUsePluginOutputFormatRadio = new JRadioButton("Use output format from plugin settings", true);
    myUseCustomOutputFormatRadio = new JRadioButton("Use output format:");
    myOutputFormatCombo = new JComboBox<>();

    myUsePluginExecutionRadio = new JRadioButton("Use plugin settings", true);
    myUseBundledFopRadio = new JRadioButton("Use bundled FOP");
    myUseBinaryFopRadio = new JRadioButton("Use external FOP (binary)");
    myExternalFopInfoLabel = new JLabel();
    myFopInstallationDir = new TextFieldWithBrowseButton();

    myUsePluginConfig = new JRadioButton("Use plugin settings", true);
    myUseEmptyConfig = new JRadioButton("Use empty settings");
    myUseConfigFile = new JRadioButton("Use settings file");
    myUserConfigLocation = new TextFieldWithBrowseButton();

    myComponent = FormBuilder.createFormBuilder()
        .addComponent(createInputPanel())
        .addComponent(createOutputPanel())
        .addComponent(createExecutionPanel())
        .addComponent(createConfigPanel())
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();
  }

  private JPanel createInputPanel() {
    JPanel panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("XSLT script file:", myXsltFile)
        .addComponent(myXmlInputFile)
        .getPanel();
    panel.setBorder(javax.swing.BorderFactory.createTitledBorder("Input"));
    return panel;
  }

  private JPanel createOutputPanel() {
    JPanel outputFormatRow = new JPanel(new BorderLayout());
    outputFormatRow.add(myUseCustomOutputFormatRadio, BorderLayout.WEST);
    outputFormatRow.add(myOutputFormatCombo, BorderLayout.CENTER);

    JPanel panel = FormBuilder.createFormBuilder()
        .addComponent(myUseTemporaryFiles)
        .addLabeledComponent("Save to File:", myOutputFile)
        .addComponent(myOpenOutputFile)
        .addComponent(myUsePluginOutputFormatRadio)
        .addComponent(outputFormatRow)
        .getPanel();
    panel.setBorder(javax.swing.BorderFactory.createTitledBorder("Output"));
    return panel;
  }

  private JPanel createExecutionPanel() {
    JPanel executionModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    executionModeRow.add(myUsePluginExecutionRadio);
    executionModeRow.add(myUseBundledFopRadio);
    executionModeRow.add(myUseBinaryFopRadio);

    JPanel panel = FormBuilder.createFormBuilder()
        .addComponent(executionModeRow)
        .addComponent(myExternalFopInfoLabel)
        .addLabeledComponent("FOP installation dir:", myFopInstallationDir)
        .getPanel();
    panel.setBorder(javax.swing.BorderFactory.createTitledBorder("FOP execution settings"));
    return panel;
  }

  private JPanel createConfigPanel() {
    JPanel configModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    configModeRow.add(myUsePluginConfig);
    configModeRow.add(myUseEmptyConfig);
    configModeRow.add(myUseConfigFile);

    JPanel panel = FormBuilder.createFormBuilder()
        .addComponent(configModeRow)
        .addLabeledComponent("Use settings file:", myUserConfigLocation)
        .getPanel();
    panel.setBorder(javax.swing.BorderFactory.createTitledBorder("FOP configuration settings"));
    return panel;
  }

  private void addStateUpdateDocumentListener(TextFieldWithBrowseButton field) {
    field.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateComponentsState();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateComponentsState();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        updateComponentsState();
      }
    });
  }

  private void applyBundledFopVersionLabel() {
    try {
      String version = null;
      java.io.InputStream is = XslFoRunConfigurationEditor.class.getClassLoader()
          .getResourceAsStream("META-INF/xslfo/bundled-fop-version.txt");
      if (is != null) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(is))) {
          String line = br.readLine();
          if (line != null && !line.trim().isEmpty()) {
            version = line.trim();
          }
        }
      }
      myUseBundledFopRadio.setText("Use bundled FOP (" + (version == null ? "unknown" : version) + ")");
    } catch (Throwable ignore) {
      // Keep default text if version cannot be determined
    }
  }

  @Override
  protected void resetEditorFrom(XslFoRunConfiguration configuration) {
    var settings = configuration.getSettings();
    myXsltFile.setText(
        settings.getXsltFilePointer() != null ? settings.getXsltFilePointer().getPresentableUrl() :
            null);
    List<String> xmlInputs = settings.getXmlInputFilesPointers().stream()
        .map(pointer -> pointer != null ? pointer.getPresentableUrl() : null)
        .filter(path -> path != null && !path.isBlank())
        .toList();
    myXmlInputFile.setXmlInputFiles(xmlInputs);
    myOutputFile.setText(settings.outputFile());
    myOpenOutputFile.setSelected(settings.openOutputFile());
    myUseTemporaryFiles.setSelected(settings.useTemporaryFiles());

    if (settings.usePluginOutputFormat()) {
      myUsePluginOutputFormatRadio.setSelected(true);
      myUseCustomOutputFormatRadio.setSelected(false);
    } else {
      myUsePluginOutputFormatRadio.setSelected(false);
      myUseCustomOutputFormatRadio.setSelected(true);
    }
    myOutputFormatCombo.setSelectedItem(settings.outputFormat());
    refreshPluginOutputFormatLabel();

    switch (settings.executionMode()) {
      case PLUGIN -> myUsePluginExecutionRadio.setSelected(true);
      case BUNDLED -> myUseBundledFopRadio.setSelected(true);
      case EXTERNAL -> myUseBinaryFopRadio.setSelected(true);
      default -> {
        // keep current selection
      }
    }
    myFopInstallationDir.setText(settings.fopInstallationDirOverride());

    switch (settings.configMode()) {
      case PLUGIN -> {
        myUsePluginConfig.setSelected(true);
        myUserConfigLocation.setText("");
      }
      case EMPTY -> {
        myUseEmptyConfig.setSelected(true);
        myUserConfigLocation.setText("");
      }
      case FILE -> {
        myUseConfigFile.setSelected(true);
        myUserConfigLocation.setText(settings.configFilePath());
      }
      default -> {
        // keep current selection
      }
    }

    updateComponentsState();
  }

  @Override
  protected void applyEditorTo(XslFoRunConfiguration configuration) {
    var settings = configuration.getSettings();
    if (myXsltFile.getText().isEmpty()) {
      settings = settings.withXsltFile(null);
    } else {
      configuration.setXsltFile(myXsltFile.getText());
      settings = configuration.getSettings();
    }
    configuration.setXmlInputFiles(myXmlInputFile.getXmlInputFiles());
    settings = configuration.getSettings();

    settings = settings.withOutputFile(myOutputFile.getText())
        .withOpenOutputFile(myOpenOutputFile.isSelected())
        .withUseTemporaryFiles(myUseTemporaryFiles.isSelected())
        .withExecutionMode(myUsePluginExecutionRadio.isSelected() ? ExecutionMode.PLUGIN :
            (myUseBundledFopRadio.isSelected() ? ExecutionMode.BUNDLED : ExecutionMode.EXTERNAL))
        .withFopInstallationDirOverride(myFopInstallationDir.getText());

    if (myUsePluginOutputFormatRadio.isSelected()) {
      settings = settings.withUsePluginOutputFormat(true);
    } else if (myUseCustomOutputFormatRadio.isSelected()) {
      settings = settings.withUsePluginOutputFormat(false).withOutputFormat(
          myOutputFormatCombo.getSelectedItem() instanceof OutputFormat
              ? (OutputFormat) myOutputFormatCombo.getSelectedItem()
              : OutputFormat.PDF);
    }

    if (myUsePluginConfig.isSelected()) {
      settings = settings.withConfigMode(SettingsFileMode.PLUGIN).withConfigFilePath(null);
    } else if (myUseEmptyConfig.isSelected()) {
      settings = settings.withConfigMode(SettingsFileMode.EMPTY).withConfigFilePath(null);
    } else {
      settings = settings.withConfigMode(SettingsFileMode.FILE)
          .withConfigFilePath(myUserConfigLocation.getText());
    }

    configuration.setSettings(settings);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myComponent;
  }

  private void refreshPluginOutputFormatLabel() {
    XslFoSettings plugin = XslFoSettings.getInstance();
    OutputFormat format = plugin != null ? plugin.getDefaultOutputFormat() : OutputFormat.PDF;
    myUsePluginOutputFormatRadio.setText(
        "Use output format from plugin settings (" + (format != null ? format.name() : "PDF") + ")");
  }

  private void updateComponentsState() {
    myOutputFile.setEnabled(!myUseTemporaryFiles.isSelected());
    myOpenOutputFile.setEnabled(!myUseTemporaryFiles.isSelected());
    if (myUseTemporaryFiles.isSelected()) {
      myOpenOutputFile.setSelected(true);
    }

    boolean pluginSelected = myUsePluginExecutionRadio.isSelected();
    boolean externalSelected = myUseBinaryFopRadio.isSelected();
    myFopInstallationDir.setEnabled(externalSelected);

    if (externalSelected) {
      String dir = myFopInstallationDir.getText();
      String message = (dir == null || dir.trim().isEmpty())
          ? "Using FOP from system PATH (command: 'fop')"
          : "Using FOP from installation directory: " + dir.trim();
      myExternalFopInfoLabel.setText(message);
      myExternalFopInfoLabel.setVisible(true);
    } else if (pluginSelected) {
      XslFoSettings plugin = XslFoSettings.getInstance();
      boolean pluginUsesExternal = plugin != null && !plugin.isUseBundledFop();
      if (pluginUsesExternal) {
        String dir = plugin.getFopInstallationDir();
        String message = (dir == null || dir.trim().isEmpty())
            ? "Using FOP from system PATH (command: 'fop')"
            : "Using FOP from installation directory: " + dir.trim();
        myExternalFopInfoLabel.setText(message);
        myExternalFopInfoLabel.setVisible(true);
      } else {
        myExternalFopInfoLabel.setText("");
        myExternalFopInfoLabel.setVisible(false);
      }
    } else {
      myExternalFopInfoLabel.setText("");
      myExternalFopInfoLabel.setVisible(false);
    }

    myUserConfigLocation.setEnabled(myUseConfigFile.isSelected());
    myOutputFormatCombo.setEnabled(myUseCustomOutputFormatRadio.isSelected());
    refreshPluginOutputFormatLabel();
  }
}

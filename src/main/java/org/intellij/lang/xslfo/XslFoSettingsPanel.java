package org.intellij.lang.xslfo;

import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.intellij.lang.xslfo.run.OutputFormat;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.FlowLayout;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/**
 * Settings panel for the global default settings for XSL-FO runs.
 */
public class XslFoSettingsPanel {
  private JComboBox<OutputFormat> myDefaultOutputFormat;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myFopInstallationDir;
  private TextFieldWithBrowseButton myUserConfigLocation;
  private JPanel myValidationPanel;
  private JLabel myWarningLabel;
  private JSeparator mySeparator;
  private JRadioButton myUseBundledFopRadio;
  private JRadioButton myUseBinaryFopRadio;
  private JLabel myBundledFopVersionLabel;

  public XslFoSettingsPanel() {
    buildUi();

    myDefaultOutputFormat.removeAllItems();
    for (OutputFormat f : OutputFormat.values()) {
      myDefaultOutputFormat.addItem(f);
    }

    myFopInstallationDir.addActionListener(
        e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            FileChooserDescriptorFactory.createSingleFolderDescriptor(), null, null, file -> {
              if (file != null) {
                myFopInstallationDir.setText(file.getPath().replace('/', java.io.File.separatorChar));
              }
            }));

    myUserConfigLocation.addActionListener(
        e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            FileChooserDescriptorFactory.createSingleFileDescriptor(XmlFileType.INSTANCE), null,
            null, file -> {
              if (file != null) {
                myUserConfigLocation.setText(
                    file.getPath().replace('/', java.io.File.separatorChar));
              }
            }));

    ButtonGroup group = new ButtonGroup();
    group.add(myUseBundledFopRadio);
    group.add(myUseBinaryFopRadio);
    myUseBundledFopRadio.addActionListener(e -> updateEnabledStates());
    myUseBinaryFopRadio.addActionListener(e -> updateEnabledStates());

    myUseBundledFopRadio.setText("Use bundled FOP (" + readBundledFopVersion() + ")");

    myWarningLabel.setIcon(AllIcons.General.BalloonError);
    MySettingsPanelChangeListener changeListener = new MySettingsPanelChangeListener();
    myFopInstallationDir.getTextField().getDocument().addDocumentListener(changeListener);
    myUserConfigLocation.getTextField().getDocument().addDocumentListener(changeListener);
    myPanel.addComponentListener(changeListener);

    updateEnabledStates();
  }

  private void buildUi() {
    myUseBundledFopRadio = new JRadioButton("Use bundled FOP");
    myUseBinaryFopRadio = new JRadioButton("Use FOP binary");
    myBundledFopVersionLabel = new JLabel();
    myFopInstallationDir = new TextFieldWithBrowseButton();
    myUserConfigLocation = new TextFieldWithBrowseButton();
    myDefaultOutputFormat = new JComboBox<>();
    mySeparator = new JSeparator();
    myWarningLabel = new JLabel();

    JPanel engineRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    engineRow.add(myUseBundledFopRadio);
    engineRow.add(myUseBinaryFopRadio);

    JPanel configPanel = FormBuilder.createFormBuilder()
        .addComponent(engineRow)
        .addComponent(myBundledFopVersionLabel)
        .addLabeledComponent("FOP installation directory (optional):", myFopInstallationDir)
        .addLabeledComponent("User configuration file (optional):", myUserConfigLocation)
        .addLabeledComponent("Default output format:", myDefaultOutputFormat)
        .getPanel();
    configPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("FOP Configuration"));

    myValidationPanel = FormBuilder.createFormBuilder()
        .addComponent(mySeparator)
        .addComponent(myWarningLabel)
        .getPanel();

    myPanel = FormBuilder.createFormBuilder()
        .addComponent(configPanel)
        .addComponentFillVertically(new JPanel(), 0)
        .addComponent(myValidationPanel)
        .getPanel();
  }

  private String readBundledFopVersion() {
    String version = null;
    try {
      java.io.InputStream is = XslFoSettingsPanel.class.getClassLoader()
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
    } catch (Throwable ignore) {
      // Ignore - use default version text if resource cannot be read
    }
    return version == null ? "unknown" : version;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public String getFopInstallationDir() {
    return myFopInstallationDir.getText();
  }

  public String getUserConfigLocation() {
    return myUserConfigLocation.getText();
  }

  public void setFopInstallationDir(String fopInstallationDir) {
    myFopInstallationDir.setText(fopInstallationDir);
  }

  public void setUserConfigLocation(String userConfigLocation) {
    myUserConfigLocation.setText(userConfigLocation);
  }

  public boolean isUseBundledFopSelected() {
    return myUseBundledFopRadio.isSelected();
  }

  public OutputFormat getDefaultOutputFormat() {
    Object sel = myDefaultOutputFormat.getSelectedItem();
    return sel instanceof OutputFormat ? (OutputFormat) sel : OutputFormat.PDF;
  }

  public void setDefaultOutputFormat(OutputFormat fmt) {
    myDefaultOutputFormat.setSelectedItem(fmt == null ? OutputFormat.PDF : fmt);
  }

  public void setUseBundledFopSelected(boolean useBundled) {
    myUseBundledFopRadio.setSelected(useBundled);
    myUseBinaryFopRadio.setSelected(!useBundled);
    updateEnabledStates();
  }

  private void updateEnabledStates() {
    boolean bundled = myUseBundledFopRadio.isSelected();
    myFopInstallationDir.setEnabled(!bundled);
    if (bundled) {
      myBundledFopVersionLabel.setVisible(false);
      myBundledFopVersionLabel.setText("");
    } else {
      String dir = myFopInstallationDir.getText();
      String text = (dir == null || dir.trim().isEmpty())
          ? "Using FOP from system PATH (command: 'fop')"
          : "Using FOP from installation directory: " + dir.trim();
      myBundledFopVersionLabel.setText(text);
      myBundledFopVersionLabel.setVisible(true);
    }
  }

  private String validateSettings() {
    String dir = myFopInstallationDir.getText();
    if (dir != null && !dir.isEmpty() && XslFoUtils.findFopExecutable(dir) == null) {
      return "<html><body><b>Error: </b>Selected FOP installation directory is invalid</body></html>";
    }
    return "";
  }

  private class MySettingsPanelChangeListener implements ComponentListener, DocumentListener {
    @Override
    public void componentShown(ComponentEvent e) {
      updateWarning();
    }

    @Override
    public void componentHidden(ComponentEvent e) {
      updateWarning();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
      updateWarning();
    }

    @Override
    public void componentResized(ComponentEvent e) {
      updateWarning();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      updateWarning();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      updateWarning();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      updateWarning();
    }

    private void updateWarning() {
      String errorMsg = validateSettings();
      if (errorMsg.isEmpty()) {
        mySeparator.setVisible(false);
        myWarningLabel.setVisible(false);
        myValidationPanel.setVisible(false);
      } else {
        mySeparator.setVisible(true);
        myWarningLabel.setVisible(true);
        myWarningLabel.setText(errorMsg);
        myValidationPanel.setVisible(true);
      }
      updateEnabledStates();
    }
  }
}

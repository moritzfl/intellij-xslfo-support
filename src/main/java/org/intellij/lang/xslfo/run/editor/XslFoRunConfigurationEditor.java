package org.intellij.lang.xslfo.run.editor;

import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;

import org.intellij.lang.xslfo.run.XslFoRunConfiguration;
import org.intellij.lang.xslfo.XslFoSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * @author Dmitry_Cherkas
 */
public class XslFoRunConfigurationEditor extends SettingsEditor<XslFoRunConfiguration> {

    private final Project myProject;

    private XsltFileField myXsltFile;
    private XmlInputFileField myXmlInputFile;
    private JPanel myComponent;
    private TextFieldWithBrowseButton myOutputFile;
    private JCheckBox myOpenOutputFile;
    private JCheckBox myUseTemporaryFiles;

    // Per-run FOP settings override UI
    private JCheckBox myUseDefaultFopSettings;
    private JRadioButton myUseBundledFopRadio;
    private JRadioButton myUseBinaryFopRadio;
    private JLabel myExternalFopInfoLabel;
    private TextFieldWithBrowseButton myFopInstallationDir;
    private TextFieldWithBrowseButton myUserConfigLocation;

    public XslFoRunConfigurationEditor(Project project) {
        this.myProject = project;

        // Replace deprecated addBrowseFolderListener with explicit chooser action
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
        myOutputFile.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, myProject, null, file -> {
            if (file != null) {
                myOutputFile.setText(file.getPath().replace('/', java.io.File.separatorChar));
            }
        }));
        myUseTemporaryFiles.addActionListener(e -> updateComponentsState());

        // FOP settings UI wiring
        if (myFopInstallationDir != null) {
            FileChooserDescriptor dirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            myFopInstallationDir.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(dirDescriptor, myProject, null, file -> {
                if (file != null) {
                    myFopInstallationDir.setText(file.getPath().replace('/', java.io.File.separatorChar));
                }
            }));
            // Update explanatory label live when the directory text changes
            myFopInstallationDir.getTextField();
            if (myFopInstallationDir.getTextField().getDocument() != null) {
                myFopInstallationDir.getTextField().getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) { updateComponentsState(); }
                    @Override
                    public void removeUpdate(DocumentEvent e) { updateComponentsState(); }
                    @Override
                    public void changedUpdate(DocumentEvent e) { updateComponentsState(); }
                });
            }
        }
        if (myUserConfigLocation != null) {
            FileChooserDescriptor fileDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
            myUserConfigLocation.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(fileDescriptor, myProject, null, file -> {
                if (file != null) {
                    myUserConfigLocation.setText(file.getPath().replace('/', java.io.File.separatorChar));
                }
            }));
            // Keep UI state in sync when user config changes (not strictly required for label, but harmless)
            myUserConfigLocation.getTextField();
            if (myUserConfigLocation.getTextField().getDocument() != null) {
                myUserConfigLocation.getTextField().getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) { updateComponentsState(); }
                    @Override
                    public void removeUpdate(DocumentEvent e) { updateComponentsState(); }
                    @Override
                    public void changedUpdate(DocumentEvent e) { updateComponentsState(); }
                });
            }
        }
        if (myUseBundledFopRadio != null && myUseBinaryFopRadio != null) {
            ButtonGroup group = new ButtonGroup();
            group.add(myUseBundledFopRadio);
            group.add(myUseBinaryFopRadio);

            // Set bundled FOP version text similar to Settings panel
            try {
                String version = null;
                java.io.InputStream is = XslFoRunConfigurationEditor.class.getClassLoader()
                        .getResourceAsStream("META-INF/xslfo/bundled-fop-version.txt");
                if (is != null) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                        String line = br.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            version = line.trim();
                        }
                    }
                }
                if (version == null) version = "unknown";
                myUseBundledFopRadio.setText("Use bundled FOP (" + version + ")");
            } catch (Throwable ignore) {
                // Keep default text if version cannot be determined
            }
        }
        if (myUseDefaultFopSettings != null) {
            myUseDefaultFopSettings.addActionListener(e -> updateComponentsState());
        }
        if (myUseBundledFopRadio != null) {
            myUseBundledFopRadio.addActionListener(e -> updateComponentsState());
        }
        if (myUseBinaryFopRadio != null) {
            myUseBinaryFopRadio.addActionListener(e -> updateComponentsState());
        }

        updateComponentsState();
    }

    private void createUIComponents() {
        myXsltFile = new XsltFileField(myProject);
        myXmlInputFile = new XmlInputFileField(myProject, myXsltFile);
    }


    /* SettingsEditor<XslFoRunConfiguration> implementation */
    @Override
    protected void resetEditorFrom(XslFoRunConfiguration s) {
        var settings = s.getSettings();
        myXsltFile.setText(settings.getXsltFilePointer() != null ? settings.getXsltFilePointer().getPresentableUrl() : null);
        myXmlInputFile.getChildComponent().setSelectedItem(settings.getXmlInputFilePointer() != null ? settings.getXmlInputFilePointer().getPresentableUrl() : null);
        myOutputFile.setText(settings.outputFile());
        myOpenOutputFile.setSelected(settings.openOutputFile());
        myUseTemporaryFiles.setSelected(settings.useTemporaryFiles());

        // FOP per-run override
        if (myUseDefaultFopSettings != null) {
            myUseDefaultFopSettings.setSelected(settings.usePluginDefaultFopSettings());
        }
        if (!settings.usePluginDefaultFopSettings()) {
            if (myUseBundledFopRadio != null) myUseBundledFopRadio.setSelected(settings.useBundledFopOverride());
            if (myUseBinaryFopRadio != null) myUseBinaryFopRadio.setSelected(!settings.useBundledFopOverride());
            if (myFopInstallationDir != null) myFopInstallationDir.setText(settings.fopInstallationDirOverride());
            if (myUserConfigLocation != null) myUserConfigLocation.setText(settings.userConfigLocationOverride());
        } else {
            // default selections
            if (myUseBundledFopRadio != null) myUseBundledFopRadio.setSelected(true);
            if (myUseBinaryFopRadio != null) myUseBinaryFopRadio.setSelected(false);
            if (myFopInstallationDir != null) myFopInstallationDir.setText("");
            if (myUserConfigLocation != null) myUserConfigLocation.setText("");
        }

        FileChooserDescriptor xmlDescriptor = myXmlInputFile.getDescriptor();

        final VirtualFile xmlInputFile = s.findXmlInputFile();
        if (xmlInputFile != null) {
            final Module contextModule = ProjectRootManager.getInstance(s.getProject()).getFileIndex().getModuleForFile(xmlInputFile);
            if (contextModule != null) {
                xmlDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, contextModule);
            }
        }
        updateComponentsState();
    }

    @Override
    protected void applyEditorTo(XslFoRunConfiguration s) {
        var settings = s.getSettings();
        // Update via immutable with* methods
        myXsltFile.getText();
        if (myXsltFile.getText().isEmpty()) {
            settings = settings.withXsltFile(null);
        } else {
            // The field stores a path string; keep using string-based setter utility from configuration for URL safety
            s.setXsltFile(myXsltFile.getText());
            settings = s.getSettings();
        }
        if (myXmlInputFile.getXmlInputFile() == null || myXmlInputFile.getXmlInputFile().isEmpty()) {
            settings = settings.withXmlInputFile(null);
        } else {
            s.setXmlInputFile(myXmlInputFile.getXmlInputFile());
            settings = s.getSettings();
        }
        settings = settings
                .withOutputFile(myOutputFile.getText())
                .withOpenOutputFile(myOpenOutputFile.isSelected())
                .withUseTemporaryFiles(myUseTemporaryFiles.isSelected());

        // Apply FOP per-run override
        if (myUseDefaultFopSettings != null && myUseDefaultFopSettings.isSelected()) {
            settings = settings.withUsePluginDefaultFopSettings(true);
        } else {
            settings = settings
                    .withUsePluginDefaultFopSettings(false)
                    .withUseBundledFopOverride(myUseBundledFopRadio != null && myUseBundledFopRadio.isSelected())
                    .withFopInstallationDirOverride(myFopInstallationDir != null ? myFopInstallationDir.getText() : null)
                    .withUserConfigLocationOverride(myUserConfigLocation != null ? myUserConfigLocation.getText() : null);
        }
        s.setSettings(settings);
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myComponent;
    }

    private void updateComponentsState() {
        myOutputFile.setEnabled(!myUseTemporaryFiles.isSelected());
        myOpenOutputFile.setEnabled(!myUseTemporaryFiles.isSelected());

        if (myUseTemporaryFiles.isSelected()) {
            myOpenOutputFile.setSelected(true);
        }
        // Enable/disable FOP override fields
        boolean useDefaults = myUseDefaultFopSettings != null && myUseDefaultFopSettings.isSelected();
        if (myUseBundledFopRadio != null) myUseBundledFopRadio.setEnabled(!useDefaults);
        if (myUseBinaryFopRadio != null) myUseBinaryFopRadio.setEnabled(!useDefaults);
        boolean externalSelected;
        String dirToUse = null;
        if (useDefaults) {
            XslFoSettings pluginSettings = XslFoSettings.getInstance();
            boolean pluginUsesBundled = pluginSettings == null || pluginSettings.isUseBundledFop();
            externalSelected = !pluginUsesBundled;
            if (pluginSettings != null) dirToUse = pluginSettings.getFopInstallationDir();
        } else {
            externalSelected = myUseBinaryFopRadio != null && myUseBinaryFopRadio.isSelected();
            if (myFopInstallationDir != null) dirToUse = myFopInstallationDir.getText();
        }
        if (myFopInstallationDir != null) myFopInstallationDir.setEnabled(!useDefaults && externalSelected);
        if (myUserConfigLocation != null) myUserConfigLocation.setEnabled(!useDefaults);
        if (myExternalFopInfoLabel != null) {
            if (externalSelected) {
                String msg;
                if (dirToUse == null || dirToUse.trim().isEmpty()) {
                    msg = "Using FOP from system PATH (command: 'fop')";
                } else {
                    msg = "Using FOP from installation directory: " + dirToUse.trim();
                }
                myExternalFopInfoLabel.setText(msg);
                myExternalFopInfoLabel.setVisible(true);
            } else {
                myExternalFopInfoLabel.setText("");
                myExternalFopInfoLabel.setVisible(false);
            }
        }
    }
}

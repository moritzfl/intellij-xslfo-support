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

    // FOP execution selection (bundled vs binary)
    private JRadioButton myUseBundledFopRadio;
    private JRadioButton myUseBinaryFopRadio;
    private JLabel myExternalFopInfoLabel;
    private TextFieldWithBrowseButton myFopInstallationDir;
    // FOP configuration (user config) source selection
    private JRadioButton myUsePluginConfig;
    private JRadioButton myUseEmptyConfig;
    private JRadioButton myUseConfigFile;
    private TextFieldWithBrowseButton myUserConfigLocation;

    public XslFoRunConfigurationEditor(Project project) {
        this.myProject = project;

        // Replace deprecated addBrowseFolderListener with explicit chooser action
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
        if (myOutputFile != null) {
            myOutputFile.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, myProject, null, file -> {
                if (file != null) {
                    myOutputFile.setText(file.getPath().replace('/', java.io.File.separatorChar));
                }
            }));
        }
        if (myUseTemporaryFiles != null) {
            myUseTemporaryFiles.addActionListener(e -> updateComponentsState());
        }

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
        // Group config mode radios
        if (myUsePluginConfig != null && myUseEmptyConfig != null && myUseConfigFile != null) {
            ButtonGroup cfgGroup = new ButtonGroup();
            cfgGroup.add(myUsePluginConfig);
            cfgGroup.add(myUseEmptyConfig);
            cfgGroup.add(myUseConfigFile);
        }
        if (myUseBundledFopRadio != null) {
            myUseBundledFopRadio.addActionListener(e -> updateComponentsState());
        }
        if (myUseBinaryFopRadio != null) {
            myUseBinaryFopRadio.addActionListener(e -> updateComponentsState());
        }
        if (myUsePluginConfig != null) myUsePluginConfig.addActionListener(e -> updateComponentsState());
        if (myUseEmptyConfig != null) myUseEmptyConfig.addActionListener(e -> updateComponentsState());
        if (myUseConfigFile != null) myUseConfigFile.addActionListener(e -> updateComponentsState());

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
        if (myOutputFile != null) myOutputFile.setText(settings.outputFile());
        if (myOpenOutputFile != null) myOpenOutputFile.setSelected(settings.openOutputFile());
        if (myUseTemporaryFiles != null) myUseTemporaryFiles.setSelected(settings.useTemporaryFiles());

        // FOP execution selection
        if (myUseBundledFopRadio != null) myUseBundledFopRadio.setSelected(settings.useBundledFopOverride());
        if (myUseBinaryFopRadio != null) myUseBinaryFopRadio.setSelected(!settings.useBundledFopOverride());
        if (myFopInstallationDir != null) myFopInstallationDir.setText(settings.fopInstallationDirOverride());

        // FOP configuration source
        if (myUsePluginConfig != null && myUseEmptyConfig != null && myUseConfigFile != null) {
            switch (settings.configMode()) {
                case PLUGIN -> {
                    myUsePluginConfig.setSelected(true);
                    if (myUserConfigLocation != null) myUserConfigLocation.setText("");
                }
                case EMPTY -> {
                    myUseEmptyConfig.setSelected(true);
                    if (myUserConfigLocation != null) myUserConfigLocation.setText("");
                }
                case FILE -> {
                    myUseConfigFile.setSelected(true);
                    if (myUserConfigLocation != null) myUserConfigLocation.setText(settings.configFilePath());
                }
            }
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
        if (myXsltFile.getText().isEmpty()) {
            settings = settings.withXsltFile(null);
        } else {
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
                .withUseTemporaryFiles(myUseTemporaryFiles.isSelected())
                .withUseBundledFopOverride(myUseBundledFopRadio != null && myUseBundledFopRadio.isSelected())
                .withFopInstallationDirOverride(myFopInstallationDir != null ? myFopInstallationDir.getText() : null);

        // Config source
        if (myUsePluginConfig != null && myUsePluginConfig.isSelected()) {
            settings = settings.withConfigMode(org.intellij.lang.xslfo.run.SettingsFileMode.PLUGIN)
                    .withConfigFilePath(null);
        } else if (myUseEmptyConfig != null && myUseEmptyConfig.isSelected()) {
            settings = settings.withConfigMode(org.intellij.lang.xslfo.run.SettingsFileMode.EMPTY)
                    .withConfigFilePath(null);
        } else { // FILE
            settings = settings.withConfigMode(org.intellij.lang.xslfo.run.SettingsFileMode.FILE)
                    .withConfigFilePath(myUserConfigLocation != null ? myUserConfigLocation.getText() : null);
        }

        s.setSettings(settings);
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myComponent;
    }

    private void updateComponentsState() {
        if (myUseTemporaryFiles != null && myOutputFile != null) {
            myOutputFile.setEnabled(!myUseTemporaryFiles.isSelected());
        }
        if (myUseTemporaryFiles != null && myOpenOutputFile != null) {
            myOpenOutputFile.setEnabled(!myUseTemporaryFiles.isSelected());
            if (myUseTemporaryFiles.isSelected()) {
                myOpenOutputFile.setSelected(true);
            }
        }
        // Execution controls
        boolean externalSelected = myUseBinaryFopRadio != null && myUseBinaryFopRadio.isSelected();
        String dirToUse = myFopInstallationDir != null ? myFopInstallationDir.getText() : null;
        if (myFopInstallationDir != null) myFopInstallationDir.setEnabled(externalSelected);
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
        // Config source controls
        boolean fileMode = myUseConfigFile != null && myUseConfigFile.isSelected();
        if (myUserConfigLocation != null) myUserConfigLocation.setEnabled(fileMode);
    }
}

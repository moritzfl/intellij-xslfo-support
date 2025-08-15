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
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

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

    public XslFoRunConfigurationEditor(Project project) {
        this.myProject = project;

        // Replace deprecated addBrowseFolderListener with explicit chooser action
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
        myOutputFile.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, myProject, null, file -> {
            if (file != null) {
                myOutputFile.setText(file.getPath().replace('/', java.io.File.separatorChar));
            }
        }));
        myUseTemporaryFiles.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateComponentsState();
            }
        });
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
    }
}

package org.intellij.lang.xslfo.run.editor;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;

/**
 * @author Dmitry_Cherkas
 */
public class XmlInputFileField extends ComponentWithBrowseButton<JComboBox<String>> {

    private final FileChooserDescriptor myXmlDescriptor;
    private final ProjectDefaultAccessor myProjectDefaultAccessor;

    private final XsltFileField myXsltFileField;

    /**
     * Decision to inject XsltFileField as a dependency is questionable, but currently Xml field depends on Xsl field by design.
     */
    public XmlInputFileField(final Project project, XsltFileField xsltFileField) {
        super(new JComboBox<>(), null);
        this.getChildComponent().setEditable(true);

        myXsltFileField = xsltFileField;
        myProjectDefaultAccessor = new ProjectDefaultAccessor(project);
        myXmlDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(XmlFileType.INSTANCE);

        // Use non-deprecated direct FileChooser invocation instead of deprecated addBrowseFolderListener/BrowseFolderActionListener
        this.addActionListener(e -> {
            // Determine initial selection directory
            Object item = getChildComponent().getEditor().getItem();
            String initialText = item != null ? item.toString() : "";
            if (initialText.isEmpty()) {
                initialText = myProjectDefaultAccessor.getText(myXsltFileField.getChildComponent());
            }
            VirtualFile initial = null;
            if (initialText != null && !initialText.isEmpty()) {
                initial = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(initialText.replace(File.separatorChar, '/')));
                if (initial != null && !initial.isDirectory()) {
                    initial = initial.getParent();
                }
            }
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(myXmlDescriptor, project, initial, file -> {
                if (file != null) {
                    getChildComponent().getEditor().setItem(file.getPath().replace('/', File.separatorChar));
                }
            });
        });

        myXsltFileField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            final PsiManager psiManager = PsiManager.getInstance(project);
            final VirtualFileManager fileMgr = VirtualFileManager.getInstance();
            final FileAssociationsManager associationsManager = FileAssociationsManager.getInstance(project);

            protected void textChanged(@NotNull DocumentEvent e) {
                final String text = myXsltFileField.getText();
                final JComboBox<String> comboBox = XmlInputFileField.this.getChildComponent();
                final Object oldXml = getXmlInputFile();
                if (!text.isEmpty()) {
                    final ComboBoxModel<String> model = comboBox.getModel();

                    boolean found = false;
                    for (int i = 0; i < model.getSize(); i++) {
                        if (oldXml.equals(model.getElementAt(i))) {
                            found = true;
                        }
                    }
                    final VirtualFile virtualFile = fileMgr.findFileByUrl(VfsUtil.pathToUrl(text.replace(File.separatorChar, '/')));
                    final PsiFile psiFile;
                    if (virtualFile != null && (psiFile = psiManager.findFile(virtualFile)) != null) {
                        final PsiFile[] files = associationsManager.getAssociationsFor(psiFile);

                        final String[] associations = new String[files.length];
                        for (int i = 0; i < files.length; i++) {
                            final VirtualFile f = files[i].getVirtualFile();
                            assert f != null;
                            associations[i] = f.getPath().replace('/', File.separatorChar);
                        }
                        comboBox.setModel(new DefaultComboBoxModel<>(associations));
                    }
                    if (!found) {
                        comboBox.getEditor().setItem(oldXml);
                    }
                    comboBox.setSelectedItem(oldXml);
                } else {
                    comboBox.setModel(new DefaultComboBoxModel<>(ArrayUtil.EMPTY_STRING_ARRAY));
                    comboBox.getEditor().setItem(oldXml);
                }
            }
        });
    }

    public String getXmlInputFile() {
        final JComboBox<String> comboBox = this.getChildComponent();
        final Object currentItem = comboBox.getEditor().getItem();
        String s = (String) (currentItem != null ? currentItem : comboBox.getSelectedItem());
        return s != null ? s : "";
    }

    public FileChooserDescriptor getDescriptor() {
        return myXmlDescriptor;
    }
}

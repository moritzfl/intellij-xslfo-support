package org.intellij.lang.xslfo.run.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.intellij.lang.xpath.xslt.XsltSupport;

/**
 * @author Dmitry_Cherkas
 */
public class XsltFileField extends TextFieldWithBrowseButton {

    private final FileChooserDescriptor myXsltDescriptor;

    public XsltFileField(final Project project) {

        final PsiManager psiManager = PsiManager.getInstance(project);

        myXsltDescriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter(file -> ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
                    final PsiFile psiFile = psiManager.findFile(file);
                    return psiFile != null && XsltSupport.isXsltFile(psiFile);
                }));

        this.addActionListener(e -> FileChooser.chooseFile(myXsltDescriptor, project, null, file -> {
            if (file != null) {
                setText(file.getPath().replace('/', java.io.File.separatorChar));
            }
        }));
    }

    public FileChooserDescriptor getDescriptor() {
        return myXsltDescriptor;
    }
}

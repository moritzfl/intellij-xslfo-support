package org.intellij.lang.xslfo.preview;

import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class PdfPreviewEditor extends TextEditorWithPreview {

  public PdfPreviewEditor(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    super(createTextEditor(project, virtualFile), new PdfPreviewFileEditor(project, virtualFile), "XSL-FO");
  }

  private static @NotNull TextEditor createTextEditor(@NotNull Project project,
                                                       @NotNull VirtualFile virtualFile) {
    return (TextEditor) TextEditorProvider.getInstance().createEditor(project, virtualFile);
  }
}

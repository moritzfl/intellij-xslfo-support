package org.intellij.lang.xslfo.preview;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.beans.PropertyChangeListener;

public class PdfPreviewFileEditor extends UserDataHolderBase implements FileEditor {

  private final VirtualFile file;
  private final PdfPreviewPanel panel;

  public PdfPreviewFileEditor(@NotNull VirtualFile file) {
    this.file = file;
    this.panel = new PdfPreviewPanel(file.getPath());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return panel;
  }

  @Override
  public @NotNull @NonNls String getName() {
    return "XSL-FO Preview";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return file;
  }
}

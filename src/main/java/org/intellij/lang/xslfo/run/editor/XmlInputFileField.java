package org.intellij.lang.xslfo.run.editor;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * UI component for managing multiple XML input files.
 */
public class XmlInputFileField extends JPanel {

  private final FileChooserDescriptor myXmlDescriptor;
  private final ProjectDefaultAccessor myProjectDefaultAccessor;
  private final XsltFileField myXsltFileField;
  private final DefaultTableModel tableModel;
  private final JBTable table;

  public XmlInputFileField(final Project project, XsltFileField xsltFileField) {
    super(new BorderLayout(0, 4));
    myXsltFileField = xsltFileField;
    myProjectDefaultAccessor = new ProjectDefaultAccessor(project);

    myXmlDescriptor = new FileChooserDescriptor(true, false, false, false, false, true);
    myXmlDescriptor.withFileFilter(file -> XmlFileType.INSTANCE.equals(file.getFileType()));

    tableModel = new DefaultTableModel(new Object[]{"XML input files"}, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    table = new JBTable(tableModel);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setTableHeader(null);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table)
        .setAddAction(button -> chooseAndAddFiles(project))
        .setRemoveAction(button -> removeSelectedFiles())
        .disableUpDownActions();
    JBLabel label = new JBLabel("XML input files");
    label.setLabelFor(table);
    add(label, BorderLayout.NORTH);
    add(decorator.createPanel(), BorderLayout.CENTER);

    myXsltFileField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      final PsiManager psiManager = PsiManager.getInstance(project);
      final VirtualFileManager fileMgr = VirtualFileManager.getInstance();
      final FileAssociationsManager associationsManager =
          FileAssociationsManager.getInstance(project);

      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (tableModel.getRowCount() > 0) {
          return;
        }
        final String text = myXsltFileField.getText();
        if (text.isBlank()) {
          return;
        }
        final VirtualFile virtualFile =
            fileMgr.findFileByUrl(VfsUtil.pathToUrl(text.replace(File.separatorChar, '/')));
        if (virtualFile == null) {
          return;
        }
        final PsiFile psiFile = psiManager.findFile(virtualFile);
        if (psiFile == null) {
          return;
        }
        final PsiFile[] files = associationsManager.getAssociationsFor(psiFile);
        for (PsiFile file : files) {
          VirtualFile f = file.getVirtualFile();
          if (f != null) {
            addXmlInputFile(f.getPath().replace('/', File.separatorChar));
          }
        }
      }
    });
  }

  private void chooseAndAddFiles(Project project) {
    String initialText = myProjectDefaultAccessor.getText(myXsltFileField.getChildComponent());
    VirtualFile initial = null;
    if (initialText != null && !initialText.isEmpty()) {
      initial = VirtualFileManager.getInstance()
          .findFileByUrl(VfsUtil.pathToUrl(initialText.replace(File.separatorChar, '/')));
      if (initial != null && !initial.isDirectory()) {
        initial = initial.getParent();
      }
    }

    com.intellij.openapi.fileChooser.FileChooser.chooseFiles(myXmlDescriptor, project, initial,
        files -> {
          if (files == null || files.isEmpty()) {
            return;
          }
          LinkedHashSet<String> merged = new LinkedHashSet<>(getXmlInputFiles());
          for (VirtualFile file : files) {
            if (file != null) {
              merged.add(file.getPath().replace('/', File.separatorChar));
            }
          }
          setXmlInputFiles(new ArrayList<>(merged));
        });
  }

  private void removeSelectedFiles() {
    int[] selectedRows = table.getSelectedRows();
    if (selectedRows.length == 0) {
      return;
    }
    for (int i = selectedRows.length - 1; i >= 0; i--) {
      int modelRow = table.convertRowIndexToModel(selectedRows[i]);
      tableModel.removeRow(modelRow);
    }
  }

  public String getXmlInputFile() {
    List<String> files = getXmlInputFiles();
    return files.isEmpty() ? "" : files.get(0);
  }

  public List<String> getXmlInputFiles() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < tableModel.getRowCount(); i++) {
      Object rowValue = tableModel.getValueAt(i, 0);
      String value = rowValue instanceof String ? (String) rowValue : null;
      if (value != null && !value.isBlank()) {
        result.add(value);
      }
    }
    return result;
  }

  public void setXmlInputFiles(List<String> files) {
    tableModel.setRowCount(0);
    LinkedHashSet<String> unique = new LinkedHashSet<>(files);
    for (String file : unique) {
      addXmlInputFile(file);
    }
  }

  private void addXmlInputFile(String path) {
    if (path == null || path.isBlank()) {
      return;
    }
    for (int i = 0; i < tableModel.getRowCount(); i++) {
      Object rowValue = tableModel.getValueAt(i, 0);
      if (path.equals(rowValue)) {
        return;
      }
    }
    tableModel.addRow(new Object[]{path});
  }

  public FileChooserDescriptor getDescriptor() {
    return myXmlDescriptor;
  }
}

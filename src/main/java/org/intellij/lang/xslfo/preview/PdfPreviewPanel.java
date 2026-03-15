package org.intellij.lang.xslfo.preview;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

public class PdfPreviewPanel extends JPanel {

  private final String filePath;

  public PdfPreviewPanel(String filePath) {
    this.filePath = filePath;
    initUI();
  }

  private void initUI() {
    setLayout(new BorderLayout());
    JLabel label = new JLabel("XSL-FO Preview - " + filePath, SwingConstants.CENTER);
    add(label, BorderLayout.CENTER);
  }
}

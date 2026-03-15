package org.intellij.lang.xslfo.preview;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Lightweight PDFBox-based single-page viewer with standard page navigation controls.
 */
public class PdfBoxViewerPanel extends JPanel {

  private static final float BASE_RENDER_DPI = 75f;
  private static final int BASE_SCROLL_INCREMENT = 16;
  private static final int SCROLL_SPEED_MULTIPLIER = 3;
  private static final String[] ZOOM_LEVELS = {
      "25%",
      "50%",
      "75%",
      "100%",
      "125%",
      "150%",
      "175%",
      "200%",
      "250%",
      "300%",
      "400%",
      "500%",
      "600%",
      "700%",
      "800%",
      "900%",
      "1000%",
      "1250%",
      "1500%",
      "1750%",
      "2000%"
  };

  private final JLabel myPageLabel = new JLabel("", SwingConstants.CENTER);
  private final JButton myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  private final JButton myFirstButton = new JButton(new NavigationIcon(NavigationIconType.FIRST));
  private final JButton myPreviousButton =
      new JButton(new NavigationIcon(NavigationIconType.PREVIOUS));
  private final JButton myNextButton = new JButton(new NavigationIcon(NavigationIconType.NEXT));
  private final JButton myLastButton = new JButton(new NavigationIcon(NavigationIconType.LAST));
  private final JTextField myPageField = new JTextField(4);
  private final JLabel myPageCountLabel = new JLabel("/ 0");
  private final JComboBox<String> myZoomCombo = new JComboBox<>(ZOOM_LEVELS);
  private final JScrollPane myScrollPane = new JScrollPane(myPageLabel);

  private Runnable myRefreshAction = () -> {
  };
  private PDDocument myDocument;
  private PDFRenderer myRenderer;
  private int myPageCount;
  private int myCurrentPage;
  private float myZoomFactor = 1.0f;
  private double myVerticalScrollRemainder;
  private double myHorizontalScrollRemainder;

  public PdfBoxViewerPanel() {
    super(new BorderLayout());
    buildUi();
    showStatus("Select a launch configuration and XML input to render preview.");
  }

  public void setRefreshAction(Runnable refreshAction) {
    myRefreshAction = refreshAction != null ? refreshAction : () -> {
    };
  }

  private void buildUi() {
    myPageLabel.setVerticalAlignment(SwingConstants.TOP);
    myPageLabel.setBorder(BorderFactory.createEmptyBorder(
        JBUI.scale(8),
        JBUI.scale(8),
        JBUI.scale(8),
        JBUI.scale(8)));

    myRefreshButton.setToolTipText("Refresh preview");
    makeCompactIconButton(myRefreshButton);
    myRefreshButton.addActionListener(e -> myRefreshAction.run());

    configureScrollSpeed();
    add(myScrollPane, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.SOUTH);

    myFirstButton.setToolTipText("First page");
    myPreviousButton.setToolTipText("Previous page");
    myNextButton.setToolTipText("Next page");
    myLastButton.setToolTipText("Last page");
    makeCompactIconButton(myFirstButton);
    makeCompactIconButton(myPreviousButton);
    makeCompactIconButton(myNextButton);
    makeCompactIconButton(myLastButton);

    myFirstButton.addActionListener(e -> goToPage(0));
    myPreviousButton.addActionListener(e -> goToPage(myCurrentPage - 1));
    myNextButton.addActionListener(e -> goToPage(myCurrentPage + 1));
    myLastButton.addActionListener(e -> goToPage(myPageCount - 1));
    myPageField.addActionListener(e -> jumpToTypedPage());
    myZoomCombo.setSelectedItem("100%");
    myZoomCombo.setToolTipText("Zoom");
    myZoomCombo.addActionListener(e -> zoomChanged());
    updateNavigationState();
  }

  private JPanel createToolbar() {
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)));
    toolbar.add(myRefreshButton);
    toolbar.add(myFirstButton);
    toolbar.add(myPreviousButton);
    toolbar.add(myPageField);
    toolbar.add(myPageCountLabel);
    toolbar.add(myNextButton);
    toolbar.add(myLastButton);
    toolbar.add(new JLabel(AllIcons.Actions.Find));
    toolbar.add(myZoomCombo);
    return toolbar;
  }

  public void showLoading(String message) {
    closeDocument();
    showStatus(message);
  }

  public void showError(String message) {
    closeDocument();
    showStatus(message);
  }

  public void setPdfFile(File pdfFile) throws IOException {
    setPdfFile(pdfFile, 0);
  }

  public void setPdfFile(File pdfFile, int preferredPageIndex) throws IOException {
    closeDocument();
    myDocument = Loader.loadPDF(pdfFile);
    myRenderer = new PDFRenderer(myDocument);
    myPageCount = myDocument.getNumberOfPages();
    if (myPageCount <= 0) {
      showStatus("Rendered preview PDF has no pages.");
      return;
    }
    myCurrentPage = Math.max(0, Math.min(preferredPageIndex, myPageCount - 1));
    renderCurrentPage();
  }

  public int getCurrentPageIndex() {
    return myPageCount > 0 ? myCurrentPage : 0;
  }

  public void dispose() {
    closeDocument();
  }

  private void jumpToTypedPage() {
    if (myPageCount <= 0) {
      return;
    }
    try {
      int requestedPage = Integer.parseInt(myPageField.getText().trim());
      goToPage(requestedPage - 1);
    } catch (NumberFormatException ignore) {
      myPageField.setText(Integer.toString(myCurrentPage + 1));
    }
  }

  private void goToPage(int pageIndex) {
    if (myPageCount <= 0) {
      return;
    }
    int target = Math.max(0, Math.min(pageIndex, myPageCount - 1));
    if (target == myCurrentPage && myPageLabel.getIcon() != null) {
      return;
    }
    myCurrentPage = target;
    try {
      renderCurrentPage();
    } catch (IOException e) {
      showError("Could not render preview page: " + e.getMessage());
    }
  }

  private void renderCurrentPage() throws IOException {
    if (myRenderer == null || myPageCount <= 0) {
      return;
    }
    float dpi = BASE_RENDER_DPI * myZoomFactor;
    BufferedImage pageImage = myRenderer.renderImageWithDPI(myCurrentPage, dpi);
    myPageLabel.setIcon(new ImageIcon(pageImage));
    myPageLabel.setText(null);
    myPageField.setText(Integer.toString(myCurrentPage + 1));
    myPageCountLabel.setText("/ " + myPageCount);
    updateNavigationState();
    revalidate();
    repaint();
  }

  private void showStatus(String message) {
    myPageLabel.setIcon(null);
    myPageLabel.setText(message);
    myPageCount = 0;
    myCurrentPage = 0;
    myPageField.setText("1");
    myPageCountLabel.setText("/ 0");
    updateNavigationState();
  }

  private void updateNavigationState() {
    boolean hasDocument = myPageCount > 0;
    myFirstButton.setEnabled(hasDocument && myCurrentPage > 0);
    myPreviousButton.setEnabled(hasDocument && myCurrentPage > 0);
    myNextButton.setEnabled(hasDocument && myCurrentPage < myPageCount - 1);
    myLastButton.setEnabled(hasDocument && myCurrentPage < myPageCount - 1);
    myPageField.setEnabled(hasDocument);
  }

  private void zoomChanged() {
    Object selected = myZoomCombo.getSelectedItem();
    if (!(selected instanceof String zoomText)) {
      return;
    }
    myZoomFactor = parseZoomFactor(zoomText);
    if (myPageCount <= 0) {
      return;
    }
    try {
      renderCurrentPage();
    } catch (IOException e) {
      showError("Could not render preview page: " + e.getMessage());
    }
  }

  private static float parseZoomFactor(String zoomText) {
    String normalized = zoomText == null ? "" : zoomText.trim().replace("%", "");
    if (normalized.isEmpty()) {
      return 1.0f;
    }
    try {
      float value = Float.parseFloat(normalized);
      if (value <= 0f) {
        return 1.0f;
      }
      return value / 100f;
    } catch (NumberFormatException ignore) {
      return 1.0f;
    }
  }

  private static void makeCompactIconButton(JButton button) {
    Icon icon = button.getIcon();
    int iconWidth = icon != null ? icon.getIconWidth() : 16;
    int iconHeight = icon != null ? icon.getIconHeight() : 16;
    int side = Math.max(iconWidth, iconHeight) + JBUI.scale(8);
    button.setMargin(JBUI.insets(2));
    button.setPreferredSize(JBUI.size(side, side));
    button.setMinimumSize(JBUI.size(side, side));
    button.setMaximumSize(JBUI.size(side, side));
  }

  private void configureScrollSpeed() {
    int unitIncrement = JBUI.scale(BASE_SCROLL_INCREMENT);
    int blockIncrement = unitIncrement * 4;
    myScrollPane.getVerticalScrollBar().setUnitIncrement(unitIncrement);
    myScrollPane.getVerticalScrollBar().setBlockIncrement(blockIncrement);
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(unitIncrement);
    myScrollPane.getHorizontalScrollBar().setBlockIncrement(blockIncrement);
    myScrollPane.setWheelScrollingEnabled(false);
    myScrollPane.addMouseWheelListener(event -> {
      JScrollBar targetBar =
          event.isShiftDown() ? myScrollPane.getHorizontalScrollBar() :
              myScrollPane.getVerticalScrollBar();
      if (!targetBar.isVisible()) {
        return;
      }

      double delta = event.getPreciseWheelRotation()
          * JBUI.scale(BASE_SCROLL_INCREMENT)
          * SCROLL_SPEED_MULTIPLIER;
      double remainder = event.isShiftDown() ? myHorizontalScrollRemainder : myVerticalScrollRemainder;
      double totalDelta = delta + remainder;
      int movement = (int) totalDelta;

      if (event.isShiftDown()) {
        myHorizontalScrollRemainder = totalDelta - movement;
      } else {
        myVerticalScrollRemainder = totalDelta - movement;
      }

      if (movement == 0) {
        event.consume();
        return;
      }

      int min = targetBar.getMinimum();
      int max = targetBar.getMaximum() - targetBar.getVisibleAmount();
      int newValue = Math.max(min, Math.min(targetBar.getValue() + movement, max));
      targetBar.setValue(newValue);
      event.consume();
    });
  }

  private void closeDocument() {
    myRenderer = null;
    if (myDocument != null) {
      try {
        myDocument.close();
      } catch (IOException ignore) {
        // ignore close failures on preview lifecycle
      }
      myDocument = null;
    }
  }

  private enum NavigationIconType {
    FIRST,
    PREVIOUS,
    NEXT,
    LAST
  }

  private static final class NavigationIcon implements Icon {
    private final NavigationIconType type;

    private NavigationIcon(NavigationIconType type) {
      this.type = type;
    }

    @Override
    public int getIconWidth() {
      return JBUI.scale(12);
    }

    @Override
    public int getIconHeight() {
      return JBUI.scale(12);
    }

    @Override
    public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        Color color = UIManager.getColor("Label.foreground");
        g2.setColor(color != null ? color : Color.DARK_GRAY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getIconWidth();
        int h = getIconHeight();
        int left = x;
        int top = y;
        int centerY = top + h / 2;
        int barWidth = Math.max(1, JBUI.scale(1));
        int margin = Math.max(1, JBUI.scale(1));

        switch (type) {
          case PREVIOUS -> paintLeftTriangle(g2, left + margin, top + margin, w - 2 * margin,
              h - 2 * margin);
          case NEXT -> paintRightTriangle(g2, left + margin, top + margin, w - 2 * margin,
              h - 2 * margin);
          case FIRST -> {
            g2.fillRect(left + margin, top + margin, barWidth, h - 2 * margin);
            paintLeftTriangle(g2, left + margin + barWidth + 1, top + margin,
                w - barWidth - 3 * margin, h - 2 * margin);
          }
          case LAST -> {
            int barX = left + w - margin - barWidth;
            g2.fillRect(barX, top + margin, barWidth, h - 2 * margin);
            paintRightTriangle(g2, left + margin, top + margin,
                w - barWidth - 3 * margin, h - 2 * margin);
          }
          default -> g2.drawLine(left + margin, centerY, left + w - margin, centerY);
        }
      } finally {
        g2.dispose();
      }
    }

    private static void paintLeftTriangle(Graphics2D g2, int x, int y, int w, int h) {
      if (w <= 0 || h <= 0) {
        return;
      }
      int[] xs = {x, x + w, x + w};
      int[] ys = {y + h / 2, y, y + h};
      g2.fillPolygon(xs, ys, 3);
    }

    private static void paintRightTriangle(Graphics2D g2, int x, int y, int w, int h) {
      if (w <= 0 || h <= 0) {
        return;
      }
      int[] xs = {x + w, x, x};
      int[] ys = {y + h / 2, y, y + h};
      g2.fillPolygon(xs, ys, 3);
    }
  }
}

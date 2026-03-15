package org.intellij.lang.xslfo.preview;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.JBUI;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight PDFBox-based single-page viewer with standard page navigation controls.
 */
public class PdfBoxViewerPanel extends JPanel {

  private static final float BASE_RENDER_DPI = 96f;
  private static final int BASE_SCROLL_INCREMENT = 16;
  private static final int SCROLL_SPEED_MULTIPLIER = 2;
  private static final int NAVIGATION_ICON_SIZE = 12;
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
      "750%",
      "1000%",
  };

  private final JLabel myPageLabel = new JLabel("", SwingConstants.CENTER);
  private final JButton myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  private final JButton myOpenExternalButton =
      new JButton(createNavigationIcon(FontAwesomeSolid.EXTERNAL_LINK_ALT));
  private final JButton myFirstButton =
      new JButton(createNavigationIcon(FontAwesomeSolid.ANGLE_DOUBLE_LEFT));
  private final JButton myPreviousButton =
      new JButton(createNavigationIcon(FontAwesomeSolid.ANGLE_LEFT));
  private final JButton myNextButton =
      new JButton(createNavigationIcon(FontAwesomeSolid.ANGLE_RIGHT));
  private final JButton myLastButton =
      new JButton(createNavigationIcon(FontAwesomeSolid.ANGLE_DOUBLE_RIGHT));
  private final JTextField myPageField = new JTextField(4);
  private final JLabel myPageCountLabel = new JLabel("/ 0");
  private final JComboBox<String> myZoomCombo = new JComboBox<>(ZOOM_LEVELS);
  private final JScrollPane myScrollPane = new JScrollPane(myPageLabel);
  private final AtomicLong myPageRenderRequestCounter = new AtomicLong(0);

  private Runnable myRefreshAction = () -> {
  };
  private PDDocument myDocument;
  private PDFRenderer myRenderer;
  private File myCurrentPdfFile;
  private int myPageCount;
  private int myCurrentPage;
  private float myZoomFactor = 1.0f;
  private double myVerticalScrollRemainder;
  private double myHorizontalScrollRemainder;
  private volatile Future<?> myPageRenderTask;

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
    myOpenExternalButton.setToolTipText("Open PDF in system viewer");
    makeCompactIconButton(myOpenExternalButton);
    myOpenExternalButton.addActionListener(e -> openInSystemPdfViewer());

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
    JPanel toolbar = new JPanel(new GridBagLayout());
    JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)));
    leftPanel.setOpaque(false);
    leftPanel.add(myRefreshButton);
    leftPanel.add(myOpenExternalButton);

    JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, JBUI.scale(6), JBUI.scale(4)));
    navigationPanel.setOpaque(false);
    navigationPanel.add(myFirstButton);
    navigationPanel.add(myPreviousButton);
    navigationPanel.add(myPageField);
    navigationPanel.add(myPageCountLabel);
    navigationPanel.add(myNextButton);
    navigationPanel.add(myLastButton);

    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(4)));
    rightPanel.setOpaque(false);
    rightPanel.add(new JLabel(AllIcons.Actions.Find));
    rightPanel.add(myZoomCombo);

    int sideWidth = Math.max(leftPanel.getPreferredSize().width, rightPanel.getPreferredSize().width);
    leftPanel.setPreferredSize(JBUI.size(sideWidth, leftPanel.getPreferredSize().height));
    rightPanel.setPreferredSize(JBUI.size(sideWidth, rightPanel.getPreferredSize().height));

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = 0;
    constraints.fill = GridBagConstraints.HORIZONTAL;

    constraints.gridx = 0;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.WEST;
    toolbar.add(leftPanel, constraints);

    constraints.gridx = 1;
    constraints.weightx = 0.0;
    constraints.anchor = GridBagConstraints.CENTER;
    toolbar.add(navigationPanel, constraints);

    constraints.gridx = 2;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.EAST;
    toolbar.add(rightPanel, constraints);
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
    myCurrentPdfFile = pdfFile;
    myDocument = Loader.loadPDF(pdfFile);
    myRenderer = new PDFRenderer(myDocument);
    myPageCount = myDocument.getNumberOfPages();
    if (myPageCount <= 0) {
      showStatus("Rendered preview PDF has no pages.");
      return;
    }
    myCurrentPage = Math.max(0, Math.min(preferredPageIndex, myPageCount - 1));
    requestRenderCurrentPage();
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
    requestRenderCurrentPage();
  }

  private void requestRenderCurrentPage() {
    if (myRenderer == null || myPageCount <= 0) {
      return;
    }
    cancelPageRenderTask();
    long requestId = myPageRenderRequestCounter.incrementAndGet();
    int pageIndex = myCurrentPage;
    float dpi = BASE_RENDER_DPI * myZoomFactor;
    PDFRenderer renderer = myRenderer;
    myPageLabel.setIcon(null);
    myPageLabel.setText("Rendering page...");
    myPageField.setText(Integer.toString(pageIndex + 1));
    myPageCountLabel.setText("/ " + myPageCount);
    updateNavigationState();
    myPageRenderTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, dpi);
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
        ApplicationManager.getApplication().invokeLater(
            () -> applyRenderedPage(requestId, renderer, pageIndex, pageImage, null));
      } catch (Exception exception) {
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
        ApplicationManager.getApplication().invokeLater(
            () -> applyRenderedPage(requestId, renderer, pageIndex, null, exception));
      }
    });
  }

  private void applyRenderedPage(long requestId, PDFRenderer renderer, int pageIndex,
                                 BufferedImage pageImage, Exception error) {
    if (requestId != myPageRenderRequestCounter.get() || renderer != myRenderer) {
      return;
    }
    myPageRenderTask = null;
    if (error != null) {
      String message = error.getMessage() == null ? error.getClass().getSimpleName() :
          error.getMessage();
      showError("Could not render preview page: " + message);
      return;
    }
    myCurrentPage = pageIndex;
    myPageLabel.setIcon(new ImageIcon(pageImage));
    myPageLabel.setText(null);
    myPageField.setText(Integer.toString(myCurrentPage + 1));
    myPageCountLabel.setText("/ " + myPageCount);
    updateNavigationState();
    revalidate();
    repaint();
  }

  private void cancelPageRenderTask() {
    Future<?> renderTask = myPageRenderTask;
    if (renderTask != null) {
      renderTask.cancel(true);
      myPageRenderTask = null;
    }
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
    myOpenExternalButton.setEnabled(
        hasDocument && myCurrentPdfFile != null && myCurrentPdfFile.exists());
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
    requestRenderCurrentPage();
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

  private static Icon createNavigationIcon(FontAwesomeSolid iconCode) {
    return FontIcon.of(
        iconCode,
        JBUI.scale(NAVIGATION_ICON_SIZE),
        UIManager.getColor("Label.foreground"));
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
    myPageRenderRequestCounter.incrementAndGet();
    cancelPageRenderTask();
    myRenderer = null;
    myCurrentPdfFile = null;
    if (myDocument != null) {
      try {
        myDocument.close();
      } catch (IOException ignore) {
        // ignore close failures on preview lifecycle
      }
      myDocument = null;
    }
  }

  private void openInSystemPdfViewer() {
    if (myCurrentPdfFile == null || !myCurrentPdfFile.exists()) {
      showOpenPdfMessage("No rendered preview PDF is available yet.", JOptionPane.WARNING_MESSAGE);
      return;
    }
    if (!Desktop.isDesktopSupported()) {
      showOpenPdfMessage("Desktop integration is not available in this environment.",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    Desktop desktop;
    try {
      desktop = Desktop.getDesktop();
    } catch (UnsupportedOperationException e) {
      showOpenPdfMessage("System PDF viewer is not available: " + e.getMessage(),
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (!desktop.isSupported(Desktop.Action.OPEN)) {
      showOpenPdfMessage("Opening files in the system viewer is not supported on this platform.",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    try {
      desktop.open(myCurrentPdfFile);
    } catch (IOException | SecurityException e) {
      showOpenPdfMessage("Could not open PDF in the system viewer:\n" + e.getMessage(),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void showOpenPdfMessage(String message, int messageType) {
    JOptionPane.showMessageDialog(this, message, "XSL-FO Preview", messageType);
  }

}

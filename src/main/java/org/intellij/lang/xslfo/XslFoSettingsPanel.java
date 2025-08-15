package org.intellij.lang.xslfo;

import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/**
 * @author Dmitry_Cherkas
 */
public class XslFoSettingsPanel {
    private JPanel myPanel;
    private TextFieldWithBrowseButton myFopInstallationDir;
    private TextFieldWithBrowseButton myUserConfigLocation;
    private JPanel myValidationPanel;
    private JLabel myWarningLabel;
    private JSeparator mySeparator;
    private JRadioButton myUseBundledFopRadio;
    private JRadioButton myUseBinaryFopRadio;
    private JLabel myBundledFopVersionLabel;

    public XslFoSettingsPanel() {
        // Replace deprecated addBrowseFolderListener with explicit chooser actions
        myFopInstallationDir.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(), null, null, file -> {
                    if (file != null) {
                        myFopInstallationDir.setText(file.getPath().replace('/', java.io.File.separatorChar));
                    }
                }));

        myUserConfigLocation.addActionListener(e -> com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor(XmlFileType.INSTANCE), null, null, file -> {
                    if (file != null) {
                        myUserConfigLocation.setText(file.getPath().replace('/', java.io.File.separatorChar));
                    }
                }));

        // group radio buttons and hook enable/disable
        ButtonGroup group = new ButtonGroup();
        group.add(myUseBundledFopRadio);
        group.add(myUseBinaryFopRadio);
        myUseBundledFopRadio.addActionListener(e -> updateEnabledStates());
        myUseBinaryFopRadio.addActionListener(e -> updateEnabledStates());

        // set bundled FOP version text without relying on FOP classes at runtime
        String version = null;
        try {
            java.io.InputStream is = XslFoSettingsPanel.class.getClassLoader()
                    .getResourceAsStream("META-INF/xslfo/bundled-fop-version.txt");
            if (is != null) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                    String line = br.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        version = line.trim();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        if (version == null) version = "unknown";
        if (myUseBundledFopRadio != null) {
            myUseBundledFopRadio.setText("Use bundled FOP (" + version + ")");
        }
        // configure Settings Validation
        myWarningLabel.setIcon(AllIcons.General.BalloonError);
        MySettingsPanelChangeListener changeListener = new MySettingsPanelChangeListener();

        myFopInstallationDir.getTextField().getDocument().addDocumentListener(changeListener);
        myUserConfigLocation.getTextField().getDocument().addDocumentListener(changeListener);
        myPanel.addComponentListener(changeListener);

        updateEnabledStates();
    }

    public JComponent getComponent() {
        return myPanel;
    }

    public String getFopInstallationDir() {
        return myFopInstallationDir.getText();
    }

    public String getUserConfigLocation() {
        return myUserConfigLocation.getText();
    }

    public void setFopInstallationDir(String fopInstallationDir) {
        myFopInstallationDir.setText(fopInstallationDir);
    }

    public void setUserConfigLocation(String userConfigLocation) {
        myUserConfigLocation.setText(userConfigLocation);
    }

    public boolean isUseBundledFopSelected() {
        return myUseBundledFopRadio.isSelected();
    }

    public void setUseBundledFopSelected(boolean useBundled) {
        myUseBundledFopRadio.setSelected(useBundled);
        myUseBinaryFopRadio.setSelected(!useBundled);
        updateEnabledStates();
    }

    private void updateEnabledStates() {
        boolean bundled = myUseBundledFopRadio != null && myUseBundledFopRadio.isSelected();
        // When bundled is selected, installation dir is irrelevant; keep user config available for both
        myFopInstallationDir.setEnabled(!bundled);
        if (myBundledFopVersionLabel != null) {
            if (bundled) {
                // Hide info when bundled is selected
                myBundledFopVersionLabel.setVisible(false);
                myBundledFopVersionLabel.setText("");
            } else {
                // Show info explaining how the binary FOP will be resolved
                String dir = myFopInstallationDir.getText();
                if (dir == null || dir.trim().isEmpty()) {
                    myBundledFopVersionLabel.setText("Using FOP from system PATH (command: 'fop')");
                } else {
                    myBundledFopVersionLabel.setText("Using FOP from installation directory: " + dir.trim());
                }
                myBundledFopVersionLabel.setVisible(true);
            }
        }
    }

    private String validateSettings() {
        String dir = myFopInstallationDir.getText();
        if (dir != null && !dir.isEmpty()) {
            if (XslFoUtils.findFopExecutable(dir) == null) {
                return "<html><body><b>Error: </b>Selected FOP installation directory is invalid</body></html>";
            }
        }
        // No error if path is empty: the bundled FOP libraries will be used.
        return "";
    }

    private class MySettingsPanelChangeListener implements ComponentListener, DocumentListener {

        @Override
        public void componentShown(ComponentEvent e) {
            updateWarning();
        }

        @Override
        public void componentHidden(ComponentEvent e) {
            updateWarning();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            updateWarning();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            updateWarning();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            updateWarning();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateWarning();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            updateWarning();
        }

        private void updateWarning() {
            String errorMsg = validateSettings();

            if (errorMsg.isEmpty()) {
                // no errors, hide validation panel
                mySeparator.setVisible(false);
                myWarningLabel.setVisible(false);
                myValidationPanel.setVisible(false);
            } else {
                mySeparator.setVisible(true);
                myWarningLabel.setVisible(true);
                myWarningLabel.setText(errorMsg);
                myValidationPanel.setVisible(true);
            }
            // Also refresh binary info text/visibility when fields change
            updateEnabledStates();
        }
    }
}

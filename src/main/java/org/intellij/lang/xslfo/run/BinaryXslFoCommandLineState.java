package org.intellij.lang.xslfo.run;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.intellij.lang.xslfo.XslFoSettings;
import org.intellij.lang.xslfo.XslFoUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Dmitry_Cherkas
 */
public class BinaryXslFoCommandLineState extends CommandLineState {

    private final XslFoSettings mySettings = XslFoSettings.getInstance();
    private final XslFoRunConfiguration myXslFoRunConfiguration;
    private final File temporaryFile;

    public BinaryXslFoCommandLineState(XslFoRunConfiguration xslFoRunConfiguration, ExecutionEnvironment environment) {
        super(environment);

        this.myXslFoRunConfiguration = xslFoRunConfiguration;
        if (myXslFoRunConfiguration.getSettings().useTemporaryFiles()) {
            try {
                OutputFormat fmt = getEffectiveOutputFormat();
                temporaryFile = File.createTempFile("fo_", fmt.extension());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            temporaryFile = null;
        }
    }

    @NotNull
    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
        // This state is dedicated to external (binary) FOP execution; no switching to bundled here.
        final GeneralCommandLine commandLine = buildCommandLine();

        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
            @Override
            public Charset getCharset() {
                return commandLine.getCharset();
            }
        };
        // Show a popup notification indicating which FOP is used (external)
        String exePath = commandLine.getExePath();
        processHandler.startNotify();
        String msg = "Using FOP (external): " + ("fop".equalsIgnoreCase(exePath) ? "fop (from PATH)" : exePath);
        NotificationGroupManager.getInstance()
                .getNotificationGroup("XSL-FO")
                .createNotification(msg, NotificationType.INFORMATION)
                .notify(myXslFoRunConfiguration.getProject());

        ProcessTerminatedListener.attach(processHandler);
        final XslFoRunConfiguration runConfiguration = myXslFoRunConfiguration;
        processHandler.addProcessListener(new ProcessAdapter() {
            private final XslFoRunConfiguration myXsltRunConfiguration = runConfiguration;

            @Override
            public void processTerminated(final @NotNull ProcessEvent event) {
                Runnable runnable = () -> {
                    Runnable runnable1 = () -> {
                        if (event.getExitCode() == 0) {
                            if (myXsltRunConfiguration.getSettings().openOutputFile()) {
                                final String url = VfsUtilCore.pathToUrl(getOutputFilePath());
                                final VirtualFile fileByUrl = VirtualFileManager
                                        .getInstance().refreshAndFindFileByUrl(url.replace(File.separatorChar, '/'));
                                if (fileByUrl != null) {
                                    fileByUrl.refresh(true,
                                        false,
                                        () -> new OpenFileDescriptor(myXsltRunConfiguration.getProject(), fileByUrl).navigate(true));
                                    return;
                                }
                            }
                            VirtualFileManager.getInstance().asyncRefresh(null);
                        }
                    };
                    ApplicationManager.getApplication().runWriteAction(runnable1);
                };
                SwingUtilities.invokeLater(runnable);
            }
        });

        return processHandler;
    }

    protected GeneralCommandLine buildCommandLine() throws ExecutionException {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        String installDir;
        ExecutionMode mode = myXslFoRunConfiguration.getSettings().executionMode();
        if (mode == ExecutionMode.PLUGIN) {
            installDir = mySettings != null ? mySettings.getFopInstallationDir() : null;
        } else {
            installDir = myXslFoRunConfiguration.getSettings().fopInstallationDirOverride();
        }
        VirtualFile fopExecutablePath = XslFoUtils.findFopExecutable(installDir);
        if (fopExecutablePath != null) {
            commandLine.setExePath(fopExecutablePath.getPath());
        } else {
            // No installation dir configured; use FOP from PATH
            commandLine.setExePath("fop");
        }

        String userConfig;
        switch (myXslFoRunConfiguration.getSettings().configMode()) {
            case PLUGIN -> userConfig = mySettings.getUserConfigLocation();
            case EMPTY -> userConfig = null;
            case FILE -> userConfig = myXslFoRunConfiguration.getSettings().configFilePath();
            default -> userConfig = null;
        }
        VirtualFile fopUserConfig = XslFoUtils.findFopUserConfig(userConfig);
        if (fopUserConfig != null) {
            commandLine.addParameters("-c", fopUserConfig.getPath());
        }

        // XML
        String xmlInput = myXslFoRunConfiguration.getSettings().getXmlInputFilePointer() != null
                ? myXslFoRunConfiguration.getSettings().getXmlInputFilePointer().getPresentableUrl()
                : null;
        if (xmlInput == null || xmlInput.isEmpty()) {
            throw new CantRunException("No XML input file selected");
        }
        commandLine.addParameters("-xml", xmlInput);

        // XSL
        String xslt = myXslFoRunConfiguration.getSettings().getXsltFilePointer() != null
                ? myXslFoRunConfiguration.getSettings().getXsltFilePointer().getPresentableUrl()
                : null;
        if (xslt == null || xslt.isEmpty()) {
            throw new CantRunException("No XSLT file selected");
        }
        commandLine.addParameters("-xsl", xslt);

        // OUTPUT FORMAT (use effective format)
        OutputFormat fmt = getEffectiveOutputFormat();
        commandLine.addParameter(fmt.cliSwitch());

        // OUT FILE
        commandLine.addParameter(getOutputFilePath());
        return commandLine;
    }

    private String getOutputFilePath() {
        String out = myXslFoRunConfiguration.getSettings().outputFile();
        return (temporaryFile != null) ? temporaryFile.getAbsolutePath() : out;
    }

    private OutputFormat getEffectiveOutputFormat() {
        XslFoRunSettings s = myXslFoRunConfiguration.getSettings();
        if (s.usePluginOutputFormat()) {
            XslFoSettings plugin = XslFoSettings.getInstance();
            return plugin != null ? plugin.getDefaultOutputFormat() : OutputFormat.PDF;
        }
        return s.outputFormat();
    }
}

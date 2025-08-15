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
        if (myXslFoRunConfiguration.isUseTemporaryFiles()) {
            try {
                temporaryFile = File.createTempFile("fo_", ".pdf");
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
        // Fallback: if no external FOP executable is configured or found, run using bundled FOP instead of failing.
        VirtualFile fopExecutablePath = XslFoUtils.findFopExecutable(mySettings.getFopInstallationDir());
        if (fopExecutablePath == null) {
            return new BundledFopCommandLineState(myXslFoRunConfiguration, getEnvironment()).startProcess();
        }

        final GeneralCommandLine commandLine = buildCommandLine();

        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
            @Override
            public Charset getCharset() {
                return commandLine.getCharset();
            }
        };
        ProcessTerminatedListener.attach(processHandler);
        final XslFoRunConfiguration runConfiguration = myXslFoRunConfiguration;
        processHandler.addProcessListener(new ProcessAdapter() {
            private final XslFoRunConfiguration myXsltRunConfiguration = runConfiguration;

            @Override
            public void processTerminated(final @NotNull ProcessEvent event) {

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                if (event.getExitCode() == 0) {
                                    if (myXsltRunConfiguration.isOpenOutputFile()) {
                                        final String url = VfsUtilCore.pathToUrl(getOutputFilePath());
                                        final VirtualFile fileByUrl = VirtualFileManager
                                                .getInstance().refreshAndFindFileByUrl(url.replace(File.separatorChar, '/'));
                                        if (fileByUrl != null) {
                                            fileByUrl.refresh(true, false, () -> {
                                                new OpenFileDescriptor(myXsltRunConfiguration.getProject(), fileByUrl).navigate(true);
                                            });
                                            return;
                                        }
                                    }
                                    VirtualFileManager.getInstance().asyncRefresh(null);
                                }
                            }
                        };
                        ApplicationManager.getApplication().runWriteAction(runnable);
                    }
                };
                SwingUtilities.invokeLater(runnable);
            }
        });

        return processHandler;
    }

    protected GeneralCommandLine buildCommandLine() throws ExecutionException {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        VirtualFile fopExecutablePath = XslFoUtils.findFopExecutable(mySettings.getFopInstallationDir());
        if (fopExecutablePath == null) {
            throw new CantRunException("Invalid FOP installation directory");
        }
        commandLine.setExePath(fopExecutablePath.getPath());

        VirtualFile fopUserConfig = XslFoUtils.findFopUserConfig(mySettings.getUserConfigLocation());
        if (fopUserConfig != null) {
            commandLine.addParameters("-c", fopUserConfig.getPath());
        }

        // XML
        if (myXslFoRunConfiguration.getXmlInputFile() == null || myXslFoRunConfiguration.getXmlInputFile().isEmpty()) {
            throw new CantRunException("No XML input file selected");
        }
        commandLine.addParameters("-xml", myXslFoRunConfiguration.getXmlInputFile());

        // XSL
        if (myXslFoRunConfiguration.getXsltFile() == null || myXslFoRunConfiguration.getXsltFile().isEmpty()) {
            throw new CantRunException("No XSLT file selected");
        }
        commandLine.addParameters("-xsl", myXslFoRunConfiguration.getXsltFile());

        // OUTPUT FORMAT (TODO: add other formats support)
        commandLine.addParameter("-pdf");

        // OUT FILE
        commandLine.addParameter(getOutputFilePath());
        return commandLine;
    }

    private String getOutputFilePath() {
        return (temporaryFile != null) ? temporaryFile.getAbsolutePath() : myXslFoRunConfiguration.getOutputFile();
    }
}

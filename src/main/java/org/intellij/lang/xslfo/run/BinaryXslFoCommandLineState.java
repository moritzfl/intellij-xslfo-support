package org.intellij.lang.xslfo.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.intellij.lang.xslfo.XslFoSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Execution state that runs FOP as an external process from the command line.
 * Handles process creation, output formatting, and temporary file management.
 */
public class BinaryXslFoCommandLineState extends CommandLineState {

  private final XslFoRunConfiguration myXslFoRunConfiguration;
  private final File temporaryFile;

  public BinaryXslFoCommandLineState(XslFoRunConfiguration xslFoRunConfiguration,
                                     ExecutionEnvironment environment) {
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

    final OSProcessHandler processHandler =
        new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
          @Override
          public Charset getCharset() {
            return commandLine.getCharset();
          }
        };

    ProcessTerminatedListener.attach(processHandler);
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(final @NotNull ProcessEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> handleProcessTerminated(event));
      }
    });
    processHandler.startNotify();

    // Show a popup notification indicating which FOP is used (external)
    String exePath = commandLine.getExePath();
    String msg =
        "Using FOP (external): " + ("fop".equalsIgnoreCase(exePath) ? "fop (from PATH)" : exePath);
    NotificationGroupManager.getInstance()
        .getNotificationGroup("XSL-FO")
        .createNotification(msg, NotificationType.INFORMATION)
        .notify(myXslFoRunConfiguration.getProject());

    return processHandler;
  }

  private void handleProcessTerminated(@NotNull ProcessEvent event) {
    if (event.getExitCode() != 0 || myXslFoRunConfiguration.getProject().isDisposed()) {
      return;
    }
    if (myXslFoRunConfiguration.getSettings().openOutputFile()) {
      final String url = VfsUtilCore.pathToUrl(getOutputFilePath());
      final VirtualFile fileByUrl = VirtualFileManager
          .getInstance().refreshAndFindFileByUrl(url.replace(File.separatorChar, '/'));
      if (fileByUrl != null) {
        fileByUrl.refresh(true,
            false,
            () -> FopExecutionHelper.openFileInEditor(myXslFoRunConfiguration.getProject(),
                fileByUrl));
        return;
      }
    }
    VirtualFileManager.getInstance().asyncRefresh(null);
  }

  protected GeneralCommandLine buildCommandLine() throws ExecutionException {
    return ExternalFopCommandLineBuilder.buildForConfiguredSingleInput(
        myXslFoRunConfiguration,
        getOutputFilePath(),
        getEffectiveOutputFormat());
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

package org.intellij.lang.xslfo.run;

import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CommandLineState replacement that runs Apache FOP in-process using bundled libraries.
 */
class BundledFopCommandLineState extends CommandLineState {

    private final XslFoRunConfiguration config;
    private final File temporaryFile;
    private volatile List<File> generatedOutputFiles = List.of();

    BundledFopCommandLineState(@NotNull XslFoRunConfiguration config, @NotNull ExecutionEnvironment environment) {
        super(environment);
        this.config = config;
        if (config.getSettings().useTemporaryFiles()) {
            try {
                OutputFormat fmt = BundledFopRunner.getEffectiveOutputFormat(config);
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
    protected ProcessHandler startProcess() {
        InProcessFopProcessHandler handler = new InProcessFopProcessHandler();

        // Run transformation on a background thread to avoid blocking UI
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            int exitCode = 0;
            try {
                // Show a popup notification indicating which FOP is used (bundled)
                NotificationGroupManager.getInstance().getNotificationGroup("XSL-FO").createNotification("Using FOP (bundled, in-process)", NotificationType.INFORMATION).notify(config.getProject());
                runFop();
            } catch (Throwable t) {
                exitCode = 1;
                // Log full stack trace to console instead of letting it bubble to the IDE
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                pw.flush();
                handler.notifyTextAvailable(sw.toString(), ProcessOutputTypes.STDERR);
                String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("XSL-FO")
                    .createNotification("Bundled FOP execution failed: " + message,
                        NotificationType.ERROR)
                    .notify(config.getProject());
            } finally {
                handler.notifyProcessTerminated(exitCode);
                if (exitCode == 0) {
                    SwingUtilities.invokeLater(this::afterProcessSucceeded);
                }
                executor.shutdown();
            }
        });

        handler.startNotify();
        return handler;
    }

    private void afterProcessSucceeded() {
        try {
            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
            List<VirtualFile> generatedVirtualFiles = new java.util.ArrayList<>();
            for (File outputFile : generatedOutputFiles) {
                VirtualFile virtualOutput = fileSystem.refreshAndFindFileByIoFile(outputFile);
                if (virtualOutput != null && !virtualOutput.isDirectory()) {
                    generatedVirtualFiles.add(virtualOutput);
                }
            }

            if (!generatedVirtualFiles.isEmpty()) {
                if (config.getSettings().openOutputFile()) {
                    for (VirtualFile generatedVirtualFile : generatedVirtualFiles) {
                        FopExecutionHelper.openFileInEditor(config.getProject(), generatedVirtualFile);
                    }
                }
                return;
            }

            File fallbackFile = getFallbackOutputFile();
            if (fallbackFile == null) {
                return;
            }
            VirtualFile fallbackVirtualFile = fileSystem.refreshAndFindFileByIoFile(fallbackFile);
            if (fallbackVirtualFile != null && !fallbackVirtualFile.isDirectory()
                && config.getSettings().openOutputFile()) {
                FopExecutionHelper.openFileInEditor(config.getProject(), fallbackVirtualFile);
            }
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XSL-FO")
                .createNotification("Opening generated output failed: " + message,
                    NotificationType.WARNING)
                .notify(config.getProject());
        }
    }

    private File getFallbackOutputFile() {
        String outputPath = getOutputFilePath();
        if (outputPath == null || outputPath.isBlank()) {
            return null;
        }
        File output = new File(outputPath);
        return output.exists() ? output : output.getParentFile();
    }

    private void runFop() throws Exception {
        generatedOutputFiles = BundledFopRunner.runFop(config, temporaryFile);
    }

    private String getOutputFilePath() {
        String out = config.getSettings().outputFile();
        return (temporaryFile != null) ? temporaryFile.getAbsolutePath() : out;
    }

    /**
     * Minimal in-memory ProcessHandler to integrate with Run tool window lifecycle.
     */
    private static class InProcessFopProcessHandler extends com.intellij.execution.process.ProcessHandler {
        private volatile boolean terminated = false;

        @Override
        protected void destroyProcessImpl() {
            // No real process to destroy
            notifyProcessTerminated(0);
        }

        @Override
        protected void detachProcessImpl() {
            notifyProcessDetached();
        }

        @Override
        public boolean detachIsDefault() {
            return false;
        }

        @Override
        public OutputStream getProcessInput() {
            return new ByteArrayOutputStream();
        }

        @Override
        public boolean isProcessTerminated() {
            return terminated;
        }

        @Override
        public boolean isProcessTerminating() {
            return terminated;
        }

        @Override
        public void notifyProcessTerminated(int exitCode) {
            if (terminated) {
                return;
            }
            terminated = true;
            super.notifyProcessTerminated(exitCode);
        }
    }
}

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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CommandLineState replacement that runs Apache FOP in-process using bundled libraries.
 */
class BundledFopCommandLineState extends CommandLineState {

    private final XslFoRunConfiguration config;
    private final File temporaryFile;
    private volatile List<File> generatedOutputFiles = List.of();
    private volatile ExecutorService executionExecutor;
    private volatile Future<?> runningTask;
    private volatile Thread workerThread;

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
        InProcessFopProcessHandler handler = new InProcessFopProcessHandler(this::cancelExecution);

        // Run transformation on a background thread to avoid blocking UI
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executionExecutor = executor;
        runningTask = executor.submit(() -> {
            int exitCode = 0;
            try {
                workerThread = Thread.currentThread();
                if (handler.isCancellationRequested() || Thread.currentThread().isInterrupted()) {
                    exitCode = 130;
                    return;
                }
                // Show a popup notification indicating which FOP is used (bundled)
                NotificationGroupManager.getInstance().getNotificationGroup("XSL-FO").createNotification("Using FOP (bundled, in-process)", NotificationType.INFORMATION).notify(config.getProject());
                runFop();
            } catch (Throwable t) {
                if (isCancellationThrowable(t, handler)) {
                    exitCode = 130;
                    if (t instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } else {
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
                }
            } finally {
                workerThread = null;
                boolean cancelled = handler.isCancellationRequested();
                if (!cancelled) {
                    handler.notifyProcessTerminated(exitCode);
                    if (exitCode == 0) {
                        SwingUtilities.invokeLater(() -> {
                            if (!handler.isCancellationRequested()) {
                                afterProcessSucceeded();
                            }
                        });
                    }
                }
                runningTask = null;
                executionExecutor = null;
                executor.shutdown();
            }
        });

        handler.startNotify();
        return handler;
    }

    private void cancelExecution() {
        Future<?> task = runningTask;
        if (task != null) {
            task.cancel(true);
        }
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
        ExecutorService executor = executionExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static boolean isCancellationThrowable(@NotNull Throwable throwable,
                                                   @NotNull InProcessFopProcessHandler handler) {
        return handler.isCancellationRequested()
            || throwable instanceof InterruptedException
            || throwable instanceof CancellationException;
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
        private final Runnable cancellationCallback;
        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        private volatile boolean terminated = false;
        private volatile boolean terminating = false;

        private InProcessFopProcessHandler(@NotNull Runnable cancellationCallback) {
            this.cancellationCallback = cancellationCallback;
        }

        @Override
        protected void destroyProcessImpl() {
            requestCancellation();
            notifyProcessTerminated(130);
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
            return terminating && !terminated;
        }

        @Override
        public void notifyProcessTerminated(int exitCode) {
            if (terminated) {
                return;
            }
            terminated = true;
            terminating = false;
            super.notifyProcessTerminated(exitCode);
        }

        boolean isCancellationRequested() {
            return cancellationRequested.get();
        }

        private void requestCancellation() {
            if (!cancellationRequested.compareAndSet(false, true)) {
                return;
            }
            terminating = true;
            cancellationCallback.run();
        }
    }
}

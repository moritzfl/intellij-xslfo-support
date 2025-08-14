package org.intellij.lang.xslfo.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.intellij.lang.xslfo.XslFoSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.fop.apps.*;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

/**
 * CommandLineState replacement that runs Apache FOP in-process using bundled libraries.
 */
class BundledFopCommandLineState extends CommandLineState {

    private final XslFoRunConfiguration config;
    private final File temporaryFile;

    BundledFopCommandLineState(@NotNull XslFoRunConfiguration config, @NotNull ExecutionEnvironment environment) {
        super(environment);
        this.config = config;
        if (config.isUseTemporaryFiles()) {
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
        InProcessFopProcessHandler handler = new InProcessFopProcessHandler();
        // Run transformation on a background thread to avoid blocking UI
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            int exitCode = 0;
            try {
                runFop();
            } catch (Throwable t) {
                exitCode = 1;
                handler.notifyTextAvailable("FOP execution failed: " + t.getMessage() + "\n", com.intellij.execution.process.ProcessOutputTypes.STDERR);
            } finally {
                int finalExitCode = exitCode;
                SwingUtilities.invokeLater(() -> afterProcessTerminated(handler, finalExitCode));
                executor.shutdown();
            }
        });
        handler.startNotify();
        return handler;
    }

    private void afterProcessTerminated(InProcessFopProcessHandler handler, int exitCode) {
        handler.notifyProcessTerminated(exitCode);
        if (exitCode == 0) {
            // replicate open-output behavior
            if (config.isOpenOutputFile()) {
                final String url = VfsUtilCore.pathToUrl(getOutputFilePath());
                final VirtualFile fileByUrl = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url.replace(File.separatorChar, '/'));
                if (fileByUrl != null) {
                    fileByUrl.refresh(true, false, () -> new OpenFileDescriptor(config.getProject(), fileByUrl).navigate(true));
                    return;
                }
            }
            VirtualFileManager.getInstance().asyncRefresh(null);
        }
    }

    private void runFop() throws IOException, SAXException, TransformerException {
        String xmlPath = config.getXmlInputFile();
        String xslPath = config.getXsltFile();
        if (xmlPath == null || xmlPath.isEmpty()) throw new IOException("No XML input file selected");
        if (xslPath == null || xslPath.isEmpty()) throw new IOException("No XSLT file selected");

        File outFile = new File(getOutputFilePath());
        // ensure parent dir exists
        File parent = outFile.getParentFile();
        if (parent != null) parent.mkdirs();

        // Configure FOP factory; optionally load user config if present
        XslFoSettings settings = XslFoSettings.getInstance();
        String userConfig = settings != null ? settings.getUserConfigLocation() : null;
        FopFactory fopFactory;
        if (userConfig != null && !userConfig.isEmpty()) {
            FopConfParser parser = new FopConfParser(new File(userConfig));
            FopFactoryBuilder builder = parser.getFopFactoryBuilder();
            fopFactory = builder.build();
        } else {
            FopFactoryBuilder builder = new FopFactoryBuilder(new File(".").toURI());
            fopFactory = builder.build();
        }
        FOUserAgent foUserAgent = fopFactory.newFOUserAgent();

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer(new StreamSource(new File(xslPath)));
            Source src = new StreamSource(new File(xmlPath));
            Result res = new SAXResult(fop.getDefaultHandler());
            transformer.transform(src, res);
        }
    }

    private String getOutputFilePath() {
        return (temporaryFile != null) ? temporaryFile.getAbsolutePath() : config.getOutputFile();
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
            terminated = true;
            super.notifyProcessTerminated(exitCode);
        }
    }
}

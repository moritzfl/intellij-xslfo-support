package org.intellij.lang.xslfo.run;

import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.intellij.lang.xslfo.XslFoSettings;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CommandLineState replacement that runs Apache FOP in-process using bundled libraries.
 */
class BundledFopCommandLineState extends CommandLineState {

    private final XslFoRunConfiguration config;
    private final File temporaryFile;

    BundledFopCommandLineState(@NotNull XslFoRunConfiguration config, @NotNull ExecutionEnvironment environment) {
        super(environment);
        this.config = config;
        if (config.getSettings().useTemporaryFiles()) {
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
    protected ProcessHandler startProcess() {
        InProcessFopProcessHandler handler = new InProcessFopProcessHandler();

        // Run transformation on a background thread to avoid blocking UI
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            int exitCode = 0;
            try {
                // Show a popup notification indicating which FOP is used (bundled)
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("XSL-FO")
                    .createNotification("Using FOP (bundled, in-process)", NotificationType.INFORMATION)
                    .notify(config.getProject());
                runFop();
            } catch (Throwable t) {
                exitCode = 1;
                // Log full stack trace to console instead of letting it bubble to the IDE
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                pw.flush();
                handler.notifyTextAvailable(sw.toString(), ProcessOutputTypes.STDERR);
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
            if (config.getSettings().openOutputFile()) {
                final String url = VfsUtilCore.pathToUrl(getOutputFilePath());
                final VirtualFile fileByUrl = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url.replace(File.separatorChar, '/'));
                if (fileByUrl != null) {
                    fileByUrl.refresh(true, false, () -> FopExecutionHelper.openFileInEditor(config.getProject(), fileByUrl));
                    return;
                }
            }
            VirtualFileManager.getInstance().asyncRefresh(null);
        }
    }

    private void runFop() throws IOException, SAXException, TransformerException {
        // Ensure JAXP factories resolve to public Xerces implementations to avoid
        // java.xml internal access restrictions in the IntelliJ plugin classloader.
        try {
            // Clear any stale overrides first
            System.clearProperty("javax.xml.parsers.SAXParserFactory");
            System.clearProperty("javax.xml.parsers.DocumentBuilderFactory");
            System.clearProperty("org.xml.sax.driver");
            // Set to public Xerces implementations (provided by xercesImpl dependency)
            System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.xerces.jaxp.SAXParserFactoryImpl");
            System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
        } catch (SecurityException ignored) {
            // If properties cannot be set, proceed; FOP/Batik may still use defaults.
        }

        String xmlPath = config.getSettings().getXmlInputFilePointer() != null ? config.getSettings().getXmlInputFilePointer().getPresentableUrl() : null;
        String xslPath = config.getSettings().getXsltFilePointer() != null ? config.getSettings().getXsltFilePointer().getPresentableUrl() : null;
        if (xmlPath == null || xmlPath.isEmpty()) throw new IOException("No XML input file selected");
        if (xslPath == null || xslPath.isEmpty()) throw new IOException("No XSLT file selected");

        File outFile = new File(getOutputFilePath());
        // ensure parent dir exists
        File parent = outFile.getParentFile();
        if (parent != null) parent.mkdirs();

        // Configure FOP factory; optionally load user config if present
        XslFoSettings pluginSettings = XslFoSettings.getInstance();
        String userConfig;
        switch (config.getSettings().configMode()) {
            case PLUGIN -> userConfig = pluginSettings != null ? pluginSettings.getUserConfigLocation() : null;
            case FILE -> userConfig = config.getSettings().configFilePath();
            default -> userConfig = null;
        }
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
            OutputFormat fmt = getEffectiveOutputFormat();
            Fop fop = fopFactory.newFop(fmt.mime(), foUserAgent, out);

            TransformerFactory factory = TransformerFactory.newInstance();

            File xsltFile = new File(xslPath);
            StreamSource xsltSource = new StreamSource(xsltFile);
            xsltSource.setSystemId(xsltFile.toURI().toString());
            Transformer transformer = factory.newTransformer(xsltSource);

            File xmlFile = new File(xmlPath);
            StreamSource xmlSource = new StreamSource(xmlFile);
            xmlSource.setSystemId(xmlFile.toURI().toString());

            Result res = new SAXResult(fop.getDefaultHandler());
            transformer.transform(xmlSource, res);
        }
    }

    private String getOutputFilePath() {
        String out = config.getSettings().outputFile();
        return (temporaryFile != null) ? temporaryFile.getAbsolutePath() : out;
    }

    private OutputFormat getEffectiveOutputFormat() {
        XslFoRunSettings s = config.getSettings();
        if (s.usePluginOutputFormat()) {
            XslFoSettings plugin = XslFoSettings.getInstance();
            return plugin != null ? plugin.getDefaultOutputFormat() : OutputFormat.PDF;
        }
        return s.outputFormat();
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

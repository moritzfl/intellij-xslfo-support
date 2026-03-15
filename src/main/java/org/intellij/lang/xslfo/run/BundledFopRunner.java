package org.intellij.lang.xslfo.run;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.io.InternalResourceResolver;
import org.apache.fop.apps.io.NullSafeInternalResourceResolver;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.EventListener;
import org.apache.fop.events.model.EventSeverity;
import org.intellij.lang.xslfo.XslFoSettings;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates in-process bundled FOP execution logic so it can be tested independently.
 */
final class BundledFopRunner {

  private static final String SAX_PARSER_FACTORY_KEY = "javax.xml.parsers.SAXParserFactory";
  private static final String DOCUMENT_BUILDER_FACTORY_KEY =
      "javax.xml.parsers.DocumentBuilderFactory";
  private static final String LEGACY_XERCES_SAX_FACTORY =
      "org.apache.xerces.jaxp.SAXParserFactoryImpl";
  private static final String LEGACY_XERCES_DOCUMENT_FACTORY =
      "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl";

  private BundledFopRunner() {
  }

  static List<File> runFop(XslFoRunConfiguration config, File temporaryFile)
      throws IOException, SAXException, TransformerException {
    return runFop(config, temporaryFile, null);
  }

  static List<File> runFop(XslFoRunConfiguration config, File temporaryFile,
                           RenderDiagnosticsSink diagnosticsSink)
      throws IOException, SAXException, TransformerException {
    clearLegacyJaxpOverrides(diagnosticsSink);

    List<String> xmlPaths = config.getSettings().getXmlInputFilesPointers().stream()
        .map(pointer -> pointer != null ? pointer.getPresentableUrl() : null)
        .filter(path -> path != null && !path.isBlank())
        .toList();
    String xslPath = config.getSettings().getXsltFilePointer() != null
        ? config.getSettings().getXsltFilePointer().getPresentableUrl()
        : null;
    if (xmlPaths.isEmpty()) {
      throw new IOException("No XML input file selected");
    }
    if (xslPath == null || xslPath.isEmpty()) {
      throw new IOException("No XSLT file selected");
    }

    // Configure FOP factory; optionally load user config if present
    XslFoSettings pluginSettings = getPluginSettingsOrNull();
    String userConfig;
    switch (config.getSettings().configMode()) {
      case PLUGIN -> userConfig =
          pluginSettings != null ? pluginSettings.getUserConfigLocation() : null;
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
    installNullSafeResourceResolver(foUserAgent, diagnosticsSink);
    if (diagnosticsSink != null) {
      // Adding a listener prevents FOUserAgent from auto-attaching its LoggingEventListener.
      foUserAgent.getEventBroadcaster().addEventListener(
          createDiagnosticsEventListener(diagnosticsSink));
    }

    List<File> outputs = new ArrayList<>();
    boolean multipleInputs = xmlPaths.size() > 1;
    for (String xmlPath : xmlPaths) {
      File outFile = resolveOutputFile(config, temporaryFile, xmlPath, multipleInputs);
      File parent = outFile.getParentFile();
      if (parent != null) {
        parent.mkdirs();
      }
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
        OutputFormat fmt = getEffectiveOutputFormat(config);
        Fop fop = fopFactory.newFop(fmt.mime(), foUserAgent, out);

        TransformerFactory factory = TransformerFactory.newInstance();
        ErrorListener errorListener = createStrictErrorListener(diagnosticsSink);
        factory.setErrorListener(errorListener);

        File xsltFile = new File(xslPath);
        StreamSource xsltSource = new StreamSource(xsltFile);
        xsltSource.setSystemId(xsltFile.toURI().toString());
        Transformer transformer = factory.newTransformer(xsltSource);
        transformer.setErrorListener(errorListener);

        File xmlFile = new File(xmlPath);
        StreamSource xmlSource = new StreamSource(xmlFile);
        xmlSource.setSystemId(xmlFile.toURI().toString());

        Result res = new SAXResult(fop.getDefaultHandler());
        transformer.transform(xmlSource, res);
      }
      outputs.add(outFile);
    }
    return List.copyOf(outputs);
  }

  private static ErrorListener createStrictErrorListener(RenderDiagnosticsSink diagnosticsSink) {
    return new ErrorListener() {
      @Override
      public void warning(TransformerException exception) {
        if (diagnosticsSink != null && exception != null && exception.getMessage() != null) {
          diagnosticsSink.warning(exception.getMessage());
        }
      }

      @Override
      public void error(TransformerException exception) throws TransformerException {
        if (diagnosticsSink != null && exception != null && exception.getMessage() != null) {
          diagnosticsSink.error(exception.getMessage());
        }
        throw exception;
      }

      @Override
      public void fatalError(TransformerException exception) throws TransformerException {
        if (diagnosticsSink != null && exception != null && exception.getMessage() != null) {
          diagnosticsSink.error(exception.getMessage());
        }
        throw exception;
      }
    };
  }

  private static void clearLegacyJaxpOverrides(RenderDiagnosticsSink diagnosticsSink) {
    clearPropertyIfMatches(SAX_PARSER_FACTORY_KEY, LEGACY_XERCES_SAX_FACTORY, diagnosticsSink);
    clearPropertyIfMatches(DOCUMENT_BUILDER_FACTORY_KEY, LEGACY_XERCES_DOCUMENT_FACTORY,
        diagnosticsSink);
  }

  private static void clearPropertyIfMatches(String key, String legacyValue,
                                             RenderDiagnosticsSink diagnosticsSink) {
    try {
      String currentValue = System.getProperty(key);
      if (legacyValue.equals(currentValue)) {
        System.clearProperty(key);
      }
    } catch (SecurityException securityException) {
      if (diagnosticsSink != null) {
        diagnosticsSink.warning("Could not clear legacy XML parser setting: " + key);
      }
    }
  }

  private static EventListener createDiagnosticsEventListener(RenderDiagnosticsSink sink) {
    return event -> {
      if (event == null) {
        return;
      }
      String formatted = EventFormatter.format(event);
      if (formatted == null || formatted.isBlank()) {
        return;
      }
      EventSeverity severity = event.getSeverity();
      if (severity == EventSeverity.WARN) {
        sink.warning(formatted);
      } else if (severity == EventSeverity.ERROR || severity == EventSeverity.FATAL) {
        sink.error(formatted);
      }
    };
  }

  private static void installNullSafeResourceResolver(FOUserAgent foUserAgent,
                                                      RenderDiagnosticsSink diagnosticsSink) {
    try {
      Field resolverField = FOUserAgent.class.getDeclaredField("resourceResolver");
      resolverField.setAccessible(true);
      Object resolver = resolverField.get(foUserAgent);
      if (!(resolver instanceof InternalResourceResolver internalResolver)) {
        return;
      }
      InternalResourceResolver wrapped =
          NullSafeInternalResourceResolver.wrap(internalResolver);
      if (wrapped != null && wrapped != internalResolver) {
        resolverField.set(foUserAgent, wrapped);
      }
    } catch (Throwable throwable) {
      if (diagnosticsSink != null) {
        diagnosticsSink.warning("Could not install null-safe resource resolver: "
            + throwable.getClass().getSimpleName());
      }
    }
  }

  static OutputFormat getEffectiveOutputFormat(XslFoRunConfiguration config) {
    XslFoRunSettings settings = config.getSettings();
    if (settings.usePluginOutputFormat()) {
      XslFoSettings plugin = getPluginSettingsOrNull();
      return plugin != null ? plugin.getDefaultOutputFormat() : OutputFormat.PDF;
    }
    return settings.outputFormat();
  }

  private static File resolveOutputFile(XslFoRunConfiguration config, File temporaryFile,
                                        String xmlPath, boolean multipleInputs) {
    if (!multipleInputs) {
      return new File(getOutputFilePath(config, temporaryFile));
    }
    String configuredOut = config.getSettings().outputFile();
    File outputDir = temporaryFile != null ? temporaryFile.getParentFile() : new File(configuredOut);
    if (outputDir == null) {
      outputDir = new File(System.getProperty("java.io.tmpdir"));
    }
    OutputFormat fmt = getEffectiveOutputFormat(config);
    String fileName = new File(xmlPath).getName();
    int dot = fileName.lastIndexOf('.');
    String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
    return new File(outputDir, baseName + fmt.extension());
  }

  private static String getOutputFilePath(XslFoRunConfiguration config, File temporaryFile) {
    String configuredOut = config.getSettings().outputFile();
    return temporaryFile != null ? temporaryFile.getAbsolutePath() : configuredOut;
  }

  private static XslFoSettings getPluginSettingsOrNull() {
    try {
      return XslFoSettings.getInstance();
    } catch (Throwable ignored) {
      return null;
    }
  }

  interface RenderDiagnosticsSink {
    void warning(String message);

    void error(String message);
  }
}

package org.intellij.lang.xslfo.run;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.intellij.lang.xslfo.XslFoSettings;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates in-process bundled FOP execution logic so it can be tested independently.
 */
final class BundledFopRunner {

  private BundledFopRunner() {
  }

  static List<File> runFop(XslFoRunConfiguration config, File temporaryFile)
      throws IOException, SAXException, TransformerException {
    // Ensure JAXP factories resolve to public Xerces implementations to avoid
    // java.xml internal access restrictions in the IntelliJ plugin classloader.
    try {
      // Clear any stale overrides first
      System.clearProperty("javax.xml.parsers.SAXParserFactory");
      System.clearProperty("javax.xml.parsers.DocumentBuilderFactory");
      System.clearProperty("org.xml.sax.driver");
      // Set to public Xerces implementations (provided by xercesImpl dependency)
      System.setProperty("javax.xml.parsers.SAXParserFactory",
          "org.apache.xerces.jaxp.SAXParserFactoryImpl");
      System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
          "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
    } catch (SecurityException ignored) {
      // If properties cannot be set, proceed; FOP/Batik may still use defaults.
    }

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
      outputs.add(outFile);
    }
    return List.copyOf(outputs);
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
}

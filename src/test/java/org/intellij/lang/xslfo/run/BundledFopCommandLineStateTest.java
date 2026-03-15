package org.intellij.lang.xslfo.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BundledFopCommandLineStateTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test(timeout = 15000)
  public void bundledRunner_rendersAllSimpleInputs() throws Exception {
    XslFoRunConfiguration config = createConfigurationForSimpleInputs();

    List<File> outputs = BundledFopRunner.runFop(config, null);

    assertEquals(2, outputs.size());
    assertEquals("values.pdf", outputs.get(0).getName());
    assertEquals("values_2.pdf", outputs.get(1).getName());
    assertTrue(outputs.get(0).exists());
    assertTrue(outputs.get(1).exists());
    assertTrue(outputs.get(0).length() > 0);
    assertTrue(outputs.get(1).length() > 0);
  }

  @Test
  public void bundledRunner_resolvesMultipleOutputsInsideConfiguredDirectory() throws Exception {
    XslFoRunConfiguration config = createConfigurationForSimpleInputs();

    List<File> outputs = BundledFopRunner.runFop(config, null);
    File outputDir = new File(config.getSettings().outputFile());

    assertEquals(outputDir.getCanonicalPath(), outputs.get(0).getParentFile().getCanonicalPath());
    assertEquals(outputDir.getCanonicalPath(), outputs.get(1).getParentFile().getCanonicalPath());
  }

  @Test(timeout = 15000)
  public void bundledRunner_doesNotMutateGlobalJaxpSystemProperties() throws Exception {
    XslFoRunConfiguration config = createConfigurationForSimpleInputs();

    String originalSaxFactory = System.getProperty("javax.xml.parsers.SAXParserFactory");
    String originalDocumentFactory = System.getProperty("javax.xml.parsers.DocumentBuilderFactory");
    String originalSaxDriver = System.getProperty("org.xml.sax.driver");
    String expectedSaxFactory = "org.apache.xerces.jaxp.SAXParserFactoryImpl".equals(originalSaxFactory)
        ? null : originalSaxFactory;
    String expectedDocumentFactory =
        "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl".equals(originalDocumentFactory)
            ? null : originalDocumentFactory;
    try {
      BundledFopRunner.runFop(config, null);
    } finally {
      assertEquals(expectedSaxFactory, System.getProperty("javax.xml.parsers.SAXParserFactory"));
      assertEquals(expectedDocumentFactory,
          System.getProperty("javax.xml.parsers.DocumentBuilderFactory"));
      assertEquals(originalSaxDriver, System.getProperty("org.xml.sax.driver"));
    }
  }

  @Test(timeout = 15000)
  public void bundledPreviewDiagnostics_nullImageUriDoesNotThrowInternalResolverNpe() throws Exception {
    Project project = XslFoRunExecutorTestHelper.createTestProject();
    XslFoRunConfiguration config = new XslFoRunConfiguration(project,
        XslFoRunExecutorTestHelper.createTestFactory());

    File outputDir = temporaryFolder.newFolder("null-uri");
    File template = temporaryFolder.newFile("template-null-image.xsl");
    File xml = temporaryFolder.newFile("values.xml");
    Files.writeString(template.toPath(), templateWithSvgImageWithoutHref(), StandardCharsets.UTF_8);
    Files.writeString(xml.toPath(), "<root/>", StandardCharsets.UTF_8);

    XslFoRunSettings settings = config.getSettings()
        .withXsltFile(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer(
            template.getAbsolutePath()))
        .withXmlInputFiles(List.of(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer(
            xml.getAbsolutePath())))
        .withOutputFile(new File(outputDir, "out.pdf").getAbsolutePath())
        .withOpenOutputFile(false)
        .withUseTemporaryFiles(false)
        .withExecutionMode(ExecutionMode.BUNDLED)
        .withConfigMode(SettingsFileMode.EMPTY)
        .withUsePluginOutputFormat(false)
        .withOutputFormat(OutputFormat.PDF);
    config.setSettings(settings);

    List<String> diagnostics = new ArrayList<>();
    try {
      BundledFopRunner.runFop(config, null, new BundledFopRunner.RenderDiagnosticsSink() {
        @Override
        public void warning(String message) {
          diagnostics.add("WARN: " + message);
        }

        @Override
        public void error(String message) {
          diagnostics.add("ERROR: " + message);
        }
      });
    } catch (Exception exception) {
      String message = collectMessages(exception, diagnostics);
      org.junit.Assert.assertFalse(message.contains("Cannot invoke \"String.startsWith(String)\""));
      org.junit.Assert.assertFalse(message.contains("InternalResourceResolver.getResource"));
    }
  }

  private XslFoRunConfiguration createConfigurationForSimpleInputs() throws Exception {
    Project project = XslFoRunExecutorTestHelper.createTestProject();
    XslFoRunConfiguration config = new XslFoRunConfiguration(project,
        XslFoRunExecutorTestHelper.createTestFactory());

    File template = new File("src/test/resources/simple/template.xsl").getCanonicalFile();
    File xml1 = new File("src/test/resources/simple/values.xml").getCanonicalFile();
    File xml2 = new File("src/test/resources/simple/values_2.xml").getCanonicalFile();
    File outputDir = temporaryFolder.newFolder("multi-output");

    List<VirtualFilePointer> xmlPointers = List.of(
        new XslFoRunExecutorTestHelper.FakeVirtualFilePointer(xml1.getAbsolutePath()),
        new XslFoRunExecutorTestHelper.FakeVirtualFilePointer(xml2.getAbsolutePath()));

    XslFoRunSettings settings = config.getSettings()
        .withXsltFile(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer(
            template.getAbsolutePath()))
        .withXmlInputFiles(xmlPointers)
        .withOutputFile(outputDir.getAbsolutePath())
        .withOpenOutputFile(false)
        .withUseTemporaryFiles(false)
        .withExecutionMode(ExecutionMode.BUNDLED)
        .withConfigMode(SettingsFileMode.EMPTY)
        .withUsePluginOutputFormat(false)
        .withOutputFormat(OutputFormat.PDF);
    config.setSettings(settings);
    return config;
  }

  private static String templateWithSvgImageWithoutHref() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <xsl:stylesheet version="1.0"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:fo="http://www.w3.org/1999/XSL/Format"
            xmlns:svg="http://www.w3.org/2000/svg">
          <xsl:template match="/">
            <fo:root>
              <fo:layout-master-set>
                <fo:simple-page-master master-name="p" page-width="210mm" page-height="297mm">
                  <fo:region-body/>
                </fo:simple-page-master>
              </fo:layout-master-set>
              <fo:page-sequence master-reference="p">
                <fo:flow flow-name="xsl-region-body">
                  <fo:block>Before image</fo:block>
                  <fo:instream-foreign-object>
                    <svg:svg width="100" height="100">
                      <svg:image width="100" height="100"/>
                    </svg:svg>
                  </fo:instream-foreign-object>
                  <fo:block>After image</fo:block>
                </fo:flow>
              </fo:page-sequence>
            </fo:root>
          </xsl:template>
        </xsl:stylesheet>
        """;
  }

  private static String collectMessages(Exception exception, List<String> diagnostics) {
    StringBuilder sb = new StringBuilder();
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null) {
        sb.append(current.getMessage()).append('\n');
      }
      for (StackTraceElement element : current.getStackTrace()) {
        sb.append(element).append('\n');
      }
    }
    for (String diagnostic : diagnostics) {
      sb.append(diagnostic).append('\n');
    }
    return sb.toString();
  }
}

package org.intellij.lang.xslfo.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
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
}

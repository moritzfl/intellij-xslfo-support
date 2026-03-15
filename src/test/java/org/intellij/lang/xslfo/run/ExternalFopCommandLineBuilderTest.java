package org.intellij.lang.xslfo.run;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExternalFopCommandLineBuilderTest {

  @Test
  public void buildForConfiguredSingleInput_buildsExpectedCommandLine() throws Exception {
    XslFoRunConfiguration config = createConfiguration();
    XslFoRunSettings settings = config.getSettings()
        .withXsltFile(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer("/tmp/template.xsl"))
        .withXmlInputFiles(List.of(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer(
            "/tmp/input.xml")))
        .withExecutionMode(ExecutionMode.EXTERNAL)
        .withConfigMode(SettingsFileMode.EMPTY);
    config.setSettings(settings);

    GeneralCommandLine commandLine = ExternalFopCommandLineBuilder.buildForConfiguredSingleInput(
        config, "/tmp/out.pdf", OutputFormat.PDF);

    assertEquals("fop", commandLine.getExePath());
    String commandLineString = commandLine.getCommandLineString();
    assertTrue(commandLineString.contains("-xml"));
    assertTrue(commandLineString.contains("/tmp/input.xml"));
    assertTrue(commandLineString.contains("-xsl"));
    assertTrue(commandLineString.contains("/tmp/template.xsl"));
    assertTrue(commandLineString.contains("-pdf"));
    assertTrue(commandLineString.contains("/tmp/out.pdf"));
  }

  @Test
  public void buildForConfiguredSingleInput_rejectsMultipleXmlInputs() {
    XslFoRunConfiguration config = createConfiguration();
    XslFoRunSettings settings = config.getSettings()
        .withXsltFile(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer("/tmp/template.xsl"))
        .withXmlInputFiles(List.of(
            new XslFoRunExecutorTestHelper.FakeVirtualFilePointer("/tmp/input-1.xml"),
            new XslFoRunExecutorTestHelper.FakeVirtualFilePointer("/tmp/input-2.xml")))
        .withExecutionMode(ExecutionMode.EXTERNAL)
        .withConfigMode(SettingsFileMode.EMPTY);
    config.setSettings(settings);

    try {
      ExternalFopCommandLineBuilder.buildForConfiguredSingleInput(
          config, "/tmp/out.pdf", OutputFormat.PDF);
      fail("Expected CantRunException for multiple XML input files");
    } catch (Exception exception) {
      assertTrue(exception instanceof CantRunException);
      assertTrue(exception.getMessage().contains("Multiple XML input files"));
    }
  }

  @Test
  public void build_rejectsBlankXmlInput() {
    XslFoRunConfiguration config = createConfiguration();
    XslFoRunSettings settings = config.getSettings()
        .withXsltFile(new XslFoRunExecutorTestHelper.FakeVirtualFilePointer("/tmp/template.xsl"))
        .withExecutionMode(ExecutionMode.EXTERNAL)
        .withConfigMode(SettingsFileMode.EMPTY);
    config.setSettings(settings);

    try {
      ExternalFopCommandLineBuilder.build(config, "   ", "/tmp/out.pdf", OutputFormat.PDF);
      fail("Expected CantRunException for blank XML input");
    } catch (Exception exception) {
      assertTrue(exception instanceof CantRunException);
      assertTrue(exception.getMessage().contains("No XML input file selected"));
    }
  }

  @Test
  public void build_rejectsMissingXslt() {
    XslFoRunConfiguration config = createConfiguration();
    XslFoRunSettings settings = config.getSettings()
        .withExecutionMode(ExecutionMode.EXTERNAL)
        .withConfigMode(SettingsFileMode.EMPTY);
    config.setSettings(settings);

    try {
      ExternalFopCommandLineBuilder.build(config, "/tmp/input.xml", "/tmp/out.pdf", OutputFormat.PDF);
      fail("Expected CantRunException for missing XSLT");
    } catch (Exception exception) {
      assertTrue(exception instanceof CantRunException);
      assertTrue(exception.getMessage().contains("No XSLT file selected"));
    }
  }

  private static XslFoRunConfiguration createConfiguration() {
    Project project = XslFoRunExecutorTestHelper.createTestProject();
    XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
    return new XslFoRunConfiguration(project, factory);
  }
}

package org.intellij.lang.xslfo.run;

import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.junit.Test;

import java.io.File;

import static org.intellij.lang.xslfo.run.XslFoRunExecutorTestHelper.setXmlPointer;
import static org.intellij.lang.xslfo.run.XslFoRunExecutorTestHelper.setXsltPointer;
import static org.junit.Assert.*;

public class XslFoRunConfigurationTest {

    @Test
    public void checkConfigurationValidatesMissingInputs() {
        Project project = XslFoRunExecutorTestHelper.createTestProject();
        XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
        XslFoRunConfiguration config = new XslFoRunConfiguration(project, factory);

        try {
            config.checkConfiguration();
            fail("Expected exception for missing XSLT file");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("xslt"));
        }

        setXsltPointer(config);
        try {
            config.checkConfiguration();
            fail("Expected exception for missing XML file");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("xml"));
        }

        setXmlPointer(config);
        config.setUseTemporaryFiles(true); // when using temp files, output path can be omitted
        try {
            config.checkConfiguration();
        } catch (Exception e) {
            fail("checkConfiguration should not throw when both inputs are set and using temp files, but was: " + e);
        }
    }

    @Test
    public void checkConfiguration_requiresOutputPath_whenNotUsingTempFile() {
        Project project = XslFoRunExecutorTestHelper.createTestProject();
        XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
        XslFoRunConfiguration config = new XslFoRunConfiguration(project, factory);

        // Provide required inputs to reach the output path validation
        setXsltPointer(config);
        setXmlPointer(config);
        config.setUseTemporaryFiles(false);
        config.setOutputFile(null); // explicit: no output path provided

        try {
            config.checkConfiguration();
            fail("Expected RuntimeConfigurationError for missing output path when not using temp files");
        } catch (Exception ex) {
            assertTrue("Expected RuntimeConfigurationError", ex instanceof com.intellij.execution.configurations.RuntimeConfigurationError);
            assertEquals("'Save to file' path must not be empty when not writing to a temporary file", ex.getMessage());
        }

        // Also validate that whitespace-only output triggers the same error
        config.setOutputFile("   ");
        try {
            config.checkConfiguration();
            fail("Expected RuntimeConfigurationError for blank output path when not using temp files");
        } catch (Exception ex) {
            assertTrue(ex instanceof com.intellij.execution.configurations.RuntimeConfigurationError);
            assertEquals("'Save to file' path must not be empty when not writing to a temporary file", ex.getMessage());
        }

        // And that providing a value passes
        config.setOutputFile("/tmp/out.pdf");
        try {
            config.checkConfiguration();
        } catch (Exception e) {
            fail("Did not expect exception when output path is provided: " + e);
        }
    }

    @Test
    public void writeAndReadExternal_withoutFilePointers_roundtripOutputSettings() {
        Project project = XslFoRunExecutorTestHelper.createTestProject();
        XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
        XslFoRunConfiguration config = new XslFoRunConfiguration(project, factory);

        config.setOutputFile("/tmp/out.pdf");
        config.setOpenOutputFile(true);
        config.setUseTemporaryFiles(true);

        Element element = new Element("config");
        config.writeExternal(element);

        // Verify only OutputFile and useTemporaryFiles attributes are present
        Element outputEl = element.getChild("OutputFile");
        assertNotNull(outputEl);
        assertEquals("/tmp/out.pdf", outputEl.getAttributeValue("path"));
        assertEquals("true", outputEl.getAttributeValue("openOutputFile"));
        assertEquals("true", element.getAttributeValue("useTemporaryFiles"));

        XslFoRunConfiguration config2 = new XslFoRunConfiguration(project, factory);
        config2.readExternal(element);

        assertEquals("/tmp/out.pdf", config2.getSettings().outputFile());
        assertTrue(config2.getSettings().openOutputFile());
        assertTrue(config2.getSettings().useTemporaryFiles());
    }

    @Test
    public void checkConfiguration_validatesMultipleXmlInputsWithFolderOutput() {
        Project project = XslFoRunExecutorTestHelper.createTestProject();
        XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
        XslFoRunConfiguration config = new XslFoRunConfiguration(project, factory);

        setXsltPointer(config);
        XslFoRunExecutorTestHelper.setXmlPointers(config, "/tmp/input1.xml", "/tmp/input2.xml");
        config.setUseTemporaryFiles(false);
        File outputDir = new File(System.getProperty("java.io.tmpdir"),
            "xslfo-multi-output-" + System.nanoTime());
        assertTrue(outputDir.mkdirs() || outputDir.isDirectory());
        outputDir.deleteOnExit();
        config.setOutputFile(outputDir.getAbsolutePath());

        try {
            config.checkConfiguration();
        } catch (Exception e) {
            fail("checkConfiguration should pass when output is a folder for multiple inputs: " + e);
        }
    }

    @Test
    public void checkConfiguration_rejectsMultipleXmlInputsWithFileOutput() {
        Project project = XslFoRunExecutorTestHelper.createTestProject();
        XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
        XslFoRunConfiguration config = new XslFoRunConfiguration(project, factory);

        setXsltPointer(config);
        XslFoRunExecutorTestHelper.setXmlPointers(config, "/tmp/input1.xml", "/tmp/input2.xml");
        config.setUseTemporaryFiles(false);
        config.setOutputFile("/tmp/singlefile.pdf");

        try {
            config.checkConfiguration();
            fail("Expected RuntimeConfigurationError when multiple XML inputs but output is a file, not a folder");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("directory"));
        }
    }

    @Test
    public void checkConfiguration_acceptsMultipleXmlInputsWithNewDirectoryPathEndingWithSeparator() {
        Project project = XslFoRunExecutorTestHelper.createTestProject();
        XslFoConfigurationFactory factory = XslFoRunExecutorTestHelper.createTestFactory();
        XslFoRunConfiguration config = new XslFoRunConfiguration(project, factory);

        setXsltPointer(config);
        XslFoRunExecutorTestHelper.setXmlPointers(config, "/tmp/input1.xml", "/tmp/input2.xml");
        config.setUseTemporaryFiles(false);
        config.setOutputFile(System.getProperty("java.io.tmpdir") + File.separator + "xslfo-new-output-dir" + File.separator);

        try {
            config.checkConfiguration();
        } catch (Exception e) {
            fail("checkConfiguration should pass for new output directory paths ending with separator: " + e);
        }
    }
}

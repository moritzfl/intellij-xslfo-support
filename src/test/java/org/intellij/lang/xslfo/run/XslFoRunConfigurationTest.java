package org.intellij.lang.xslfo.run;

import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.junit.Test;

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
        try {
            config.checkConfiguration();
        } catch (Exception e) {
            fail("checkConfiguration should not throw when both inputs are set, but was: " + e);
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
}

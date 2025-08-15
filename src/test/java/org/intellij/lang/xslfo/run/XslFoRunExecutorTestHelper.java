package org.intellij.lang.xslfo.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

/**
 * Test helper for creating and preparing XslFoRunConfiguration instances in tests.
 */
public final class XslFoRunExecutorTestHelper {

    private XslFoRunExecutorTestHelper() {}

    /**
     * Creates a minimal fake ConfigurationFactory to use for constructing configurations in tests.
     */
    public static @NotNull XslFoConfigurationFactory createTestFactory() {
        return new XslFoConfigurationFactory(new XslFoRunConfigType()) {
        };
    }

    /**
     * Creates a minimal fake Project using Mockito.
     */
    public static @NotNull Project createTestProject() {
        // In tests we don't need a real Project; most usages are for passing through.
        return org.mockito.Mockito.mock(Project.class);
    }


    record FakeVirtualFilePointer(String presentableUrl) implements VirtualFilePointer {
        @Override
        public @NotNull String getUrl() { return presentableUrl; }
        @Override
        public @NotNull String getFileName() { return presentableUrl; }
        @Override
        public VirtualFile getFile() { return null; }
        @Override
        public @NotNull String getPresentableUrl() { return presentableUrl; }
        @Override
        public boolean isValid() { return true; }
    }

    static void setXsltPointer(XslFoRunConfiguration cfg) {
        cfg.setSettings(cfg.getSettings().withXsltFile(new FakeVirtualFilePointer("/tmp/dummy.xsl")));
    }

    static void setXmlPointer(XslFoRunConfiguration cfg) {
        cfg.setSettings(cfg.getSettings().withXmlInputFile(new FakeVirtualFilePointer("/tmp/dummy.xml")));
    }
}

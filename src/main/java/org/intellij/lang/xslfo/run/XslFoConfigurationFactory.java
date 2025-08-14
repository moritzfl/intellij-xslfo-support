package org.intellij.lang.xslfo.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry_Cherkas
 */
public class XslFoConfigurationFactory extends ConfigurationFactory {

    public XslFoConfigurationFactory(ConfigurationType type) {
        super(type);
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        // Decide default implementation: use external binary if configured, otherwise bundled FOP
        org.intellij.lang.xslfo.XslFoSettings settings = org.intellij.lang.xslfo.XslFoSettings.getInstance();
        org.intellij.lang.xslfo.XslFoUtils utils = new org.intellij.lang.xslfo.XslFoUtils();
        com.intellij.openapi.vfs.VirtualFile fop = org.intellij.lang.xslfo.XslFoUtils.findFopExecutable(settings != null ? settings.getFopInstallationDir() : null);
        if (fop != null) {
            return new BinaryXslFoRunConfiguration(project, this);
        }
        return new BundledFopRunConfiguration(project, this);
    }

    @Override
    public @NotNull
    @NonNls String getId() {
        return this.getName();
    }

    @Override
    public @NotNull String getName() {
        return "XSL-FO";
    }
}

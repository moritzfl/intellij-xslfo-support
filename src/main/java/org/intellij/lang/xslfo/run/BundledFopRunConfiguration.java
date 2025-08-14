package org.intellij.lang.xslfo.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Run configuration that uses bundled Apache FOP libraries to execute in-process.
 */
public class BundledFopRunConfiguration extends XslFoRunConfiguration {

    public BundledFopRunConfiguration(Project project, ConfigurationFactory factory) {
        super(project, factory);
    }

    @NotNull
    @Override
    protected RunProfileState createState(@NotNull ExecutionEnvironment environment) throws ExecutionException {
        return new BundledFopCommandLineState(this, environment);
    }
}

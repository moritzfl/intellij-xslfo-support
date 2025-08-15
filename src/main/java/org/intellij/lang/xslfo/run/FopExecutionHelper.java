package org.intellij.lang.xslfo.run;

import org.intellij.lang.xslfo.XslFoSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Helper to resolve which FOP implementation should be used for a given run configuration.
 * It centralizes the decision of "bundled vs external" based on the run configuration's
 * ExecutionMode and (if needed) the plugin-level settings.
 */
public final class FopExecutionHelper {
    private FopExecutionHelper() {}

    /**
     * Determines whether the bundled in-process FOP should be used for the given configuration.
     *
     * Logic (preserved from prior inline implementation):
     * - If ExecutionMode.PLUGIN: follow the plugin setting XslFoSettings.isUseBundledFop().
     * - If ExecutionMode.BUNDLED: use bundled FOP.
     * - If ExecutionMode.EXTERNAL: use external/binary FOP.
     */
    public static boolean useBundledFop(@NotNull XslFoRunConfiguration runConfiguration) {
        ExecutionMode mode = runConfiguration.getSettings().executionMode();
        if (mode == ExecutionMode.PLUGIN) {
            XslFoSettings settings = XslFoSettings.getInstance();
            boolean pluginBundled = settings != null && settings.isUseBundledFop();
            return pluginBundled;
        }
        return mode == ExecutionMode.BUNDLED;
    }
}

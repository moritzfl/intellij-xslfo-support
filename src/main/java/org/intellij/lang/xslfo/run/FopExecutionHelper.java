package org.intellij.lang.xslfo.run;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.xslfo.XslFoSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Helper utilities for FOP execution and post-processing.
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

    /**
     * Opens the given file in the IDE. If the file type is unknown (no association), open it as plain text.
     */
    public static void openFileInEditor(@NotNull Project project, @NotNull VirtualFile file) {
        FileType type = file.getFileType();
        if (type instanceof UnknownFileType) {
            // Force open in text editor
            FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, 0), true);
        } else {
            // Use default navigation/editor for known types
            new OpenFileDescriptor(project, file).navigate(true);
        }
    }
}

package org.intellij.lang.xslfo.run;

import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A grouped, type-safe container for XSL-FO run configuration settings.
 * <br>
 * This value object allows passing configuration around as a single unit,
 * using sensible types for file references (VirtualFilePointer) instead of raw strings.
 * It also supports per-run FOP settings overrides, with default behavior to use plugin defaults.
 */
public record XslFoRunSettings(@Nullable VirtualFilePointer xsltFile,
                               @Nullable VirtualFilePointer xmlInputFile,
                               @Nullable String outputFile,
                               boolean openOutputFile,
                               boolean useTemporaryFiles,
                               // FOP execution selection
                               @NotNull ExecutionMode executionMode,
                               @Nullable String fopInstallationDirOverride,
                               // FOP configuration (user config) source selection
                               @NotNull SettingsFileMode configMode,
                               @Nullable String configFilePath) implements Cloneable {

    public @Nullable VirtualFilePointer getXsltFilePointer() {
        return xsltFile;
    }

    public @Nullable VirtualFilePointer getXmlInputFilePointer() {
        return xmlInputFile;
    }

    public XslFoRunSettings withXsltFile(@Nullable VirtualFilePointer newXslt) {
        return new XslFoRunSettings(newXslt, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, configMode, configFilePath);
    }

    public XslFoRunSettings withXmlInputFile(@Nullable VirtualFilePointer newXml) {
        return new XslFoRunSettings(xsltFile, newXml, outputFile, openOutputFile, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, configMode, configFilePath);
    }

    public XslFoRunSettings withOutputFile(@Nullable String newOutput) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, newOutput, openOutputFile, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, configMode, configFilePath);
    }

    public XslFoRunSettings withOpenOutputFile(boolean newOpen) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, newOpen, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, configMode, configFilePath);
    }

    public XslFoRunSettings withUseTemporaryFiles(boolean newUseTemp) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, newUseTemp,
                executionMode, fopInstallationDirOverride, configMode, configFilePath);
    }

    public XslFoRunSettings withFopInstallationDirOverride(@Nullable String dir) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                executionMode, dir, configMode, configFilePath);
    }

    public XslFoRunSettings withExecutionMode(@NotNull ExecutionMode mode) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                mode, fopInstallationDirOverride, configMode, configFilePath);
    }

    public XslFoRunSettings withConfigMode(@NotNull SettingsFileMode mode) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, mode, configFilePath);
    }

    public XslFoRunSettings withConfigFilePath(@Nullable String path) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, configMode, path);
    }

    /**
     * Creates a copy of this settings object.
     * <br>
     * Note: VirtualFilePointer references are not duplicated and will be shared in the clone.
     */
    public @NotNull XslFoRunSettings clone() {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                executionMode, fopInstallationDirOverride, configMode, configFilePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XslFoRunSettings that)) return false;
        return openOutputFile == that.openOutputFile
                && useTemporaryFiles == that.useTemporaryFiles
                && executionMode == that.executionMode
                && Objects.equals(xsltFile, that.xsltFile)
                && Objects.equals(xmlInputFile, that.xmlInputFile)
                && Objects.equals(outputFile, that.outputFile)
                && Objects.equals(fopInstallationDirOverride, that.fopInstallationDirOverride)
                && configMode == that.configMode
                && Objects.equals(configFilePath, that.configFilePath);
    }

    @Override
    public @NotNull String toString() {
        return "XslFoRunSettings{" +
                "xsltFile=" + (xsltFile != null ? xsltFile.getPresentableUrl() : "null") +
                ", xmlInputFile=" + (xmlInputFile != null ? xmlInputFile.getPresentableUrl() : "null") +
                ", outputFile='" + outputFile + '\'' +
                ", openOutputFile=" + openOutputFile +
                ", useTemporaryFiles=" + useTemporaryFiles +
                ", executionMode=" + executionMode +
                ", fopInstallationDirOverride='" + fopInstallationDirOverride + '\'' +
                ", configMode=" + configMode +
                ", configFilePath='" + configFilePath + '\'' +
                '}';
    }
}

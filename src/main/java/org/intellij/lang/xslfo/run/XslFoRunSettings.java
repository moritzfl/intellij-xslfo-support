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
                               // FOP resolution: by default use plugin defaults; if false, use the override fields below
                               boolean usePluginDefaultFopSettings,
                               @Nullable String fopInstallationDirOverride,
                               @Nullable String userConfigLocationOverride,
                               boolean useBundledFopOverride) implements Cloneable {

    public @Nullable VirtualFilePointer getXsltFilePointer() {
        return xsltFile;
    }

    public @Nullable VirtualFilePointer getXmlInputFilePointer() {
        return xmlInputFile;
    }

    public XslFoRunSettings withXsltFile(@Nullable VirtualFilePointer newXslt) {
        return new XslFoRunSettings(newXslt, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withXmlInputFile(@Nullable VirtualFilePointer newXml) {
        return new XslFoRunSettings(xsltFile, newXml, outputFile, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withOutputFile(@Nullable String newOutput) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, newOutput, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withOpenOutputFile(boolean newOpen) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, newOpen, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withUseTemporaryFiles(boolean newUseTemp) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, newUseTemp,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withUsePluginDefaultFopSettings(boolean useDefaults) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                useDefaults, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withFopInstallationDirOverride(@Nullable String dir) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, dir, userConfigLocationOverride, useBundledFopOverride);
    }

    public XslFoRunSettings withUserConfigLocationOverride(@Nullable String userConfig) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfig, useBundledFopOverride);
    }

    public XslFoRunSettings withUseBundledFopOverride(boolean useBundled) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundled);
    }

    /**
     * Creates a copy of this settings object.
     * <br>
     * Note: VirtualFilePointer references are not duplicated and will be shared in the clone.
     */
    public @NotNull XslFoRunSettings clone() {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles,
                usePluginDefaultFopSettings, fopInstallationDirOverride, userConfigLocationOverride, useBundledFopOverride);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XslFoRunSettings that)) return false;
        return openOutputFile == that.openOutputFile
                && useTemporaryFiles == that.useTemporaryFiles
                && usePluginDefaultFopSettings == that.usePluginDefaultFopSettings
                && useBundledFopOverride == that.useBundledFopOverride
                && Objects.equals(xsltFile, that.xsltFile)
                && Objects.equals(xmlInputFile, that.xmlInputFile)
                && Objects.equals(outputFile, that.outputFile)
                && Objects.equals(fopInstallationDirOverride, that.fopInstallationDirOverride)
                && Objects.equals(userConfigLocationOverride, that.userConfigLocationOverride);
    }

    @Override
    public @NotNull String toString() {
        return "XslFoRunSettings{" +
                "xsltFile=" + (xsltFile != null ? xsltFile.getPresentableUrl() : "null") +
                ", xmlInputFile=" + (xmlInputFile != null ? xmlInputFile.getPresentableUrl() : "null") +
                ", outputFile='" + outputFile + '\'' +
                ", openOutputFile=" + openOutputFile +
                ", useTemporaryFiles=" + useTemporaryFiles +
                ", usePluginDefaultFopSettings=" + usePluginDefaultFopSettings +
                ", fopInstallationDirOverride='" + fopInstallationDirOverride + '\'' +
                ", userConfigLocationOverride='" + userConfigLocationOverride + '\'' +
                ", useBundledFopOverride=" + useBundledFopOverride +
                '}';
    }
}

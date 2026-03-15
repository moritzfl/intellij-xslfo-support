package org.intellij.lang.xslfo.run;

import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                               @NotNull List<VirtualFilePointer> xmlInputFiles,
                               @Nullable String outputFile,
                               boolean openOutputFile,
                               boolean useTemporaryFiles,
                               // FOP execution selection
                               @NotNull ExecutionMode executionMode,
                               @Nullable String fopInstallationDirOverride,
                               // FOP configuration (user config) source selection
                               @NotNull SettingsFileMode configMode,
                               @Nullable String configFilePath,
                               // Output format selection
                               boolean usePluginOutputFormat,
                               @NotNull OutputFormat outputFormat) implements Cloneable {

  public XslFoRunSettings {
    xmlInputFiles = xmlInputFiles == null ? List.of() : List.copyOf(xmlInputFiles);
  }

  public @NotNull XslFoRunSettings withUsePluginOutputFormat(boolean value) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile,
        openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath, value, outputFormat);
  }

  public @NotNull XslFoRunSettings withOutputFormat(@NotNull OutputFormat format) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile,
        openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, format);
  }

  public @Nullable VirtualFilePointer getXsltFilePointer() {
    return xsltFile;
  }

  public @Nullable VirtualFilePointer getXmlInputFilePointer() {
    if (xmlInputFile != null) {
      return xmlInputFile;
    }
    return xmlInputFiles.isEmpty() ? null : xmlInputFiles.get(0);
  }

  public @NotNull List<VirtualFilePointer> getXmlInputFilesPointers() {
    if (!xmlInputFiles.isEmpty()) {
      return xmlInputFiles;
    }
    return xmlInputFile == null ? List.of() : List.of(xmlInputFile);
  }

  public XslFoRunSettings withXsltFile(@Nullable VirtualFilePointer newXslt) {
    return new XslFoRunSettings(newXslt, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withXmlInputFile(@Nullable VirtualFilePointer newXml) {
    return new XslFoRunSettings(xsltFile, newXml,
        newXml == null ? List.of() : List.of(newXml), outputFile, openOutputFile, useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withXmlInputFiles(@NotNull List<VirtualFilePointer> newXmls) {
    List<VirtualFilePointer> copy = new ArrayList<>(newXmls);
    VirtualFilePointer primary = copy.isEmpty() ? null : copy.get(0);
    return new XslFoRunSettings(xsltFile, primary, Collections.unmodifiableList(copy), outputFile,
        openOutputFile, useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withOutputFile(@Nullable String newOutput) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, newOutput, openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withOpenOutputFile(boolean newOpen) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, newOpen,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withUseTemporaryFiles(boolean newUseTemp) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        newUseTemp,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withFopInstallationDirOverride(@Nullable String dir) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        useTemporaryFiles,
        executionMode, dir, configMode, configFilePath, usePluginOutputFormat, outputFormat);
  }

  public XslFoRunSettings withExecutionMode(@NotNull ExecutionMode mode) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        useTemporaryFiles,
        mode, fopInstallationDirOverride, configMode, configFilePath, usePluginOutputFormat,
        outputFormat);
  }

  public XslFoRunSettings withConfigMode(@NotNull SettingsFileMode mode) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, mode, configFilePath, usePluginOutputFormat,
        outputFormat);
  }

  public XslFoRunSettings withConfigFilePath(@Nullable String path) {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, path, usePluginOutputFormat,
        outputFormat);
  }

  /**
   * Creates a copy of this settings object.
   * <br>
   * Note: VirtualFilePointer references are not duplicated and will be shared in the clone.
   */
  public @NotNull XslFoRunSettings clone() {
    return new XslFoRunSettings(xsltFile, xmlInputFile, xmlInputFiles, outputFile, openOutputFile,
        useTemporaryFiles,
        executionMode, fopInstallationDirOverride, configMode, configFilePath,
        usePluginOutputFormat, outputFormat);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof XslFoRunSettings that)) {
      return false;
    }
    return openOutputFile == that.openOutputFile
        && useTemporaryFiles == that.useTemporaryFiles
        && executionMode == that.executionMode
        && Objects.equals(xsltFile, that.xsltFile)
        && Objects.equals(xmlInputFile, that.xmlInputFile)
        && Objects.equals(xmlInputFiles, that.xmlInputFiles)
        && Objects.equals(outputFile, that.outputFile)
        && Objects.equals(fopInstallationDirOverride, that.fopInstallationDirOverride)
        && configMode == that.configMode
        && Objects.equals(configFilePath, that.configFilePath)
        && usePluginOutputFormat == that.usePluginOutputFormat
        && outputFormat == that.outputFormat;
  }

  @Override
  public @NotNull String toString() {
    return "XslFoRunSettings{" +
        "xsltFile=" + (xsltFile != null ? xsltFile.getPresentableUrl() : "null") +
        ", xmlInputFile=" + (xmlInputFile != null ? xmlInputFile.getPresentableUrl() : "null") +
        ", xmlInputFiles=" + xmlInputFiles.stream().map(p -> p != null ? p.getPresentableUrl() : "null").toList() +
        ", outputFile='" + outputFile + '\'' +
        ", openOutputFile=" + openOutputFile +
        ", useTemporaryFiles=" + useTemporaryFiles +
        ", executionMode=" + executionMode +
        ", fopInstallationDirOverride='" + fopInstallationDirOverride + '\'' +
        ", configMode=" + configMode +
        ", configFilePath='" + configFilePath + '\'' +
        ", usePluginOutputFormat=" + usePluginOutputFormat +
        ", outputFormat=" + outputFormat +
        '}';
  }
}

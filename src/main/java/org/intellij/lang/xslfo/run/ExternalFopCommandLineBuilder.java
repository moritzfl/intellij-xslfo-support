package org.intellij.lang.xslfo.run;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.xslfo.XslFoSettings;
import org.intellij.lang.xslfo.XslFoUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Builds command lines for external FOP execution so run/preview paths share one implementation.
 */
final class ExternalFopCommandLineBuilder {

  private ExternalFopCommandLineBuilder() {
  }

  static @NotNull GeneralCommandLine buildForConfiguredSingleInput(
      @NotNull XslFoRunConfiguration configuration,
      @NotNull String outputPath,
      @NotNull OutputFormat outputFormat) throws ExecutionException {
    List<String> xmlInputs = configuration.getSettings().getXmlInputFilesPointers().stream()
        .map(pointer -> pointer != null ? pointer.getPresentableUrl() : null)
        .filter(path -> path != null && !path.isBlank())
        .toList();
    if (xmlInputs.isEmpty()) {
      throw new CantRunException("No XML input file selected");
    }
    if (xmlInputs.size() > 1) {
      throw new CantRunException(
          "Multiple XML input files are currently supported only with bundled FOP execution mode");
    }
    return build(configuration, xmlInputs.get(0), outputPath, outputFormat);
  }

  static @NotNull GeneralCommandLine build(@NotNull XslFoRunConfiguration configuration,
                                           @NotNull String xmlInputPath,
                                           @NotNull String outputPath,
                                           @NotNull OutputFormat outputFormat)
      throws ExecutionException {
    String normalizedXmlInput = normalizeNonBlank(xmlInputPath, "No XML input file selected");
    String normalizedOutput = normalizeNonBlank(outputPath, "No output file selected");

    String xsltPath = configuration.getSettings().getXsltFilePointer() != null
        ? configuration.getSettings().getXsltFilePointer().getPresentableUrl()
        : null;
    if (xsltPath == null || xsltPath.isBlank()) {
      throw new CantRunException("No XSLT file selected");
    }

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(resolveFopExecutablePath(configuration));

    VirtualFile userConfig = XslFoUtils.findFopUserConfig(resolveUserConfigPath(configuration));
    if (userConfig != null) {
      commandLine.addParameters("-c", userConfig.getPath());
    }

    commandLine.addParameters("-xml", normalizedXmlInput);
    commandLine.addParameters("-xsl", xsltPath);
    commandLine.addParameter(outputFormat.cliSwitch());
    commandLine.addParameter(normalizedOutput);
    return commandLine;
  }

  private static @NotNull String resolveFopExecutablePath(
      @NotNull XslFoRunConfiguration configuration) {
    String installationDir;
    XslFoSettings pluginSettings = getPluginSettingsOrNull();
    ExecutionMode mode = configuration.getSettings().executionMode();
    if (mode == ExecutionMode.PLUGIN) {
      installationDir = pluginSettings != null ? pluginSettings.getFopInstallationDir() : null;
    } else {
      installationDir = configuration.getSettings().fopInstallationDirOverride();
    }

    VirtualFile executable = XslFoUtils.findFopExecutable(installationDir);
    if (executable != null) {
      return executable.getPath();
    }
    return "fop";
  }

  private static @Nullable String resolveUserConfigPath(
      @NotNull XslFoRunConfiguration configuration) {
    XslFoSettings pluginSettings = getPluginSettingsOrNull();
    return switch (configuration.getSettings().configMode()) {
      case PLUGIN -> pluginSettings != null ? pluginSettings.getUserConfigLocation() : null;
      case FILE -> configuration.getSettings().configFilePath();
      case EMPTY -> null;
    };
  }

  private static @NotNull String normalizeNonBlank(@Nullable String value,
                                                   @NotNull String errorMessage)
      throws CantRunException {
    if (value == null) {
      throw new CantRunException(errorMessage);
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new CantRunException(errorMessage);
    }
    return trimmed;
  }

  private static @Nullable XslFoSettings getPluginSettingsOrNull() {
    try {
      return XslFoSettings.getInstance();
    } catch (Throwable ignored) {
      return null;
    }
  }
}

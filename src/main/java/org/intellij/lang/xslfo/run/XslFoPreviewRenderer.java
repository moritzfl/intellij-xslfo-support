package org.intellij.lang.xslfo.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates single-input PDF preview output and captures rendering diagnostics.
 */
public final class XslFoPreviewRenderer {

  private static final String NULL_RESOURCE_URI_MESSAGE =
      "FOP could not resolve a referenced resource because its URI is empty/null. "
          + "Check generated image/resource src/href values and configured base paths.";

  private XslFoPreviewRenderer() {
  }

  public static @NotNull PreviewRenderResult renderPreview(
      @NotNull XslFoRunConfiguration runConfiguration,
      @NotNull String xmlInputPath) {
    List<PreviewRenderMessage> messages = new ArrayList<>();
    String trimmedXmlInput = xmlInputPath.trim();
    if (trimmedXmlInput.isEmpty()) {
      addMessage(messages, Severity.ERROR, "No XML input file selected");
      return new PreviewRenderResult(null, List.copyOf(messages), false);
    }

    XslFoRunConfiguration previewConfiguration =
        (XslFoRunConfiguration) runConfiguration.clone();
    previewConfiguration.setXmlInputFiles(List.of(trimmedXmlInput));

    File previewOutputFile;
    try {
      previewOutputFile = File.createTempFile("xslfo_preview_", OutputFormat.PDF.extension());
      previewOutputFile.deleteOnExit();
    } catch (IOException ioException) {
      addMessage(messages, Severity.ERROR,
          "Could not create temporary preview output file: " + ioException.getMessage());
      return new PreviewRenderResult(null, List.copyOf(messages), false);
    }

    XslFoRunSettings previewSettings = previewConfiguration.getSettings()
        .withOutputFile(previewOutputFile.getAbsolutePath())
        .withOpenOutputFile(false)
        .withUseTemporaryFiles(false)
        .withUsePluginOutputFormat(false)
        .withOutputFormat(OutputFormat.PDF);
    previewConfiguration.setSettings(previewSettings);

    try {
      if (FopExecutionHelper.useBundledFop(previewConfiguration)) {
        File output = runBundled(previewConfiguration, messages);
        return new PreviewRenderResult(output, List.copyOf(messages), true);
      }
      File output = runExternal(previewConfiguration, trimmedXmlInput, previewOutputFile, messages);
      return new PreviewRenderResult(output, List.copyOf(messages), true);
    } catch (Exception exception) {
      String message = containsNullResourceUriError(exception) ? NULL_RESOURCE_URI_MESSAGE :
          extractErrorMessage(exception);
      addMessage(messages, Severity.ERROR, message);
      return new PreviewRenderResult(null, List.copyOf(messages), false);
    }
  }

  private static @NotNull File runBundled(@NotNull XslFoRunConfiguration configuration,
                                          @NotNull List<PreviewRenderMessage> messages)
      throws Exception {
    List<File> outputs = BundledFopRunner.runFop(configuration, null, new BundledFopRunner.RenderDiagnosticsSink() {
      @Override
      public void warning(String message) {
        addMessage(messages, Severity.WARNING, message);
      }

      @Override
      public void error(String message) {
        addMessage(messages, Severity.ERROR, message);
      }
    });
    if (outputs.isEmpty()) {
      throw new IOException("Bundled FOP did not produce any output for preview.");
    }
    File produced = outputs.get(0);
    if (!produced.exists()) {
      throw new IOException("Bundled FOP did not produce a preview PDF file.");
    }
    return produced;
  }

  private static @NotNull File runExternal(@NotNull XslFoRunConfiguration configuration,
                                           @NotNull String xmlInputPath,
                                           @NotNull File outputFile,
                                           @NotNull List<PreviewRenderMessage> messages)
      throws IOException, ExecutionException {
    GeneralCommandLine commandLine = ExternalFopCommandLineBuilder.build(
        configuration,
        xmlInputPath,
        outputFile.getAbsolutePath(),
        OutputFormat.PDF);
    ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine);
    collectExternalDiagnostics(processOutput, messages);
    if (processOutput.getExitCode() != 0) {
      throw new IOException(buildExternalErrorMessage(processOutput));
    }
    if (!outputFile.exists()) {
      throw new IOException("External FOP finished without creating preview PDF output.");
    }
    return outputFile;
  }

  private static void collectExternalDiagnostics(@NotNull ProcessOutput output,
                                                 @NotNull List<PreviewRenderMessage> messages) {
    addExternalLines(output.getStderr(), messages);
  }

  private static void addExternalLines(String text, List<PreviewRenderMessage> messages) {
    if (text == null || text.isBlank()) {
      return;
    }
    for (String line : text.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String lower = trimmed.toLowerCase();
      if (lower.contains("warn")) {
        addMessage(messages, Severity.WARNING, trimmed);
      } else {
        addMessage(messages, Severity.ERROR, trimmed);
      }
    }
  }

  private static boolean containsNullResourceUriError(@NotNull Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (isNullResourceUriError(current)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNullResourceUriError(@NotNull Throwable throwable) {
    String message = throwable.getMessage();
    if (containsNullResourceUriError(message)) {
      return true;
    }
    for (StackTraceElement element : throwable.getStackTrace()) {
      if ("org.apache.fop.apps.io.InternalResourceResolver".equals(element.getClassName())
          && "getResource".equals(element.getMethodName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsNullResourceUriError(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return text.contains("stringUri")
        && text.contains("null")
        && (text.contains("startsWith") || text.contains("InternalResourceResolver"));
  }

  private static @NotNull String buildExternalErrorMessage(@NotNull ProcessOutput output) {
    String rawError = output.getStderr();
    if (rawError == null || rawError.isBlank()) {
      rawError = output.getStdout();
    }
    if (containsNullResourceUriError(rawError)) {
      return "External FOP preview rendering failed because a referenced resource URI is empty/null. "
          + "Check generated image/resource src/href values and configured base paths.";
    }
    String firstLine = firstNonEmptyLine(rawError);
    if (firstLine == null) {
      return "External FOP preview rendering failed with exit code " + output.getExitCode() + ".";
    }
    return "External FOP preview rendering failed (exit code " + output.getExitCode()
        + "): " + firstLine;
  }

  private static String firstNonEmptyLine(String text) {
    if (text == null) {
      return null;
    }
    for (String line : text.split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
    }
    return null;
  }

  private static @NotNull String extractErrorMessage(@NotNull Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
    }
    return throwable.getClass().getSimpleName();
  }

  private static void addMessage(List<PreviewRenderMessage> messages, Severity severity,
                                 String message) {
    if (message == null || message.isBlank()) {
      return;
    }
    String trimmed = message.trim();
    if (trimmed.startsWith("at ")) {
      return;
    }
    messages.add(new PreviewRenderMessage(severity, trimmed));
  }

  public enum Severity {
    WARNING,
    ERROR
  }

  public record PreviewRenderMessage(@NotNull Severity severity, @NotNull String message) {
  }

  public record PreviewRenderResult(@Nullable File outputFile,
                                    @NotNull List<PreviewRenderMessage> diagnostics,
                                    boolean success) {
  }
}

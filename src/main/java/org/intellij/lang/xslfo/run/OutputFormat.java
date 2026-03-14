package org.intellij.lang.xslfo.run;

import org.apache.fop.apps.MimeConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Supported output formats for FOP execution.
 * Default is PDF.
 */
public enum OutputFormat {
  PDF,
  AFP,
  PCL,
  POSTSCRIPT;

  /**
   * Returns Apache FOP MIME constant for this format.
   *
   * @return the MIME type string for this output format
   */
  public @NotNull String mime() {
    return switch (this) {
      case PDF -> MimeConstants.MIME_PDF;
      case AFP -> MimeConstants.MIME_AFP;
      case PCL -> MimeConstants.MIME_PCL;
      case POSTSCRIPT -> MimeConstants.MIME_POSTSCRIPT;
    };
  }

  /**
   * Returns FOP CLI switch for this format (e.g., -pdf, -afp, -pcl, -ps).
   *
   * @return the CLI switch string for this output format
   */
  public @NotNull String cliSwitch() {
    return switch (this) {
      case PDF -> "-pdf";
      case AFP -> "-afp";
      case PCL -> "-pcl";
      case POSTSCRIPT -> "-ps";
    };
  }

  /**
   * Returns default file extension including dot (e.g., .pdf).
   *
   * @return the file extension including the leading dot
   */
  public @NotNull String extension() {
    return switch (this) {
      case PDF -> ".pdf";
      case AFP -> ".afp";
      case PCL -> ".pcl";
      case POSTSCRIPT -> ".ps";
    };
  }

  /**
   * Parses a string to an OutputFormat enum value.
   *
   * @param name the string to parse
   * @param defaultValue the default value to return if parsing fails
   * @return the parsed OutputFormat, or defaultValue if parsing fails
   */
  public static @NotNull OutputFormat fromString(String name, @NotNull OutputFormat defaultValue) {
    if (name == null) {
      return defaultValue;
    }
    try {
      return OutputFormat.valueOf(name.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return defaultValue;
    }
  }
}

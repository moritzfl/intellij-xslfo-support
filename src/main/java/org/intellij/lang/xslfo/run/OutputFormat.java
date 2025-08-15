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

    /** Returns Apache FOP MIME constant for this format. */
    public @NotNull String mime() {
        return switch (this) {
            case PDF -> MimeConstants.MIME_PDF;
            case AFP -> MimeConstants.MIME_AFP;
            case PCL -> MimeConstants.MIME_PCL;
            case POSTSCRIPT -> MimeConstants.MIME_POSTSCRIPT;
        };
    }

    /** Returns FOP CLI switch for this format (e.g., -pdf, -afp, -pcl, -ps). */
    public @NotNull String cliSwitch() {
        return switch (this) {
            case PDF -> "-pdf";
            case AFP -> "-afp";
            case PCL -> "-pcl";
            case POSTSCRIPT -> "-ps";
        };
    }

    /** Returns default file extension including dot (e.g., .pdf). */
    public @NotNull String extension() {
        return switch (this) {
            case PDF -> ".pdf";
            case AFP -> ".afp";
            case PCL -> ".pcl";
            case POSTSCRIPT -> ".ps";
        };
    }

    public static @NotNull OutputFormat fromString(String name, @NotNull OutputFormat defaultValue) {
        if (name == null) return defaultValue;
        try {
            return OutputFormat.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }
}

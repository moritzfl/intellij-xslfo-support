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
 */
public record XslFoRunSettings(@Nullable VirtualFilePointer xsltFile,
                               @Nullable VirtualFilePointer xmlInputFile,
                               @Nullable String outputFile,
                               boolean openOutputFile,
                               boolean useTemporaryFiles) implements Cloneable {

    public @Nullable VirtualFilePointer getXsltFilePointer() {
        return xsltFile;
    }

    public @Nullable VirtualFilePointer getXmlInputFilePointer() {
        return xmlInputFile;
    }

    public XslFoRunSettings withXsltFile(@Nullable VirtualFilePointer newXslt) {
        return new XslFoRunSettings(newXslt, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles);
    }

    public XslFoRunSettings withXmlInputFile(@Nullable VirtualFilePointer newXml) {
        return new XslFoRunSettings(xsltFile, newXml, outputFile, openOutputFile, useTemporaryFiles);
    }

    public XslFoRunSettings withOutputFile(@Nullable String newOutput) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, newOutput, openOutputFile, useTemporaryFiles);
    }

    public XslFoRunSettings withOpenOutputFile(boolean newOpen) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, newOpen, useTemporaryFiles);
    }

    public XslFoRunSettings withUseTemporaryFiles(boolean newUseTemp) {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, newUseTemp);
    }

    /**
     * Creates a copy of this settings object.
     * <br>
     * Note: VirtualFilePointer references
     * are not duplicated and will be shared in the clone, which is fine for this immutable value object.
     */
    public @NotNull XslFoRunSettings clone() {
        return new XslFoRunSettings(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XslFoRunSettings that)) return false;
        return openOutputFile == that.openOutputFile
            && useTemporaryFiles == that.useTemporaryFiles
            && Objects.equals(xsltFile, that.xsltFile)
            && Objects.equals(xmlInputFile, that.xmlInputFile)
            && Objects.equals(outputFile, that.outputFile);
    }

    @Override
    public String toString() {
        return "XslFoRunSettings{" +
            "xsltFile=" + (xsltFile != null ? xsltFile.getPresentableUrl() : "null") +
            ", xmlInputFile=" + (xmlInputFile != null ? xmlInputFile.getPresentableUrl() : "null") +
            ", outputFile='" + outputFile + '\'' +
            ", openOutputFile=" + openOutputFile +
            ", useTemporaryFiles=" + useTemporaryFiles +
            '}';
    }
}

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
public final class XslFoRunSettings implements Cloneable {

    private final @Nullable VirtualFilePointer xsltFile;
    private final @Nullable VirtualFilePointer xmlInputFile;
    private final @Nullable String outputFile;
    private final boolean openOutputFile;
    private final boolean useTemporaryFiles;

    public XslFoRunSettings(
            @Nullable VirtualFilePointer xsltFile,
            @Nullable VirtualFilePointer xmlInputFile,
            @Nullable String outputFile,
            boolean openOutputFile,
            boolean useTemporaryFiles
    ) {
        this.xsltFile = xsltFile;
        this.xmlInputFile = xmlInputFile;
        this.outputFile = outputFile;
        this.openOutputFile = openOutputFile;
        this.useTemporaryFiles = useTemporaryFiles;
    }

    public @Nullable VirtualFilePointer getXsltFilePointer() {
        return xsltFile;
    }

    public @Nullable VirtualFilePointer getXmlInputFilePointer() {
        return xmlInputFile;
    }

    public @Nullable String getOutputFile() {
        return outputFile;
    }

    public boolean isOpenOutputFile() {
        return openOutputFile;
    }

    public boolean isUseTemporaryFiles() {
        return useTemporaryFiles;
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
    public int hashCode() {
        return Objects.hash(xsltFile, xmlInputFile, outputFile, openOutputFile, useTemporaryFiles);
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

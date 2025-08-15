package org.intellij.lang.xslfo.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.lang.xslfo.run.editor.XslFoRunConfigurationEditor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Dmitry_Cherkas
 */
public abstract class XslFoRunConfiguration extends LocatableConfigurationBase<XslFoRunSettings>
    implements RunConfigurationWithSuppressedDefaultDebugAction, RunProfileWithCompileBeforeLaunchOption {

    private static final String NAME = "XSL-FO Configuration";

    private String mySuggestedName;

    private XslFoRunSettings settings = new XslFoRunSettings(null, null, null, false, false, true, null, SettingsFileMode.PLUGIN, null);

    public XslFoRunConfiguration(Project project, ConfigurationFactory factory) {
        super(project, factory, NAME);
    }

    @Override
    public boolean isExcludeCompileBeforeLaunchOption() {
        return true;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        // Allow running with bundled FOP libraries even if external executable is not configured.
        // Keep basic validation of input selections; detailed checks happen at execution time.
        String xslt = settings.getXsltFilePointer() != null ? settings.getXsltFilePointer().getPresentableUrl() : null;
        if (xslt == null || xslt.isEmpty()) {
            throw new RuntimeConfigurationError("No XSLT file selected");
        }
        String xml = settings.getXmlInputFilePointer() != null ? settings.getXmlInputFilePointer().getPresentableUrl() : null;
        if (xml == null || xml.isEmpty()) {
            throw new RuntimeConfigurationError("No XML input file selected");
        }
    }

    @NotNull
    @Override
    public SettingsEditor<XslFoRunConfiguration> getConfigurationEditor() {
        return new XslFoRunConfigurationEditor(getProject());
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        final VirtualFile baseFile = findXsltFile();
        if (baseFile == null) {
            throw new ExecutionException("No XSLT file selected");
        }
        return createState(environment);
    }

    @NotNull
    protected abstract RunProfileState createState(@NotNull ExecutionEnvironment environment) throws ExecutionException;

    @Override
    public String suggestedName() {
        return mySuggestedName;
    }

    public void setupFromFile(@NotNull XmlFile file) {
        assert XsltSupport.isXsltFile(file) : "Not an XSLT file: " + file.getName();
        mySuggestedName = file.getName();
        setGeneratedName();

        final VirtualFile virtualFile = file.getVirtualFile();
        assert virtualFile != null : "No VirtualFile for " + file.getName();

        setXsltFile(virtualFile);

        final PsiFile[] associations = FileAssociationsManager.getInstance(file.getProject()).getAssociationsFor(file);
        if (associations.length > 0) {
            final VirtualFile assoc = associations[0].getVirtualFile();
            assert assoc != null;
            setXmlInputFile(assoc);
        }
    }

    @Override
    public void readExternal(@NotNull Element element) throws InvalidDataException {
        super.readExternal(element);

        VirtualFilePointer xslt = null;
        VirtualFilePointer xml = null;
        String outPath = null;
        boolean openOut = false;
        boolean useTemp = false;
        boolean useBundledOverride = true;
        String fopDirOverride = null;
        // New settings: config mode and path
        SettingsFileMode configMode = SettingsFileMode.PLUGIN;
        String configFilePath = null;

        Element e = element.getChild("XsltFile");
        if (e != null) {
            final String url = e.getAttributeValue("url");
            if (url != null) {
                xslt = VirtualFilePointerManager.getInstance().create(url, getProject(), null);
            }
        }
        e = element.getChild("XmlFile");
        if (e != null) {
            final String url = e.getAttributeValue("url");
            if (url != null) {
                xml = VirtualFilePointerManager.getInstance().create(url, getProject(), null);
            }
        }

        e = element.getChild("OutputFile");
        if (e != null) {
            outPath = e.getAttributeValue("path");
            openOut = Boolean.parseBoolean(e.getAttributeValue("openOutputFile"));
        }
        String useTempAttr = element.getAttributeValue("useTemporaryFiles");
        if (useTempAttr != null) {
            useTemp = Boolean.parseBoolean(useTempAttr);
        }
        String useBundledOverrideAttr = element.getAttributeValue("useBundledFopOverride");
        if (useBundledOverrideAttr != null) {
            useBundledOverride = Boolean.parseBoolean(useBundledOverrideAttr);
        }
        String fopDirAttr = element.getAttributeValue("fopInstallationDirOverride");
        if (fopDirAttr != null && !fopDirAttr.isEmpty()) {
            fopDirOverride = fopDirAttr;
        }
        // New persistence
        String configModeAttr = element.getAttributeValue("configMode");
        if (configModeAttr != null) {
            try {
                configMode = SettingsFileMode.valueOf(configModeAttr);
            } catch (IllegalArgumentException ignore) {
                configMode = SettingsFileMode.PLUGIN;
            }
        } else {
            // Backward compatibility: infer from legacy attributes
            String useDefaultsAttr = element.getAttributeValue("usePluginDefaultFopSettings");
            boolean useDefaults = useDefaultsAttr == null || Boolean.parseBoolean(useDefaultsAttr);
            String legacyUserConfig = element.getAttributeValue("userConfigLocationOverride");
            if (useDefaults) {
                configMode = SettingsFileMode.PLUGIN;
            } else if (legacyUserConfig != null && !legacyUserConfig.isEmpty()) {
                configMode = SettingsFileMode.FILE;
                configFilePath = legacyUserConfig;
            } else {
                configMode = SettingsFileMode.EMPTY;
            }
        }
        String configPathAttr = element.getAttributeValue("configFilePath");
        if (configPathAttr != null && !configPathAttr.isEmpty()) {
            configFilePath = configPathAttr;
        }

        settings = new XslFoRunSettings(xslt, xml, outPath, openOut, useTemp, useBundledOverride, fopDirOverride, configMode, configFilePath);
    }

    @Override
    public void writeExternal(@NotNull Element element) throws WriteExternalException {
        super.writeExternal(element);

        Element e = new Element("XsltFile");
        if (settings.getXsltFilePointer() != null) {
            e.setAttribute("url", settings.getXsltFilePointer().getUrl());
            element.addContent(e);
        }
        e = new Element("XmlFile");
        if (settings.getXmlInputFilePointer() != null) {
            e.setAttribute("url", settings.getXmlInputFilePointer().getUrl());
            element.addContent(e);
        }
        e = new Element("OutputFile");
        if (settings.outputFile() != null) {
            e.setAttribute("path", settings.outputFile());
            e.setAttribute("openOutputFile", Boolean.toString(settings.openOutputFile()));
            element.addContent(e);
        }
        element.setAttribute("useTemporaryFiles", Boolean.toString(settings.useTemporaryFiles()));
        element.setAttribute("useBundledFopOverride", Boolean.toString(settings.useBundledFopOverride()));
        if (settings.fopInstallationDirOverride() != null) {
            element.setAttribute("fopInstallationDirOverride", settings.fopInstallationDirOverride());
        }
        element.setAttribute("configMode", settings.configMode().name());
        if (settings.configMode() == SettingsFileMode.FILE && settings.configFilePath() != null) {
            element.setAttribute("configFilePath", settings.configFilePath());
        }
    }

    @Override
    public RunConfiguration clone() {
        final XslFoRunConfiguration configuration = (XslFoRunConfiguration) super.clone();
        configuration.settings = this.settings.clone();
        return configuration;
    }

    public void setXsltFile(@NotNull String xsltFile) {
        if (xsltFile.isEmpty()) {
            settings = settings.withXsltFile(null);
        } else {
            VirtualFilePointer ptr = VirtualFilePointerManager.getInstance()
                .create(VfsUtilCore.pathToUrl(xsltFile).replace(File.separatorChar, '/'), getProject(), null);
            settings = settings.withXsltFile(ptr);
        }
    }

    public void setXsltFile(VirtualFile virtualFile) {
        VirtualFilePointer ptr = VirtualFilePointerManager.getInstance().create(virtualFile, getProject(), null);
        settings = settings.withXsltFile(ptr);
    }

    public void setXsltFile(@NotNull VirtualFilePointer pointer) {
        settings = settings.withXsltFile(pointer);
    }

    @Nullable
    public String getXsltFile() {
        return settings.getXsltFilePointer() != null ? settings.getXsltFilePointer().getPresentableUrl() : null;
    }

    @Nullable
    public VirtualFile findXsltFile() {
        return settings.getXsltFilePointer() != null ? settings.getXsltFilePointer().getFile() : null;
    }

    public void setXmlInputFile(@NotNull String xmlInputFile) {
        if (xmlInputFile.isEmpty()) {
            settings = settings.withXmlInputFile(null);
        } else {
            VirtualFilePointer ptr = VirtualFilePointerManager.getInstance()
                .create(VfsUtilCore.pathToUrl(xmlInputFile).replace(File.separatorChar, '/'), getProject(), null);
            settings = settings.withXmlInputFile(ptr);
        }
    }

    public void setXmlInputFile(VirtualFile xmlInputFile) {
        VirtualFilePointer ptr = VirtualFilePointerManager.getInstance().create(xmlInputFile, getProject(), null);
        settings = settings.withXmlInputFile(ptr);
    }

    public void setXmlInputFile(@NotNull VirtualFilePointer pointer) {
        settings = settings.withXmlInputFile(pointer);
    }

    @Nullable
    public String getXmlInputFile() {
        return settings.getXmlInputFilePointer() != null ? settings.getXmlInputFilePointer().getPresentableUrl() : null;
    }

    @Nullable
    public VirtualFile findXmlInputFile() {
        return settings.getXmlInputFilePointer() != null ? settings.getXmlInputFilePointer().getFile() : null;
    }

    public boolean isOpenOutputFile() {
        return settings.openOutputFile();
    }

    public void setOpenOutputFile(boolean openOutputFile) {
        this.settings = this.settings.withOpenOutputFile(openOutputFile);
    }

    public String getOutputFile() {
        return settings.outputFile();
    }

    public void setOutputFile(String outputFile) {
        this.settings = this.settings.withOutputFile(outputFile);
    }

    public void setUseTemporaryFiles(boolean useTemporaryFiles) {
        this.settings = this.settings.withUseTemporaryFiles(useTemporaryFiles);
    }

    public boolean isUseTemporaryFiles() {
        return settings.useTemporaryFiles();
    }

    /**
     * Returns the grouped settings object for this run configuration.
     */
    @NotNull
    public XslFoRunSettings getSettings() {
        return settings;
    }

    /**
     * Replaces this configuration's settings instance.
     */
    public void setSettings(@NotNull XslFoRunSettings settings) {
        this.settings = settings;
    }
}

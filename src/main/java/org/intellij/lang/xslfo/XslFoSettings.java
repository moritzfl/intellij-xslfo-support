package org.intellij.lang.xslfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry_Cherkas
 */
@State(
        name = "XslFoSettings",
        storages = @Storage("other.xml")
)
public class XslFoSettings implements PersistentStateComponent<XslFoSettings> {

    private String myFopInstallationDir;
    private String myUserConfigLocation;
    private boolean myUseBundledFop = true;
    // Default output format for runs that use plugin settings
    private org.intellij.lang.xslfo.run.OutputFormat myDefaultOutputFormat = org.intellij.lang.xslfo.run.OutputFormat.PDF;


    public String getFopInstallationDir() {
        return myFopInstallationDir;
    }

    public void setFopInstallationDir(String fopInstallationDir) {
        this.myFopInstallationDir = fopInstallationDir;
    }

    public String getUserConfigLocation() {
        return myUserConfigLocation;
    }

    public void setUserConfigLocation(String userConfigLocation) {
        this.myUserConfigLocation = userConfigLocation;
    }

    public boolean isUseBundledFop() {
        return myUseBundledFop;
    }

    public void setUseBundledFop(boolean useBundledFop) {
        this.myUseBundledFop = useBundledFop;
    }

    public org.intellij.lang.xslfo.run.OutputFormat getDefaultOutputFormat() {
        return myDefaultOutputFormat == null ? org.intellij.lang.xslfo.run.OutputFormat.PDF : myDefaultOutputFormat;
    }

    public void setDefaultOutputFormat(org.intellij.lang.xslfo.run.OutputFormat format) {
        this.myDefaultOutputFormat = format == null ? org.intellij.lang.xslfo.run.OutputFormat.PDF : format;
    }

    @Nullable
    @Override
    public XslFoSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull XslFoSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static XslFoSettings getInstance() {
        return ApplicationManager.getApplication().getService(XslFoSettings.class);
    }
}

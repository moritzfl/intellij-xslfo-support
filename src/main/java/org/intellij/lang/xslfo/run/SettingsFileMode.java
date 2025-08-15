package org.intellij.lang.xslfo.run;

/**
 * Defines how FOP configuration (user config) is resolved for a run configuration.
 */
public enum SettingsFileMode {
    PLUGIN, // use plugin settings
    EMPTY,  // do not use any settings file
    FILE    // use a specific settings file path stored alongside the mode
}

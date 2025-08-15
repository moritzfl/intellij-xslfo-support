package org.intellij.lang.xslfo.run;

/**
 * Defines how FOP execution is resolved for a run configuration.
 */
public enum ExecutionMode {
    PLUGIN,   // follow plugin settings (bundled vs external and installation dir)
    BUNDLED,  // force bundled FOP
    EXTERNAL  // force external FOP (binary)
}

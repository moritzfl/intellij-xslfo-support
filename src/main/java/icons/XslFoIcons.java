package icons;

import javax.swing.Icon;

import com.intellij.openapi.util.IconLoader;


/**
 * Provides plugin icons for the IntelliJ UI.
 */
public class XslFoIcons {
  private static Icon load() {
    return IconLoader.getIcon("/icons/fop-logo-16x16.png", XslFoIcons.class);
  }

  public static final Icon FopLogo = load();
}

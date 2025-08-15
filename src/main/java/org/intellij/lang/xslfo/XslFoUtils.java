package org.intellij.lang.xslfo;

import com.intellij.execution.Platform;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;

/**
 * @author Dmitry_Cherkas
 */
public class XslFoUtils {

    public static VirtualFile findFopExecutable(String pathToFopInstallationDir) {
        if (pathToFopInstallationDir == null || pathToFopInstallationDir.isEmpty()) {
            return null;
        }
        String url = VfsUtilCore.pathToUrl(pathToFopInstallationDir).replace(File.separatorChar, '/');
        VirtualFile base = VirtualFileManager.getInstance().findFileByUrl(url);
        if (base == null) {
            return null;
        }

        // Determine possible executable names depending on platform
        String[] candidates;
        if (Platform.current() == Platform.WINDOWS) {
            candidates = new String[]{"fop.bat", "fop.cmd"};
        } else {
            candidates = new String[]{"fop"};
        }

        // If the path points directly to the executable (support either extension)
        if (!base.isDirectory()) {
            for (String name : candidates) {
                if (name.equalsIgnoreCase(base.getName())) {
                    return base;
                }
            }
            return null;
        }

        // Try <installDir>/bin/<exe>
        VirtualFile bin = base.findChild("bin");
        if (bin != null) {
            for (String name : candidates) {
                VirtualFile exe = bin.findChild(name);
                if (exe != null) {
                    return exe;
                }
            }
        }

        // Try <installDir>/<exe>
        for (String name : candidates) {
            VirtualFile exe = base.findChild(name);
            if (exe != null) {
                return exe;
            }
        }

        return null;
    }

    public static VirtualFile findFopUserConfig(String userConfigLocation) {
        if(userConfigLocation == null || userConfigLocation.isEmpty()) {
            return null;
        }
        String url = VfsUtilCore.pathToUrl(userConfigLocation).replace(File.separatorChar, '/');
        return VirtualFileManager.getInstance().findFileByUrl(url);
    }
}

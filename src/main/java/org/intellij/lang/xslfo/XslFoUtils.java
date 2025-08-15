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
        String executableName = (Platform.current() == Platform.WINDOWS) ? "fop.bat" : "fop";

        // If the path points directly to the executable (rare, but be tolerant)
        if (!base.isDirectory() && executableName.equalsIgnoreCase(base.getName())) {
            return base;
        }

        // Try <installDir>/bin/<exe>
        if (base.isDirectory()) {
            VirtualFile bin = base.findChild("bin");
            if (bin != null) {
                VirtualFile exe = bin.findChild(executableName);
                if (exe != null) {
                    return exe;
                }
            }
            // Try <installDir>/<exe>
            VirtualFile exe = base.findChild(executableName);
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

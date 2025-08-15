package org.intellij.lang.xslfo.schema;

import com.intellij.javaee.StandardResourceProvider;
import com.intellij.javaee.ResourceRegistrar;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registers any XSD files bundled with the plugin so they are available to the IDE
 * without additional setup by the user.
* <br>
 * The provider will read each XSD, extract its targetNamespace and register
 * it as a standard resource so that references to that namespace can be resolved.
 */
public class BundledSchemasProvider implements StandardResourceProvider {

    private static final String INDEX_PATH = "META-INF/xslfo/schemas/index.txt";
    private static final Pattern TARGET_NS = Pattern.compile("targetNamespace\\s*=\\s*\"([^\"]+)\"");

    @Override
    public void registerResources(@NotNull ResourceRegistrar registrar) {
        ClassLoader cl = BundledSchemasProvider.class.getClassLoader();
        List<String> resources = loadIndex(cl);
        for (String resPath : resources) {
            if (resPath == null || resPath.isBlank()) continue;
            try (InputStream is = cl.getResourceAsStream(resPath)) {
                if (is == null) continue;
                String ns = extractTargetNamespace(is);
                if (ns != null && !ns.isBlank()) {
                    registrar.addStdResource(ns, null, "/" + resPath, BundledSchemasProvider.class);
                }
            } catch (IOException ignored) {
                // Ignore malformed entries; keep plugin robust
            }
        }
    }

    private static @NotNull List<String> loadIndex(ClassLoader cl) {
        List<String> items = new ArrayList<>();
        try (InputStream is = cl.getResourceAsStream(INDEX_PATH)) {
            if (is == null) return items; // no schemas bundled
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    items.add(line);
                }
            }
        } catch (IOException ignored) {
        }
        return items;
    }

    private static String extractTargetNamespace(InputStream is) throws IOException {
        // Read small prefix; targetNamespace is near top usually
        byte[] buf = is.readNBytes(8192);
        String head = new String(buf, StandardCharsets.UTF_8);
        Matcher m = TARGET_NS.matcher(head);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}

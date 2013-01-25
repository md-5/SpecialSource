package net.md_5.specialsource;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lookup inheritance from a class in a given URLClassLoader.
 */
public class URLClassLoaderInheritanceProvider implements IInheritanceProvider {
    private final URLClassLoader classLoader;

    public URLClassLoaderInheritanceProvider(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getParents(String owner) {
        try {
            String ownerInternalName = owner.replace('.', '/').concat(".class");
            URL url = classLoader.findResource(ownerInternalName);
            if (url == null) {
                return null;
            }

            InputStream inputStream = url.openStream();
            if (inputStream == null) {
                return null;
            }

            // TODO: refactor

            ClassReader cr = new ClassReader(inputStream);
            ClassNode node = new ClassNode();
            cr.accept(node, 0);

            List<String> parents = new ArrayList<String>();

            for (String iface : (List<String>) node.interfaces) {
                parents.add(iface);
            }
            parents.add(node.superName);

            return parents;
        } catch (IOException ex) {
            return null;
        }
    }
}

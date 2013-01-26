package net.md_5.specialsource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lookup class inheritance from classes at runtime, remapped through a JarMapping
 */
public class RemappedRuntimeInheritanceProvider extends RuntimeInheritanceProvider {
    private final JarMapping jarMapping;
    private final JarMapping inverseJarMapping;

    public RemappedRuntimeInheritanceProvider(ClassLoader classLoader, boolean verbose, JarMapping jarMapping) {
        super(classLoader, verbose);

        this.jarMapping = jarMapping;
        this.inverseJarMapping = new JarMapping();

        // Invert the mapping
        for (Map.Entry<String, String> entry : jarMapping.classes.entrySet()) {
            inverseJarMapping.classes.put(entry.getValue(), entry.getKey());
        }

        for (Map.Entry<String, String> entry : jarMapping.packages.entrySet()) {
            inverseJarMapping.packages.put(entry.getValue(), entry.getKey());
        }
        // TODO: methods, fields?
    }

    @Override
    public List<String> getParents(String before) {
        // Remap the input (example: cb -> obf)
        // If the type is not mapped, return immediately
        String after = JarRemapper.mapTypeName(before, jarMapping.packages, jarMapping.classes, null);
        if (after == null) {
            if (verbose) {
                System.out.println("RemappedRuntimeInheritanceProvider doesn't know about "+before);
            }
            return null;
        }

        if (verbose) {
            System.out.println("RemappedRuntimeInheritanceProvider getParents "+before+" -> "+after);
        }

        List<String> beforeParents = super.getParents(after);
        if (beforeParents == null) {
            if (verbose) {
                System.out.println("- none");
            }
            return null;
        }

        // Un-remap the output (example: obf -> cb)
        List<String> afterParents = new ArrayList<String>();
        for (String beforeParent : beforeParents) {
            String afterParent = JarRemapper.mapTypeName(beforeParent, inverseJarMapping.packages, inverseJarMapping.classes, beforeParent);
            if (verbose) {
                System.out.println("- " + beforeParent + " -> " + afterParent);
            }

            afterParents.add(afterParent);
        }

        return afterParents;
    }
}

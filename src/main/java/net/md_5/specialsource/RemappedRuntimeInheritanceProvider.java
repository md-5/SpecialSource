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

    public RemappedRuntimeInheritanceProvider(JarMapping jarMapping) {
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
        String after = JarRemapper.mapTypeName(before, jarMapping.packages, jarMapping.classes);

        List<String> beforeParents = super.getParents(after);
        if (beforeParents == null) {
            return null;
        }

        // Un-remap the output (example: obf -> cb)
        List<String> afterParents = new ArrayList<String>();
        for (String beforeParent : beforeParents) {
            afterParents.add(JarRemapper.mapTypeName(beforeParent, inverseJarMapping.packages, inverseJarMapping.classes));
        }

        return afterParents;
    }
}

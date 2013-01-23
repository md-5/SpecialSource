package net.md_5.specialsource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulate a small subset of the maven-shade-plugin class relocation functionality
 */
public class ShadeRelocationSimulator {
    public Map<String, String> relocations = new HashMap<String, String>();

    // No relocations
    public static final ShadeRelocationSimulator IDENTITY = new ShadeRelocationSimulator();

    private ShadeRelocationSimulator() {
    }

    /**
     * Load relocations from map of pattern to shadedPattern
     * @param relocations
     */
    public ShadeRelocationSimulator(Map<String, String> relocations) {
        for (Map.Entry<String, String> entry : relocations.entrySet()) {
            relocations.put(toInternalName(entry.getKey()), toInternalName(entry.getValue()));
        }
    }

    /**
     * Load relocations from list of equals-separated patterns (pattern=shadedPattern)
     * @param list
     */
    public ShadeRelocationSimulator(List<String> list) {
        for (String pair : list) {
            int index = pair.indexOf("=");
            if (index == -1) {
                throw new IllegalArgumentException("ShadeRelocationSimulator invalid relocation string, missing =: "+pair);
            }
            String pattern = pair.substring(0, index);
            String shadedPattern = pair.substring(index + 1);

            relocations.put(toInternalName(pattern), toInternalName(shadedPattern));
        }
    }

    public String shadeClassName(String className) {
        for (Map.Entry<String, String> entry : relocations.entrySet()) {
            String pattern = entry.getKey();
            String shadedPattern = entry.getValue();

            // Match the pattern.. currently, only _exact prefixes_ and replacements are supported
            if (className.startsWith(toInternalName(pattern))) { // TODO: regex support?
                String newClassName = toInternalName(shadedPattern) + className.substring(pattern.length());

                return newClassName;
            }
        }

        return className;
    }

    public String shadeMethodDescriptor(String oldDescriptor) {
        MethodDescriptorTransformer methodDescriptorTransformer = new MethodDescriptorTransformer(relocations, null);
        return methodDescriptorTransformer.transform(oldDescriptor);
    }

    public static String toInternalName(String className) {
        return className.replace('.', '/');
    }
}

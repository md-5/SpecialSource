package net.md_5.specialsource;

import com.google.common.base.Joiner;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class InheritanceMap {

    private final Map<String, ArrayList<String>> inheritanceMap = new HashMap<String, ArrayList<String>>();

    /**
     * Generate an inheritance map for the given classes
     */
    public void generate(List<IInheritanceProvider> inheritanceProviders, Collection<String> classes) {
        for (String className : classes) {
            ArrayList<String> parents = getParents(inheritanceProviders, className);

            if (parents == null) {
                System.out.println("No inheritance information found for "+className);
            } else {
                ArrayList<String> filteredParents = new ArrayList<String>();

                // Include only classes requested
                for (String parent: parents) {
                    if (classes.contains(parent)) {
                        filteredParents.add(parent);
                    }
                }

                // If there are parents we care about, add to map
                if (filteredParents.size() > 0) {
                    inheritanceMap.put(className, filteredParents);
                }
            }
        }
    }

    private ArrayList<String> getParents(List<IInheritanceProvider> inheritanceProviders, String className) {
        for (IInheritanceProvider inheritanceProvider : inheritanceProviders) {
            // // ask each provider for inheritance information on the class, until one responds
            // TODO: refactor with JarRemapper tryClimb?
            List<String> parents = inheritanceProvider.getParents(className);

            if (parents != null) {
                return (ArrayList<String>) parents;
            }
        }

        return null;
    }

    public void save(PrintWriter writer) {
        List<String> classes = new ArrayList<String>(inheritanceMap.keySet());
        Collections.sort(classes);

        for (String className : classes) {
            writer.print(className);
            writer.print(' ');

            List<String> parents = inheritanceMap.get(className);
            writer.println(Joiner.on(' ').join(parents));
        }
    }
}

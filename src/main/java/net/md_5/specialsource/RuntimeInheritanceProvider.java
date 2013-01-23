package net.md_5.specialsource;

import java.util.List;
import java.util.ArrayList;

/**
 * Lookup class inheritance from classes loaded at runtime
 */
public class RuntimeInheritanceProvider implements IInheritanceProvider {
    // TODO: option to transform through a jarRemapper at runtime

    public List<String> getParents(String internalClassName) {
        List<String> parents = new ArrayList<String>();
        String sourceClassName = toSourceName(internalClassName);
        Class clazz;
        try {
            clazz = ClassLoader.getSystemClassLoader().loadClass(sourceClassName); // load class without initializing
            //clazz = Class.forName(toSourceName(sourceClassName)); // runs static initializers - avoid!
        } catch (Throwable t) {
            System.out.println("RuntimeInheritanceProvider failed: "+t);
            return parents;
        }

        for (Class iface : clazz.getInterfaces()) {
            parents.add(toInternalName(iface.getName()));
        }

        Class superClass = clazz.getSuperclass();
        if (superClass != null) {
            parents.add(toInternalName(superClass.getName()));
        }

        return parents;
    }

    // Convert class name from internal name to source name
    public String toSourceName(String className) {
        return className.replace('/', '.');
    }

    //  .. and vice versa
    public String toInternalName(String className) {
        return className.replace('.', '/');
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }
}

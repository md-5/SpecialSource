package net.md_5.specialsource;

import java.util.List;

public interface IInheritanceProvider {
    /**
     * Get the superclass and implemented interfaces of a class
     * @param className
     * @return
     */
    List<String> getParents(String className);
}

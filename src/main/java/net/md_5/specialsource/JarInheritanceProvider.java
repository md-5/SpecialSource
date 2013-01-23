package net.md_5.specialsource;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Lookup inheritance from a class given a jar
 */
public class JarInheritanceProvider implements IInheritanceProvider {
    private final Jar self;

    public JarInheritanceProvider(Jar self) {
        this.self = self;
    }

    @SuppressWarnings("unchecked") // Saddens me to see ASM strip vital info like that
    public List<String> getParents(String owner) {
        System.out.println("jar: owner "+owner);
        List<String> parents = new ArrayList<String>();
        ClassNode node = self.getNode(owner);
        if (node != null) {
            for (String iface : (List<String>) node.interfaces) {
                System.out.println("jar: add iface="+iface);
                parents.add(iface);
            }
            System.out.println("jar: add super="+node.superName);
            parents.add(node.superName);
        } else {
            System.out.println("jar: nothing for "+owner);
        }
        return parents;
    }

    public String toString() {
        return getClass().getSimpleName()+"("+self.file.getName()+")";
    }
}

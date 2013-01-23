package net.md_5.specialsource;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;

public class JarInheritanceProvider implements IInheritanceProvider {
    private final Jar self;

    public JarInheritanceProvider(Jar self) {
        this.self = self;
    }

    @SuppressWarnings("unchecked") // Saddens me to see ASM strip vital info like that
    public List<String> getParents(String owner) {
        List<String> parents = new ArrayList<String>();
        ClassNode node = self.getNode(owner);
        if (node != null) {
            for (String iface : (List<String>) node.interfaces) {
                parents.add(iface);
            }
            parents.add(node.superName);
        }
        return parents;
    }
}

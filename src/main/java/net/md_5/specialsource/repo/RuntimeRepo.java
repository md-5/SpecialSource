package net.md_5.specialsource.repo;

import java.io.IOException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeRepo extends CachingRepo {

    @Getter
    private static final RuntimeRepo instance = new RuntimeRepo();

    @Override
    protected ClassNode findClass0(String internalName) {
        ClassReader cr;
        try {
            cr = new ClassReader(internalName);
        } catch (IOException ex) {
            return null;
        }
        ClassNode node = new ClassNode();
        cr.accept(node, 0);
        return node;
    }
}

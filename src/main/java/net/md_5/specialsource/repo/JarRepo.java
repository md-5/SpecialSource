package net.md_5.specialsource.repo;

import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import net.md_5.specialsource.Jar;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

@RequiredArgsConstructor
public class JarRepo extends CachingRepo {

    private final Jar jar;

    @Override
    protected ClassNode findClass0(String internalName) {
        ClassNode node = null;

        try {
            InputStream is = jar.getClass(internalName);

            if (is != null) {
                ClassReader reader = new ClassReader(is);
                ClassNode node0 = new ClassNode();
                reader.accept(node0, 0); // TODO
                is.close();

                node = node0;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return node;
    }
}

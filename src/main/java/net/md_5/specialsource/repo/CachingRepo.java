package net.md_5.specialsource.repo;

import java.util.Map;
import java.util.WeakHashMap;
import org.objectweb.asm.tree.ClassNode;

public abstract class CachingRepo implements ClassRepo {

    private final Map<String, ClassNode> cache = new WeakHashMap<String, ClassNode>();

    @Override
    public final ClassNode findClass(String internalName) {
        ClassNode fromCache = cache.get(internalName);
        if (fromCache != null) {
            return fromCache;
        }

        ClassNode found = findClass0(internalName);
        if (found != null) {
            cache.put(internalName, found);
            return found;
        }

        return null;
    }

    protected abstract ClassNode findClass0(String internalName);
}

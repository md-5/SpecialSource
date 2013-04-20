/**
 * Copyright (c) 2012-2013, md_5. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.md_5.specialsource.provider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Lookup inheritance from a class in a given URLClassLoader.
 */
@RequiredArgsConstructor
public class ClassLoaderProvider implements InheritanceProvider {

    private final ClassLoader classLoader;

    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> getParents(String owner) {
        // TODO: ToInternalName
        String ownerInternalName = owner.replace('.', '/').concat(".class");
        InputStream input = classLoader.getResourceAsStream(ownerInternalName);
        if (input == null) {
            return null;
        }

        try {
            ClassReader cr = new ClassReader(input);
            ClassNode node = new ClassNode();
            cr.accept(node, 0);

            Collection<String> parents = new HashSet<String>();
            for (String iface : (List<String>) node.interfaces) {
                parents.add(iface);
            }
            parents.add(node.superName);

            return parents;
        } catch (IOException ex) {
            // Just ignore this, means that we couldn't get any lookup for the specified class
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    // Too bad, can't recover from here
                }
            }
        }

        return null;
    }
}

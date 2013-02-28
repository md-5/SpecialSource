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
package net.md_5.specialsource;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lookup inheritance from a class in a given URLClassLoader.
 */
public class URLClassLoaderInheritanceProvider implements IInheritanceProvider {
    private final URLClassLoader classLoader;
    private final boolean verbose;

    public URLClassLoaderInheritanceProvider(URLClassLoader classLoader, boolean verbose) {
        this.classLoader = classLoader;
        this.verbose = verbose;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getParents(String owner) {
        try {
            String ownerInternalName = owner.replace('.', '/').concat(".class");
            if (verbose) {
                System.out.println("URLClassLoaderInheritanceProvider looking up "+ownerInternalName);
            }
            URL url = classLoader.findResource(ownerInternalName);
            if (url == null) {
                return null;
            }

            InputStream inputStream = url.openStream();
            if (inputStream == null) {
                return null;
            }

            ClassReader cr = new ClassReader(inputStream);
            ClassNode node = new ClassNode();
            cr.accept(node, 0);

            List<String> parents = new ArrayList<String>();

            for (String iface : (List<String>) node.interfaces) {
                parents.add(iface);
                if (verbose) {
                    System.out.println(" - "+iface);
                }
            }
            parents.add(node.superName);
            if (verbose) {
                System.out.println(" + "+node.superName);
            }

            return parents;
        } catch (IOException ex) {
            if (verbose) {
                System.out.println("URLClassLoaderInheritanceProvider "+owner+" exception: "+ex);
                ex.printStackTrace();
            }
            return null;
        }
    }
}

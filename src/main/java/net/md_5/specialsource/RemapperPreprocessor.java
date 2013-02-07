/**
 * Copyright (c) 2012, md_5. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

/**
 * "Pre-process" a class file, intended to be used before remapping with JarRemapper.
 *
 * Currently includes:
 * - Extracting inheritance
 */

public class RemapperPreprocessor {

    public boolean debug = false;

    private InheritanceMap inheritanceMap;

    /**
     *
     * @param inheritanceMap Map to add extracted inheritance information too, or null to not extract inheritance
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public RemapperPreprocessor(InheritanceMap inheritanceMap) {
        this.inheritanceMap = inheritanceMap;
    }

    public void preprocess(String className, InputStream inputStream) throws IOException {
        ClassReader classReader = new ClassReader(inputStream);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); // TODO

        if (inheritanceMap != null) {
            log("Loading plugin class inheritance for "+className);

            // Get inheritance
            ArrayList<String> parents = new ArrayList<String>();

            for (String iface : (List<String>) classNode.interfaces) {
                parents.add(iface);
            }
            parents.add(classNode.superName);

            inheritanceMap.inheritanceMap.put(className.replace('.', '/'), parents);

            log("Inheritance added "+className+" parents "+parents.size());
        }
        // TODO: ReflectionRemapper
    }

    private void log(String message) {
        if (debug) {
            System.out.println("[RemapperPreprocessor] " + message);
        }
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lookup class inheritance from classes at runtime, remapped through a JarMapping
 */
public class RemappedRuntimeInheritanceProvider extends RuntimeInheritanceProvider {
    private final JarMapping jarMapping;
    private final JarMapping inverseJarMapping;

    public RemappedRuntimeInheritanceProvider(ClassLoader classLoader, boolean verbose, JarMapping jarMapping) {
        super(classLoader, verbose);

        this.jarMapping = jarMapping;
        this.inverseJarMapping = new JarMapping();

        // Invert the mapping
        for (Map.Entry<String, String> entry : jarMapping.classes.entrySet()) {
            inverseJarMapping.classes.put(entry.getValue(), entry.getKey());
        }

        for (Map.Entry<String, String> entry : jarMapping.packages.entrySet()) {
            inverseJarMapping.packages.put(entry.getValue(), entry.getKey());
        }
        // TODO: methods, fields?
    }

    @Override
    public List<String> getParents(String before) {
        // Remap the input (example: cb -> obf)
        // If the type is not mapped, return immediately
        String after = JarRemapper.mapTypeName(before, jarMapping.packages, jarMapping.classes, null);
        if (after == null) {
            if (verbose) {
                System.out.println("RemappedRuntimeInheritanceProvider doesn't know about "+before);
            }
            return null;
        }

        if (verbose) {
            System.out.println("RemappedRuntimeInheritanceProvider getParents "+before+" -> "+after);
        }

        List<String> beforeParents = super.getParents(after);
        if (beforeParents == null) {
            if (verbose) {
                System.out.println("- none");
            }
            return null;
        }

        // Un-remap the output (example: obf -> cb)
        List<String> afterParents = new ArrayList<String>();
        for (String beforeParent : beforeParents) {
            String afterParent = JarRemapper.mapTypeName(beforeParent, inverseJarMapping.packages, inverseJarMapping.classes, beforeParent);
            if (verbose) {
                System.out.println("- " + beforeParent + " -> " + afterParent);
            }

            afterParents.add(afterParent);
        }

        return afterParents;
    }
}

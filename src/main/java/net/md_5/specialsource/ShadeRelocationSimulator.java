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

import java.util.*;

/**
 * Simulate a small subset of the maven-shade-plugin class relocation
 * functionality
 */
public class ShadeRelocationSimulator extends JarMappingLoadTransformer {

    public LinkedHashMap<String, String> relocations = new LinkedHashMap<String, String>();
    // No relocations
    public static final ShadeRelocationSimulator IDENTITY = new ShadeRelocationSimulator();

    private ShadeRelocationSimulator() {
    }

    /**
     * Load relocations from map of pattern to shadedPattern
     *
     * @param relocations
     */
    public ShadeRelocationSimulator(Map<String, String> relocations) {
        for (Map.Entry<String, String> entry : relocations.entrySet()) {
            this.relocations.put(toInternalName(entry.getKey()), toInternalName(entry.getValue()));
        }
    }

    /**
     * Load relocations from list of equals-separated patterns
     * (pattern=shadedPattern)
     *
     * @param list
     */
    public ShadeRelocationSimulator(List<String> list) {
        for (String pair : list) {
            int index = pair.indexOf("=");
            if (index == -1) {
                throw new IllegalArgumentException("ShadeRelocationSimulator invalid relocation string, missing =: " + pair);
            }
            String pattern = pair.substring(0, index);
            String shadedPattern = pair.substring(index + 1);

            relocations.put(toInternalName(pattern), toInternalName(shadedPattern));
        }
    }

    public ShadeRelocationSimulator(String string) {
        this(Arrays.asList(string.split(",")));
    }

    @Override
    public String transformClassName(String className) {
        for (Map.Entry<String, String> entry : relocations.entrySet()) {
            String pattern = entry.getKey();
            String shadedPattern = entry.getValue();

            // Match the pattern.. currently, only _exact prefixes_ and replacements are supported
            if (className.startsWith(toInternalName(pattern))) { // TODO: regex support?
                String newClassName = toInternalName(shadedPattern) + className.substring(pattern.length());

                return newClassName;
            }
        }

        return className;
    }

    @Override
    public String transformMethodDescriptor(String oldDescriptor) {
        MethodDescriptorTransformer methodDescriptorTransformer = new MethodDescriptorTransformer(relocations, null);
        return methodDescriptorTransformer.transform(oldDescriptor);
    }

    public static String toInternalName(String className) {
        return className.replace('.', '/');
    }
}

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

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class InheritanceMap implements IInheritanceProvider {

    private final Map<String, ArrayList<String>> inheritanceMap = new HashMap<String, ArrayList<String>>();

    public static final InheritanceMap EMPTY = new InheritanceMap();

    /**
     * Generate an inheritance map for the given classes
     */
    public void generate(IInheritanceProvider inheritanceProvider, Collection<String> classes) {
        for (String className : classes) {
            List<String> parents = inheritanceProvider.getParents(className);

            if (parents == null) {
                System.out.println("No inheritance information found for "+className);
            } else {
                ArrayList<String> filteredParents = new ArrayList<String>();

                // Include only classes requested
                for (String parent: parents) {
                    if (classes.contains(parent)) {
                        filteredParents.add(parent);
                    }
                }

                // If there are parents we care about, add to map
                if (filteredParents.size() > 0) {
                    setParents(className, filteredParents);
                }
            }
        }
    }

    public void save(PrintWriter writer) {
        List<String> classes = new ArrayList<String>(inheritanceMap.keySet());
        Collections.sort(classes);

        for (String className : classes) {
            writer.print(className);
            writer.print(' ');

            List<String> parents = getParents(className);
            writer.println(Joiner.on(' ').join(parents));
        }
    }

    public void load(BufferedReader reader, BiMap<String, String> classMap) throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            String[] tokens = line.split(" ");

            if (tokens.length < 2) {
                throw new IOException("Invalid inheritance map file line: " + line);
            }

            String className = tokens[0];
            List<String> parents = Arrays.asList(tokens).subList(1, tokens.length);

            if (classMap == null) {
                setParents(className, new ArrayList<String>(parents));
            } else {
                String remappedClassName = JarRemapper.mapTypeName(className, /*packageMap*/null, classMap, /*defaultIfUnmapped*/null);
                if (remappedClassName == null) {
                    throw new IOException("Inheritance map input class not remapped: " + className);
                }

                ArrayList<String> remappedParents = new ArrayList<String>();
                for (String parent : parents) {
                    String remappedParent = JarRemapper.mapTypeName(parent, /*packageMap*/null, classMap, /*defaultIfUnmapped*/null);
                    if (remappedParent == null) {
                        throw new IOException("Inheritance map parent class not remapped: " + parent);
                    }

                    remappedParents.add(remappedParent);
                }

                setParents(remappedClassName, remappedParents);
            }
        }
    }

    public boolean hasParents(String className) {
        return inheritanceMap.containsKey(className);
    }

    public List<String> getParents(String className) {
        return inheritanceMap.get(className);
    }

    public void setParents(String className, ArrayList<String> parents) {
        inheritanceMap.put(className, parents);
    }

    public int size() {
        return inheritanceMap.size();
    }
}

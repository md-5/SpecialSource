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

import com.google.common.base.Joiner;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class InheritanceMap {

    private final Map<String, ArrayList<String>> inheritanceMap = new HashMap<String, ArrayList<String>>();

    /**
     * Generate an inheritance map for the given classes
     */
    public void generate(List<IInheritanceProvider> inheritanceProviders, Collection<String> classes) {
        for (String className : classes) {
            ArrayList<String> parents = getParents(inheritanceProviders, className);

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
                    inheritanceMap.put(className, filteredParents);
                }
            }
        }
    }

    private ArrayList<String> getParents(List<IInheritanceProvider> inheritanceProviders, String className) {
        for (IInheritanceProvider inheritanceProvider : inheritanceProviders) {
            // // ask each provider for inheritance information on the class, until one responds
            // TODO: refactor with JarRemapper tryClimb?
            List<String> parents = inheritanceProvider.getParents(className);

            if (parents != null) {
                return (ArrayList<String>) parents;
            }
        }

        return null;
    }

    public void save(PrintWriter writer) {
        List<String> classes = new ArrayList<String>(inheritanceMap.keySet());
        Collections.sort(classes);

        for (String className : classes) {
            writer.print(className);
            writer.print(' ');

            List<String> parents = inheritanceMap.get(className);
            writer.println(Joiner.on(' ').join(parents));
        }
    }
}

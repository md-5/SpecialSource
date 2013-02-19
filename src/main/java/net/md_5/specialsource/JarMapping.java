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

import java.io.*;
import java.util.*;

public class JarMapping {

    public final LinkedHashMap<String, String> packages = new LinkedHashMap<String, String>();
    public final Map<String, String> classes = new HashMap<String, String>();
    public final Map<String, String> fields = new HashMap<String, String>();
    public final Map<String, String> methods = new HashMap<String, String>();

    private InheritanceMap inheritanceMap = new InheritanceMap();
    private IInheritanceProvider fallbackInheritanceProvider = null;

    public JarMapping() {
    }

    public JarMapping(BufferedReader reader, ShadeRelocationSimulator shader) throws IOException {
        loadMappings(reader, shader);
    }

    /**
     * Set the inheritance map used for caching superclass/interfaces. This call be omitted to
     * use a local cache, or set to your own global cache.
     */
    public void setInheritanceMap(InheritanceMap inheritanceMap) {
        this.inheritanceMap = inheritanceMap;
    }

    /**
     * Set the inheritance provider to be consulted if the inheritance map has no information on
     * the requested class (results will be cached in the inheritance map).
     */
    public void setFallbackInheritanceProvider(IInheritanceProvider fallbackInheritanceProvider) {
        this.fallbackInheritanceProvider = fallbackInheritanceProvider;
    }

    public String tryClimb(Map<String, String> map, NodeType type, String owner, String name) {
        String key = owner + "/" + name;

        String mapped = map.get(key);
        if (mapped == null) {
            List<String> parents = null;

            if (inheritanceMap.hasParents(owner)) {
                parents = inheritanceMap.getParents(owner);
            } else if (fallbackInheritanceProvider != null) {
                parents = fallbackInheritanceProvider.getParents(owner);
                inheritanceMap.setParents(owner, (ArrayList<String>) parents);
            }

            if (parents != null) {
                // climb the inheritance tree
                for (String parent : parents) {
                    mapped = tryClimb(map, type, parent, name);
                    if (mapped != null) {
                        return mapped;
                    }
                }
            }
        }
        return mapped;
    }

    /**
     * Load a mapping given a .csrg file
     *
     * @param reader Mapping file reader
     * @param shader Relocation to apply to old class names, or null for no
     * relocation
     * @throws IOException
     */
    public void loadMappings(BufferedReader reader, ShadeRelocationSimulator shader) throws IOException {
        if (shader == null) {
            shader = ShadeRelocationSimulator.IDENTITY;
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty()){
                continue;
            }
            // TODO: refactor ShadeRelocationSimulator application

            if (line.contains(":")) {
                // standard srg
                parseSrgLine(line, shader);
            } else {
                // better 'compact' srg format
                parseCsrgLine(line, shader);
            }
        }
    }

    /**
     * Parse a 'csrg' mapping format line and populate the data structures
     */
    private void parseCsrgLine(String line, ShadeRelocationSimulator shader) throws IOException {
        String[] tokens = line.split(" ");

        if (tokens.length == 2) {
            String oldClassName = shader.shadeClassName(tokens[0]);
            String newClassName = tokens[1];

            if (oldClassName.endsWith("/")) {
                // Special case: mapping an entire hierarchy of classes
                packages.put(oldClassName.substring(0, oldClassName.length() - 1), newClassName);
            } else {
                classes.put(oldClassName, newClassName);
            }
        } else if (tokens.length == 3) {
            String oldClassName = shader.shadeClassName(tokens[0]);
            String oldFieldName = tokens[1];
            String newFieldName = tokens[2];
            fields.put(oldClassName + "/" + oldFieldName, newFieldName);
        } else if (tokens.length == 4) {
            String oldClassName = shader.shadeClassName(tokens[0]);
            String oldMethodName = tokens[1];
            String oldMethodDescriptor = shader.shadeMethodDescriptor(tokens[2]);
            String newMethodName = tokens[3];
            methods.put(oldClassName + "/" + oldMethodName + " " + oldMethodDescriptor, newMethodName);
        } else {
            throw new IOException("Invalid csrg file line, token count " + tokens.length + " unexpected in "+line);
        }
    }

    /**
     * Parse a standard 'srg' mapping format line and populate the data structures
     */
    private void parseSrgLine(String line, ShadeRelocationSimulator shader) throws IOException {
        String[] tokens = line.split(" ");
        String kind = tokens[0];

        if (kind.equals("CL:")) {
            String oldClassName = shader.shadeClassName(tokens[1]); // TODO: refactor
            String newClassName = tokens[2];

            if (classes.containsKey(oldClassName)) {
                throw new IllegalArgumentException("Duplicate class mapping: " + oldClassName + " -> " + newClassName +
                    " but already mapped to "+classes.get(oldClassName)+" in line="+line);
            }

            classes.put(oldClassName, newClassName);
        } else if (kind.equals("PK:")) {
            String oldPackageName = tokens[1];
            String newPackageName = tokens[2];

            if (packages.containsKey(oldPackageName)) {
                throw new IllegalArgumentException("Duplicate package mapping: " + oldPackageName + " ->" + newPackageName +
                    " but already mapped to "+packages.get(oldPackageName)+" in line="+line);
            }

            packages.put(oldPackageName, newPackageName);
        } else if (kind.equals("FD:")) {
            String oldFull = tokens[1];
            String newFull = tokens[2];

            // Split the qualified field names into their classes and actual names
            int splitOld = oldFull.lastIndexOf('/');
            int splitNew = newFull.lastIndexOf('/');
            if (splitOld == -1 || splitNew == -1) {
                throw new IllegalArgumentException("Field name is invalid, not fully-qualified: " + oldFull +
                        " -> " + newFull + " in line="+line);
            }

            String oldClassName = oldFull.substring(0, splitOld);
            String oldFieldName = oldFull.substring(splitOld + 1);
            String newClassName = newFull.substring(0, splitNew);
            String newFieldName = newFull.substring(splitNew + 1);

            fields.put(oldClassName + "/" + oldFieldName, newFieldName);
        } else if (kind.equals("MD:")) {
            String oldFull = tokens[1];
            String oldMethodDescriptor = tokens[2];
            String newFull = tokens[3];
            String newMethodDescriptor = tokens[4];

            // Split the qualified field names into their classes and actual names TODO: refactor with above
            int splitOld = oldFull.lastIndexOf('/');
            int splitNew = newFull.lastIndexOf('/');
            if (splitOld == -1 || splitNew == -1) {
                throw new IllegalArgumentException("Field name is invalid, not fully-qualified: " + oldFull +
                        " -> " + newFull + " in line="+line);
            }

            String oldClassName = oldFull.substring(0, splitOld);
            String oldMethodName = oldFull.substring(splitOld + 1);
            String newClassName = newFull.substring(0, splitNew);
            String newMethodName = newFull.substring(splitNew + 1);

            // TODO: validate newMethodDescriptor instead of completely ignoring it

            methods.put(oldClassName + "/" + oldMethodName + " " + oldMethodDescriptor, newMethodName);
        } else {
            throw new IllegalArgumentException("Unable to parse srg file, unrecognized mapping type in line="+line);
        }
    }

    public JarMapping(JarComparer oldJar, JarComparer newJar, File logFile, boolean compact) throws IOException {
        this(oldJar, newJar, logFile, compact, false);
    }

    /**
     * Generate a mapping given an original jar and renamed jar
     *
     * @param oldJar Original jar
     * @param newJar Renamed jar
     * @param logfile Optional .srg file to output mappings to
     * @param compact If true, generate .csrg logfile instead of .srg
     * @param full if true, generate duplicates
     * @throws IOException
     */
    public JarMapping(JarComparer oldJar, JarComparer newJar, File logfile, boolean compact, boolean full) throws IOException {
        SpecialSource.validate(oldJar, newJar);

        PrintWriter out;
        if (logfile == null) {
            out = new PrintWriter(System.out);
        } else {
            out = new PrintWriter(logfile);
        }

        ISrgWriter srgWriter;
        if (compact) {
            srgWriter = new CompactSrgWriter(out);
        } else {
            srgWriter = new SrgWriter(out, oldJar.jar.file.getName(), newJar.jar.file.getName());
        }

        for (int i = 0; i < oldJar.classes.size(); i++) {
            String oldClass = oldJar.classes.get(i);
            String newClass = newJar.classes.get(i);
            classes.put(oldClass, newClass); // always output class names (no duplicate check)
            srgWriter.addClassMap(oldClass, newClass);
        }
        for (int i = 0; i < oldJar.fields.size(); i++) {
            Ownable oldField = oldJar.fields.get(i);
            Ownable newField = newJar.fields.get(i);
            String key = oldField.owner + "/" + oldField.name;
            fields.put(key, newField.name);

            if (full || !oldField.name.equals(newField.name)) {
                srgWriter.addFieldMap(oldField, newField);
            }
        }
        for (int i = 0; i < oldJar.methods.size(); i++) {
            Ownable oldMethod = oldJar.methods.get(i);
            Ownable newMethod = newJar.methods.get(i);
            String key = oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor;
            methods.put(key, newMethod.name);

            MethodDescriptorTransformer methodDescriptorTransformer = new MethodDescriptorTransformer(null, classes);
            String oldDescriptor = methodDescriptorTransformer.transform(oldMethod.descriptor);

            if (full || !(oldMethod.name + " " + oldDescriptor).equals(newMethod.name + " " + newMethod.descriptor)) {
                srgWriter.addMethodMap(oldMethod, newMethod);
            }
        }

        srgWriter.write();
    }
}

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;

public class JarRemapper extends Remapper {

    private static final int CLASS_LEN = ".class".length();
    private final List<IInheritanceProvider> inheritanceProviders;
    private final JarMapping jarMapping;

    public JarRemapper(JarMapping jarMapping, List<IInheritanceProvider> inheritanceProviders) throws IOException {
        this.jarMapping = jarMapping;
        this.inheritanceProviders = inheritanceProviders;
    }

    @Override
    public String map(String typeName) {
        return mapTypeName(typeName, jarMapping.packages, jarMapping.classes);
    }

    public static String mapTypeName(String typeName, Map<String, String> packageMap, Map<String, String> classMap) {
        int index = typeName.indexOf('$');
        String key = (index == -1) ? typeName : typeName.substring(0, index);
        String mapped = mapClassName(typeName, packageMap, classMap);

        return mapped != null ? mapped + (index == -1 ? "" : typeName.substring(index, typeName.length())) : typeName;
    }

    /**
     * Helper method to map a class name by package (prefix) or class (exact)
     * map
     */
    private static String mapClassName(String className, Map<String, String> packageMap, Map<String, String> classMap) {
        if (packageMap != null) {
            for (String oldPackage : packageMap.keySet()) {
                if (className.startsWith(oldPackage)) {
                    String newPackage = packageMap.get(oldPackage);

                    return newPackage + className.substring(oldPackage.length());
                }
            }
        }

        return classMap != null ? classMap.get(className) : null;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String mapped = tryClimb(jarMapping.fields, NodeType.FIELD, owner, name);
        return mapped == null ? name : mapped;
    }

    private String tryClimb(Map<String, String> map, NodeType type, String owner, String name) {
        String key = owner + "/" + name;

        String mapped = map.get(key);
        if (mapped == null) {
            // ask each provider for inheritance information on the class, until one responds
            for (IInheritanceProvider inheritanceProvider : inheritanceProviders) {
                List<String> parents = inheritanceProvider.getParents(owner);

                if (parents != null) {
                    // climb the inheritance tree
                    for (String parent : parents) {
                        mapped = tryClimb(map, type, parent, name);
                        if (mapped != null) {
                            return mapped;
                        }
                    }
                    break;
                }
            }
        }
        return mapped;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        String mapped = tryClimb(jarMapping.methods, NodeType.METHOD, owner, name + " " + desc);
        return mapped == null ? name : mapped;
    }

    /**
     * Remap all the classes in a jar, writing a new jar to the target
     */
    public void remapJar(Jar jar, File target) throws IOException {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(target));
        try {
            if (jar == null) {
                return;
            }
            for (Enumeration<JarEntry> entr = jar.file.entries(); entr.hasMoreElements();) {
                JarEntry entry = entr.nextElement();

                InputStream is = jar.file.getInputStream(entry);
                try {
                    String name = entry.getName();
                    byte[] data;
                    if (name.endsWith(".class")) {
                        name = name.substring(0, name.length() - CLASS_LEN);

                        data = remapClassFile(is);
                        String newName = map(name);

                        entry = new JarEntry(newName == null ? name : newName + ".class");

                    } else {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        int n;
                        byte[] b = new byte[1 << 15]; // Max class file size
                        while ((n = is.read(b, 0, b.length)) != -1) {
                            buffer.write(b, 0, n);
                        }
                        buffer.flush();
                        data = buffer.toByteArray();
                    }
                    entry.setTime(0);
                    out.putNextEntry(entry);
                    out.write(data);
                } finally {
                    is.close();
                }
            }
        } finally {
            out.close();
        }
    }

    /**
     * Remap an individual class given an InputStream to its bytecode
     */
    public byte[] remapClassFile(InputStream is) throws IOException {
        ClassReader reader = new ClassReader(is);
        ClassWriter wr = new ClassWriter(0);
        RemappingClassAdapter mapper = new RemappingClassAdapter(wr, this);
        reader.accept(mapper, ClassReader.EXPAND_FRAMES);
        return wr.toByteArray();
    }
}

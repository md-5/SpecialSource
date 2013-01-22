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
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.ClassNode;

public class JarRemapper extends Remapper {

    private static final int CLASS_LEN = ".class".length();
    private final Jar self;
    private final JarMapping jarMapping;

    private JarRemapper(JarMapping jarMapping, Jar self) throws IOException {
        this.jarMapping = jarMapping;
        this.self = self;
    }

    @Override
    public String map(String typeName) {
        int index = typeName.indexOf('$');
        String key = (index == -1) ? typeName : typeName.substring(0, index);
        String mapped = null;
        for (String oldPackage : jarMapping.packages.keySet()) {
            if (key.startsWith(oldPackage)) {
                String newPackage = jarMapping.packages.get(oldPackage);
                mapped = newPackage + key.substring(oldPackage.length());
                break;
            }
        }
        if (mapped == null) {
            mapped = jarMapping.classes.get(key);
        }

        return mapped != null ? mapped + (index == -1 ? "" : typeName.substring(index, typeName.length())) : typeName;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String mapped = tryClimb(jarMapping.fields, NodeType.FIELD, owner, name);
        return mapped == null ? name : mapped;
    }

    @SuppressWarnings("unchecked") // Saddens me to see ASM strip vital info like that
    private String tryClimb(Map<String, String> map, NodeType type, String owner, String name) {
        String key = owner + "/" + name;

        String mapped = map.get(key);
        if (mapped == null) {
            ClassNode node = self.getNode(owner);
            if (node != null) {
                for (String iface : (List<String>) node.interfaces) {
                    mapped = tryClimb(map, type, iface, name);
                    if (mapped != null) {
                        return mapped;
                    }
                }
                return tryClimb(map, type, node.superName, name);
            }
        }
        return mapped;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        String mapped = tryClimb(jarMapping.methods, NodeType.METHOD, owner, name + " " + desc);
        return mapped == null ? name : mapped;
    }

    public static void renameJar(Jar jar, File target, JarMapping jarMapping) throws IOException {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(target));
        try {
            JarRemapper self = new JarRemapper(jarMapping, jar);
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

                        ClassReader reader = new ClassReader(is);
                        ClassWriter wr = new ClassWriter(0);
                        RemappingClassAdapter mapper = new RemappingClassAdapter(wr, self);
                        reader.accept(mapper, ClassReader.EXPAND_FRAMES);
                        data = wr.toByteArray();
                        String newName = self.map(name);

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
}

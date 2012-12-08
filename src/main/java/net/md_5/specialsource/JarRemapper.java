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
    private static final String HEADER = ""
            + "# THESE ARE AUTOMATICALLY GENERATED MAPPINGS BETWEEN {0} and {1}\n"
            + "# THEY WERE GENERATED ON {2} USING Special Source (c) md_5 2012.\n"
            + "# PLEASE DO NOT REMOVE THIS HEADER OR DISTRIBUTE THIS FILE WITHOUT PERMISSION!\n";
    private final JarComparer oldJar;
    private final JarComparer newJar;
    private final Jar self;
    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>();

    private JarRemapper(JarComparer oldJar, JarComparer newJar, Jar self, File logfile) throws IOException {
        SpecialSource.validate(oldJar, newJar);
        this.oldJar = oldJar;
        this.newJar = newJar;
        this.self = self;

        List<String> searge = new ArrayList<>();

        for (int i = 0; i < oldJar.classes.size(); i++) {
            String oldClass = oldJar.classes.get(i);
            String newClass = newJar.classes.get(i);
            classes.put(oldClass, newClass);
            if (!Objects.equals(oldClass, newClass)) {
                searge.add("CL: " + oldClass + " " + newClass);
            }
        }
        for (int i = 0; i < oldJar.fields.size(); i++) {
            Ownable oldField = oldJar.fields.get(i);
            Ownable newField = newJar.fields.get(i);
            fields.put(oldField.owner + "/" + oldField.name, newField.name);
            if (!Objects.equals(oldField, newField)) {
                searge.add("FD: " + oldField.owner + "/" + oldField.name + " " + newField.owner + "/" + newField.name);
            }
        }
        for (int i = 0; i < oldJar.methods.size(); i++) {
            Ownable oldMethod = oldJar.methods.get(i);
            Ownable newMethod = newJar.methods.get(i);
            methods.put(oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor, newMethod.name);
            if (!Objects.equals(oldMethod, newMethod)) {
                searge.add("MD: " + oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor + " " + newMethod.owner + "/" + newMethod.name + " " + newMethod.descriptor);
            }
        }

        Collections.sort(searge);
        try (PrintWriter out = new PrintWriter(logfile)) {
            out.println(MessageFormat.format(HEADER, oldJar.jar.file.getName(), newJar.jar.file.getName(), new Date()));
            for (String s : searge) {
                out.println(s);
            }
        }
    }

    @Override
    public String map(String typeName) {
        int index = typeName.indexOf('$');
        String key = (index == -1) ? typeName : typeName.substring(0, index);
        String mapped = classes.get(key);
        return mapped != null ? mapped + (index == -1 ? "" : typeName.substring(index, typeName.length())) : typeName;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String mapped = tryClimb(fields, NodeType.FIELD, owner, name);
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
        String mapped = tryClimb(methods, NodeType.METHOD, owner, name + " " + desc);
        return mapped == null ? name : mapped;
    }

    public static void renameJar(Jar jar, File target, JarComparer oldNames, JarComparer newNames) throws IOException {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(target))) {
            JarRemapper self = new JarRemapper(oldNames, newNames, jar, new File(target.getPath() + ".srg"));
            for (Enumeration<JarEntry> entr = jar.file.entries(); entr.hasMoreElements();) {
                JarEntry entry = entr.nextElement();
                try (InputStream is = jar.file.getInputStream(entry)) {
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
                        byte[] b = new byte[1 << 15]; // Max class file size, arbritrary number
                        while ((n = is.read(b, 0, b.length)) != -1) {
                            buffer.write(b, 0, n);
                        }
                        buffer.flush();
                        data = buffer.toByteArray();
                    }
                    entry.setTime(0);
                    out.putNextEntry(entry);
                    out.write(data);
                }
            }
        }
    }
}

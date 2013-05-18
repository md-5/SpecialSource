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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.RemappingFieldAdapter;
import org.objectweb.asm.tree.ClassNode;

import static org.objectweb.asm.ClassWriter.*;

public class JarRemapper extends Remapper {

    private static final int CLASS_LEN = ".class".length();
    public final JarMapping jarMapping;
    public RemapperPreprocessor remapperPreprocessor;
    private int writerFlags = COMPUTE_MAXS;
    private int readerFlags = 0;

    public JarRemapper(RemapperPreprocessor remapperPreprocessor, JarMapping jarMapping) {
        this.remapperPreprocessor = remapperPreprocessor;
        this.jarMapping = jarMapping;
    }

    public JarRemapper(JarMapping jarMapping) {
        this(null, jarMapping);
    }

    /**
     * Enable or disable API-only generation (stripping all code, leaving only symbols).
     */
    public void setGenerateAPI(boolean generateAPI) {
        if (generateAPI) {
            readerFlags |= ClassReader.SKIP_CODE;
        } else {
            readerFlags &= ~ClassReader.SKIP_CODE;
        }
    }

    @Override
    public String map(String typeName) {
        return mapTypeName(typeName, jarMapping.packages, jarMapping.classes, typeName);
    }

    public static String mapTypeName(String typeName, Map<String, String> packageMap, Map<String, String> classMap, String defaultIfUnmapped) {
        int index = typeName.indexOf('$');
        String key = (index == -1) ? typeName : typeName.substring(0, index);
        String mapped = mapClassName(key, packageMap, classMap);

        return mapped != null ? mapped + (index == -1 ? "" : typeName.substring(index, typeName.length())) : defaultIfUnmapped;
    }

    /**
     * Helper method to map a class name by package (prefix) or class (exact)
     */
    private static String mapClassName(String className, Map<String, String> packageMap, Map<String, String> classMap) {
        if (packageMap != null) {
            Iterator<String> iter = packageMap.keySet().iterator();
            while (iter.hasNext()) {
                String oldPackage = iter.next();
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
        String mapped = jarMapping.tryClimb(jarMapping.fields, NodeType.FIELD, owner, name);
        return mapped == null ? name : mapped;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        String mapped = jarMapping.tryClimb(jarMapping.methods, NodeType.METHOD, owner, name + " " + desc);
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
            for (String name : jar.getEntryNames()) {

                JarEntry entry;

                InputStream is = jar.getResource(name);
                try {
                    byte[] data;
                    if (name.endsWith(".class")) {
                        // remap classes
                        name = name.substring(0, name.length() - CLASS_LEN);

                        data = remapClassFile(is);
                        String newName = map(name);

                        entry = new JarEntry(newName == null ? name : newName + ".class");
                    } else if (name.endsWith(".DSA") || name.endsWith(".SF")) {
                        // skip signatures
                        continue;
                    } else {
                        // copy other resources
                        entry = new JarEntry(name);

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
        return remapClassFile(new ClassReader(is));
    }

    public byte[] remapClassFile(byte[] in) {
        return remapClassFile(new ClassReader(in));
    }

    @SuppressWarnings("unchecked")
    private byte[] remapClassFile(ClassReader reader) {
        if (remapperPreprocessor != null) {
            byte[] pre = remapperPreprocessor.preprocess(reader);
            if (pre != null) {
                reader = new ClassReader(pre);
            }
        }

        ClassNode node = new ClassNode();
        RemappingClassAdapter mapper = new RemappingClassAdapter(node, this)
        {
            @Override
            protected MethodVisitor createRemappingMethodAdapter(int access, String newDesc, MethodVisitor sup)
            {
                MethodVisitor remap = new UnsortedRemappingMethodAdapter(access, newDesc, sup, remapper);
                return new MethodVisitor(Opcodes.ASM4, remap)
                {
                    @Override
                    public void visitAttribute(Attribute attr)
                    {
                        if (SpecialSource.kill_lvt && attr.type.equals("LocalVariableTable")) return;  
                        if (SpecialSource.kill_generics && attr.type.equals("LocalVariableTypeTable")) return;                        
                        if (mv != null) mv.visitAttribute(attr);
                    }
                };
            }

            @Override
            protected FieldVisitor createRemappingFieldAdapter(FieldVisitor sup)
            {
                FieldVisitor remap = new RemappingFieldAdapter(sup, remapper);
                return new FieldVisitor(Opcodes.ASM4, sup)
                {
                    @Override
                    public void visitAttribute(Attribute attr)
                    {
                        if (SpecialSource.kill_lvt && attr.type.equals("LocalVariableTable")) return;
                        if (SpecialSource.kill_generics && attr.type.equals("LocalVariableTypeTable")) return;                        
                        if (fv != null) fv.visitAttribute(attr);
                    }
                };
            }

            @Override
            public void visitSource(String source, String debug)
            {
                if (!SpecialSource.kill_source && cv != null)
                {
                    cv.visitSource(source, debug);
                }
            }

            @Override
            public void visitAttribute(Attribute attr)
            {
                if (SpecialSource.kill_generics && attr.type.equals("Signature")) return;     
                if (cv != null) cv.visitAttribute(attr); 
            }
        };
        reader.accept(mapper, readerFlags);

        ClassWriter wr = new ClassWriter(writerFlags);
        node.accept(wr);
        if (SpecialSource.identifier != null)
        {
            wr.newUTF8(SpecialSource.identifier);
        }
        return wr.toByteArray();
    }
}

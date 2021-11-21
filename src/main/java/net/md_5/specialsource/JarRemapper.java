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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import lombok.Setter;
import net.md_5.specialsource.repo.ClassRepo;
import net.md_5.specialsource.repo.JarRepo;
import net.md_5.specialsource.writer.LogWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import static org.objectweb.asm.ClassWriter.*;

public class JarRemapper extends CustomRemapper {

    private static final int CLASS_LEN = ".class".length();
    private RemapperProcessor preProcessor;
    public final JarMapping jarMapping;
    private RemapperProcessor postProcessor;
    @Setter
    private LogWriter logWriter;
    private int writerFlags = COMPUTE_MAXS;
    private int readerFlags = 0;
    private boolean copyResources = true;

    public JarRemapper(RemapperProcessor preProcessor, JarMapping jarMapping, RemapperProcessor postProcessor) {
        this.preProcessor = preProcessor;
        this.jarMapping = jarMapping;
        this.postProcessor = postProcessor;
    }

    public JarRemapper(RemapperProcessor remapperPreprocessor, JarMapping jarMapping) {
        this(remapperPreprocessor, jarMapping, null);
    }

    public JarRemapper(JarMapping jarMapping) {
        this(null, jarMapping);
    }

    /**
     * Enable or disable API-only generation.
     *
     * If enabled, only symbols will be output to the remapped jar, suitable for
     * use as a library. Code and resources will be excluded.
     */
    public void setGenerateAPI(boolean generateAPI) {
        if (generateAPI) {
            readerFlags |= ClassReader.SKIP_CODE;
            copyResources = false;
        } else {
            readerFlags &= ~ClassReader.SKIP_CODE;
            copyResources = true;
        }
    }

    public void setLogFile(File file) throws FileNotFoundException {
        this.logWriter = new LogWriter(file);
    }

    @Override
    public String map(String typeName) {
        return mapTypeName(typeName, jarMapping.packages, jarMapping.classes, typeName);
    }

    public static String mapTypeName(String typeName, Map<String, String> packageMap, Map<String, String> classMap, String defaultIfUnmapped) {
        String mapped = mapClassName(typeName, packageMap, classMap);
        return mapped != null ? mapped : defaultIfUnmapped;
    }

    /**
     * Helper method to map a class name by package (prefix) or class (exact)
     */
    private static String mapClassName(String className, Map<String, String> packageMap, Map<String, String> classMap) {
        if (classMap != null && classMap.containsKey(className)) {
            return classMap.get(className);
        }
        
        int index = className.lastIndexOf('$');
        if (index != -1)
        {
            String outer = className.substring(0, index);
            String mapped = mapClassName(outer, packageMap, classMap);
            if  (mapped == null) return null;
            return mapped + className.substring(index);
        }

        if (packageMap != null) {
            Iterator<String> iter = packageMap.keySet().iterator();
            while (iter.hasNext()) {
                String oldPackage = iter.next();
                if (matchClassPackage(oldPackage, className)) {
                    String newPackage = packageMap.get(oldPackage);

                    return moveClassPackage(newPackage, getSimpleName(oldPackage, className));
                }
            }
        }

        return null;
    }

    private static boolean matchClassPackage(String packageName, String className) {
        if (packageName.equals(".")) {
            return isDefaultPackage(className);
        }

        return className.startsWith(packageName);
    }

    private static String moveClassPackage(String packageName, String classSimpleName) {
        if (packageName.equals(".")) {
            return classSimpleName;
        }

        return packageName + classSimpleName;
    }

    private static boolean isDefaultPackage(String className) {
        return className.indexOf('/') == -1;
    }

    private static String getSimpleName(String oldPackage, String className) {
        if (oldPackage.equals(".")) {
            return className;
        }

        return className.substring(oldPackage.length());
    }

    @Override
    public String mapFieldName(String owner, String name, String desc, int access) {
        String mapped = jarMapping.tryClimb(jarMapping.fields, NodeType.FIELD, owner, name, desc, access);
        return mapped == null ? name : mapped;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc, int access) {
        String mapped = jarMapping.tryClimb(jarMapping.methods, NodeType.METHOD, owner, name + " " + desc, null, access);
        return mapped == null ? name : mapped;
    }

    public void remapJar(Jar jar, File target) throws IOException {
        remapJar(jar, target, Collections.EMPTY_SET);
    }

    /**
     * Remap all the classes in a jar, writing a new jar to the target
     */
    public void remapJar(Jar jar, File target, Set<String> includes) throws IOException {
        if (jar == null) {
            return;
        }
        if (target.getParentFile() != null && !target.getParentFile().exists()) {
            target.getParentFile().mkdirs();
        }
        ClassRepo repo = new JarRepo(jar);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(target))) {
            Set<String> jarEntries = jar.getEntryNames();
            ProgressMeter meter = new ProgressMeter(jarEntries.size(), "Remapping jar... %2.0f%%");

            for (String name : jarEntries) {
                JarEntry entry;

                try (InputStream is = jar.getResource(name)) {
                    byte[] data;
                    if (name.endsWith(".class") && shouldHandle(name, includes)) {
                        // remap classes
                        name = name.substring(0, name.length() - CLASS_LEN);

                        data = remapClassFile(is, repo);
                        String newName = map(name);

                        entry = new JarEntry(newName == null ? name : newName + ".class");
                    } else if (name.endsWith(".DSA") || name.endsWith(".SF")) {
                        // skip signatures
                        continue;
                    } else {
                        // copy other resources
                        if (!copyResources) {
                            continue; // unless generating an API
                        }
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
                    if (SpecialSource.stable) {
                        entry.setTime(0);
                    }
                    out.putNextEntry(entry);
                    out.write(data);

                    meter.makeProgress();
                }
            }
        }
        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException ex) {
                //
            }
        }
    }

    private static boolean shouldHandle(String name, Set<String> includes) {
        if (includes.isEmpty()) {
            return true;
        }

        for (String match : includes) {
            if (match.equals(".") && !name.contains("/")) {
                return true;
            }
            if (name.startsWith(match)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remap an individual class given an InputStream to its bytecode
     */
    public byte[] remapClassFile(InputStream is, ClassRepo repo) throws IOException {
        return remapClassFile(new ClassReader(is), repo);
    }

    public byte[] remapClassFile(byte[] in, ClassRepo repo) {
        return remapClassFile(new ClassReader(in), repo);
    }

    @SuppressWarnings("unchecked")
    private byte[] remapClassFile(ClassReader reader, final ClassRepo repo) {
        if (preProcessor != null) {
            byte[] pre = preProcessor.process(reader);
            if (pre != null) {
                reader = new ClassReader(pre);
            }
        }

        ClassNode node = new ClassNode();
        RemappingClassAdapter mapper = new RemappingClassAdapter(node, this, repo);
        if (logWriter != null) {
            mapper.setLogWriter(logWriter);
        }
        reader.accept(mapper, readerFlags);

        ClassWriter wr = new ClassWriter(writerFlags);
        node.accept(wr);
        if (SpecialSource.identifier != null) {
            wr.newUTF8(SpecialSource.identifier);
        }

        return (postProcessor != null) ? postProcessor.process(wr.toByteArray()) : wr.toByteArray();
    }
}

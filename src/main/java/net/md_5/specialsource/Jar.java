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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * This class wraps a {@link JarFile} enabling quick access to the jar's main
 * class, as well as the ability to get the {@link InputStream} of a class file,
 * and speedy lookups to see if the jar contains the specified class.
 */
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Jar {

    public final JarFile file;
    public final String main;
    private final Set<String> contains = new HashSet<String>();
    private final Map<String, ClassNode> classes = new HashMap<String, ClassNode>();

    public boolean containsClass(String clazz) {
        return contains.contains(clazz) ? true : getClass(clazz) != null;
    }

    public InputStream getClass(String clazz) {
        try {
            ZipEntry e = file.getEntry(clazz + ".class");
            if (e != null) {
                contains.add(clazz);
            }
            return e == null ? null : file.getInputStream(e);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ClassNode getNode(String clazz) {
        try {
            ClassNode cache = classes.get(clazz);
            if (cache != null) {
                return cache;
            }
            InputStream is = getClass(clazz);
            if (is != null) {
                ClassReader cr = new ClassReader(getClass(clazz));
                ClassNode node = new ClassNode();
                cr.accept(node, 0);
                classes.put(clazz, node);
                return node;
            } else {
                return null;
            }
        } catch (IOException ex) {
            System.out.println(clazz);
            throw new RuntimeException(ex);
        }
    }

    public static Jar init(String jar) throws IOException {
        File file = new File(jar);
        return init(file);
    }

    public static Jar init(File file) throws IOException {
        JarFile jarFile = new JarFile(file);
        String main = null;

        Manifest manifest = jarFile.getManifest();
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            if (attributes != null) {
                String mainClassName = attributes.getValue("Main-Class");
                if (mainClassName != null) {
                    main = mainClassName.replace('.', '/');
                }
            }
        }

        return new Jar(jarFile, main);
    }
}

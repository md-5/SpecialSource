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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.md_5.specialsource.util.Pair2;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * This class wraps one or more {@link JarFile}s enabling quick access to the
 * jar's main class, as well as the ability to get the {@link InputStream} of a
 * class file, and speedy lookups to see if the jar contains the specified
 * class.
 */
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Jar implements Closeable {

    private final List<JarFile> jarFiles;
    @Getter
    private final String main;
    @Getter
    private final String filename;
    private final LinkedHashMap<String, JarFile> jarForResource;
    private final Set<String> contains = new HashSet<String>();

    /**
     * Check if this jar contains the given class. Takes the internal name of a
     * class (/).
     *
     * @param clazz
     * @return
     */
    public boolean containsClass(String clazz) {
        if (contains.contains(clazz)) {
            return true;
        }

        if (containsResource(clazz + ".class")) {
            contains.add(clazz);
            return true;
        }
        return false;
    }

    /**
     * Check if this jar contains the given resource.
     *
     * @param name The name of the resource
     * @return true Whether a resource with the given name exists
     */
    @SuppressWarnings("resource") // closed when the this Jar is closed
    public boolean containsResource(String name) {
        JarFile jarFile = jarForResource.get(name);
        return jarFile != null && jarFile.getEntry(name) != null;
    }

    /**
     * Get the stream for a file in this jar.
     *
     * @param name
     * @return
     * @throws IOException
     */
    @SuppressWarnings("resource") // closed when the this Jar is closed
    public InputStream getResource(String name) throws IOException {
        JarFile jarFile = jarForResource.get(name);
        if (jarFile == null) {
            return null;
        }

        ZipEntry e = jarFile.getEntry(name);
        return e == null ? null : jarFile.getInputStream(e);
    }

    /**
     * Get the stream for a file in this jar.
     *
     * @param name
     * @return
     * @throws IOException
     */
    @SuppressWarnings("resource") // closed when the this Jar is closed
    public Pair2<ZipEntry, InputStream> getEntry(String name) throws IOException {
        JarFile jarFile = jarForResource.get(name);
        if (jarFile == null) {
            return null;
        }

        ZipEntry e = jarFile.getEntry(name);
        return e == null ? null : new Pair2<>(e, jarFile.getInputStream(e));
    }

    /**
     * Takes the internal name of a class (/).
     *
     * @param clazz
     * @return
     * @throws IOException
     */
    public InputStream getClass(String clazz) throws IOException {
        InputStream inputStream = getResource(clazz + ".class");

        if (inputStream != null) {
            contains.add(clazz);
        }
        return inputStream;
    }

    /**
     * Get the {@link ClassNode} object corresponding to this class. Takes the
     * internal name of a class (/)
     *
     * @param clazz
     * @return
     */
    public ClassNode getNode(String clazz) {
        // No luck, so lets try read it
        try (InputStream is = getClass(clazz)) {
            if (is != null) {
                ClassReader cr = new ClassReader(is);
                // Process it
                ClassNode node = new ClassNode();
                cr.accept(node, 0);

                return node;
            }
        } catch (IOException ex) {
            // Wrap this in a runtime exception so it can conform easily to interfaces
            throw new RuntimeException(clazz, ex);
        }

        // We get here if the class isn't in the jar
        return null;
    }

    /**
     * Get all file names in the jar, (archive order is preserved).
     *
     * @return
     */
    public Set<String> getEntryNames() {
        return jarForResource.keySet(); // This is safe as LinkedHashMap.keySet is ordered
    }

    /**
     * Read and collect jar files so resources can override those in earlier
     * files.
     *
     * @param jarFiles
     * @return
     */
    private static LinkedHashMap<String, JarFile> collectJarFiles(List<JarFile> jarFiles) {
        LinkedHashMap<String, JarFile> jarForResource = new LinkedHashMap<String, JarFile>();
        // For all jars
        for (JarFile jarFile : jarFiles) {
            // Get all entries
            for (Enumeration<JarEntry> entr = jarFile.entries(); entr.hasMoreElements();) {
                // Add to list
                jarForResource.put(entr.nextElement().getName(), jarFile);
            }
            // continue through each jar file, overwriting subsequent classes in multiple jars ("jar mods")
        }

        return jarForResource;
    }

    /**
     * Get the (internal) name of the main class as declared in this manifest.
     *
     * @param manifest
     * @return
     */
    private static String getMainClassName(Manifest manifest) {
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            if (attributes != null) {
                String mainClassName = attributes.getValue("Main-Class");
                if (mainClassName != null) {
                    // TODO: To internal name
                    return mainClassName.replace('.', '/');
                }
            }
        }

        return null;
    }

    /**
     * Read a new jar instance from the given file.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static Jar init(File file) throws IOException {
        return init(Collections.singletonList(file));
    }

    /**
     * Read a new jar instance from the given list of files.
     *
     * @param files
     * @return
     * @throws IOException
     */
    public static Jar init(List<File> files) throws IOException {
        Preconditions.checkArgument(files.size() > 0, "Jar init requires at least one file!");

        // Save some time by resizing these to their target size
        List<JarFile> jarFiles = new ArrayList<JarFile>(files.size());
        List<String> filenames = new ArrayList<String>(files.size());

        // Populate file names and JarFiles
        for (File file : files) {
            filenames.add(file.getName());
            jarFiles.add(new JarFile(file, false));
        }

        LinkedHashMap<String, JarFile> jarForResource = collectJarFiles(jarFiles);
        String fileName = Joiner.on(" + ").join(filenames);

        String main = null;
        // For each jar
        for (JarFile jar : jarFiles) {
            // Get main
            String newMain = getMainClassName(jar.getManifest());
            // If they have a main
            if (newMain != null) {
                // If we haven't set a main already, then set
                if (main == null) {
                    main = newMain;
                } else {
                    // Else warn that there are many main classes in the set we have been given
                    System.err.println("[Warning] Duplicate Main classes for " + fileName);
                }
            }
        }

        // Return the new all encompassing jar instance. The file name will be the sum of all names.
        return new Jar(jarFiles, main, fileName, jarForResource);
    }

    /**
     * Closes all jar files in this Jar
     *
     * @see java.io.Closeable
     * @throws IOException if an I/O error has occurred
     */
    @Override
    public void close() throws IOException {
        for (JarFile jarFile : jarFiles) {
            jarFile.close();
        }
        jarFiles.clear();
        jarForResource.clear();
        contains.clear();
    }
}

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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.objectweb.asm.ClassReader;

import static java.util.Arrays.asList;

public class SpecialSource {

    private static OptionSet options;

    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser() {
            {
                acceptsAll(asList("?", "help"), "Show the help");

                acceptsAll(asList("a", "first-jar"), "First jar with original names, for generating mapping")
                        .withRequiredArg()
                        .ofType(String.class);

                acceptsAll(asList("b", "second-jar"), "Second jar with renamed names, for generating mapping")
                        .withRequiredArg()
                        .ofType(String.class);

                acceptsAll(asList("s", "srg-out"), "Mapping file output")
                        .withRequiredArg()
                        .ofType(File.class);

                acceptsAll(asList("c", "compact"), "Output mapping file in compact format");
                acceptsAll(asList("f", "generate-dupes"), "Include unrenamed symbols in mapping file output");

                acceptsAll(asList("m", "srg-in"), "Mapping file input")
                        .withRequiredArg()
                        .ofType(String.class);

                acceptsAll(asList("n", "numeric-srg"), "Use numeric .srg mappings with srg-in dir (num->mcp vs obf->mcp)");

                acceptsAll(asList("R", "in-shade-relocation", "shade-relocation"), "Simulate maven-shade-plugin relocation patterns on srg-in input names")
                        .withRequiredArg();

                acceptsAll(asList("out-shade-relocation"), "Simulate maven-shade-plugin relocation patterns on srg-in output names")
                        .withRequiredArg();

                acceptsAll(asList("r", "reverse"), "Reverse input/output names on srg-in");

                acceptsAll(asList("i", "in-jar"), "Input jar(s) to remap")
                        .withRequiredArg()
                        .ofType(String.class);

                acceptsAll(asList("o", "out-jar"), "Output jar to write")
                        .withRequiredArg()
                        .ofType(File.class);


                acceptsAll(asList("force-redownload"), "Force redownloading remote resources (invalid cache)");

                acceptsAll(asList("l", "live"), "Enable runtime inheritance lookup");
                acceptsAll(asList("L", "live-remapped"), "Enable runtime inheritance lookup through a mapping");

                acceptsAll(asList("H", "write-inheritance"), "Write inheritance map to file")
                        .withRequiredArg()
                        .ofType(File.class);
                acceptsAll(asList("h", "read-inheritance"), "Read inheritance map from file")
                        .withRequiredArg()
                        .ofType(File.class);

                //acceptsAll(asList("G", "remap-reflect-field"), "Remap reflection calls to getDeclaredField()"); // TODO

                acceptsAll(asList("q", "quiet"), "Quiet mode");
            }
        };

        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println(ex.getLocalizedMessage());
            System.exit(-1);
            return;
        }

        if (options == null || options.has("?")) {
            try {
                parser.printHelpOn(System.err);
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
            }
            System.exit(-1);
            return;
        }

        JarMapping jarMapping;
        URLDownloader.verbose = !options.has("quiet");
        URLDownloader.useCache = !options.has("force-redownload");

        if (options.has("first-jar") && options.has("second-jar")) {
            // Generate mappings from two otherwise-identical jars
            log("Reading jars");
            Jar jar1 = Jar.init(URLDownloader.getLocalFile((String) options.valueOf("first-jar")));
            Jar jar2 = Jar.init(URLDownloader.getLocalFile((String) options.valueOf("second-jar")));

            log("Creating jar compare");
            JarComparer visitor1 = new JarComparer(jar1);
            JarComparer visitor2 = new JarComparer(jar2);
            visit(new Pair<Jar>(jar1, jar2), new Pair<JarComparer>(visitor1, visitor2), new Pair<String>(jar1.getMain(), jar2.getMain()));

            jarMapping = new JarMapping(visitor1, visitor2, (File) options.valueOf("srg-out"), options.has("compact"), options.has("generate-dupes"));
        } else if (options.has("srg-in")) {
            log("Loading mappings");

            jarMapping = new JarMapping();

            // Loading options
            boolean reverse = options.has("reverse");
            boolean numeric = options.has("numeric-srg");
            String inShadeRelocation = (String) options.valueOf("in-shade-relocation");
            String outShadeRelocation = (String) options.valueOf("out-shade-relocation");

            // Load each mapping
            @SuppressWarnings("unchecked")
            List<String> filenames = (List<String>) options.valuesOf("srg-in");
            for (String filename : filenames) {
                jarMapping.loadMappings(filename, reverse, numeric, inShadeRelocation, outShadeRelocation);
            }
        } else {
            System.err.println("No mappings given, first-jar/second-jar or srg-in required");
            parser.printHelpOn(System.err);
            System.exit(-1);
            return;
        }
        log(jarMapping.packages.size() + " packages, " + jarMapping.classes.size() + " classes, " + jarMapping.fields.size() + " fields, " + jarMapping.methods.size() + " methods");

        InheritanceProviders inheritanceProviders = new InheritanceProviders();
        jarMapping.setFallbackInheritanceProvider(inheritanceProviders);

        if (options.has("live-remapped")) {
            inheritanceProviders.add(new RemappedRuntimeInheritanceProvider(ClassLoader.getSystemClassLoader(), !options.has("quiet"), jarMapping));
        }

        if (options.has("live")) {
            inheritanceProviders.add(new RuntimeInheritanceProvider(ClassLoader.getSystemClassLoader(), !options.has("quiet")));
        }

        if (options.has("read-inheritance")) {
            InheritanceMap inheritanceMap = new InheritanceMap();

            BufferedReader reader = new BufferedReader(new FileReader((File) options.valueOf("read-inheritance")));

            BiMap<String, String> inverseClassMap = HashBiMap.create(jarMapping.classes).inverse();
            inheritanceMap.load(reader, inverseClassMap);
            log("Loaded inheritance map for "+inheritanceMap.size()+" classes");

            inheritanceProviders.add(inheritanceMap);
        }


        if (options.has("in-jar")) {
            if (!options.has("out-jar")) {
                System.err.println("No output jar given, in-jar requires out-jar");
                parser.printHelpOn(System.err);
                System.exit(-1);
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> filenames = (List<String>) options.valuesOf("in-jar");
            List<File> files = new ArrayList<File>();
            for (String filename : filenames) {
                files.add(URLDownloader.getLocalFile(filename));
            }

            Jar jar3 = Jar.init(files);

            inheritanceProviders.add(new JarInheritanceProvider(jar3));

            log("Remapping final jar");
            JarRemapper jarRemapper = new JarRemapper(jarMapping);
            jarRemapper.remapJar(jar3, (File) options.valueOf("out-jar"));
        }


        if (options.has("write-inheritance")) {
            InheritanceMap inheritanceMap = new InheritanceMap();

            inheritanceMap.generate(inheritanceProviders, jarMapping.classes.values());
            PrintWriter printWriter = new PrintWriter((File) options.valueOf("write-inheritance"));
            inheritanceMap.save(printWriter);
            printWriter.close();
        }
    }

    private static void log(String message) {
        if (options != null && !options.has("quiet")) {
            System.out.println(message);
        }
    }

    private static void visit(Pair<Jar> jars, Pair<JarComparer> visitors, Pair<String> classes) throws IOException {
        JarComparer visitor1 = visitors.first;
        JarComparer visitor2 = visitors.second;

        ClassReader clazz1 = new ClassReader(jars.first.getClass(classes.first));
        ClassReader clazz2 = new ClassReader(jars.second.getClass(classes.second));
        clazz1.accept(visitor1, 0);
        clazz2.accept(visitor2, 0);

        validate(visitor1, visitor2);

        while (visitor1.iterDepth < visitor1.classes.size()) {
            String className1 = visitor1.classes.get(visitor1.iterDepth);
            String className2 = visitor2.classes.get(visitor1.iterDepth);
            Pair<String> pair = new Pair<String>(className1, className2);
            visitor1.iterDepth++;
            visit(jars, visitors, pair);
        }
    }

    public static void validate(JarComparer visitor1, JarComparer visitor2) {
        if (visitor1.classes.size() != visitor2.classes.size()) {
            throw new IllegalStateException("classes "+visitor1.classes.size()+" != "+visitor2.classes.size());
        }
        if (visitor1.fields.size() != visitor2.fields.size()) {
            throw new IllegalStateException("fields "+visitor1.fields.size()+" != "+visitor2.fields.size());
        }
        if (visitor1.methods.size() != visitor2.methods.size()) {
            throw new IllegalStateException("methods "+visitor1.methods.size()+" != "+visitor2.methods.size());
        }
    }
}

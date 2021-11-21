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

import net.md_5.specialsource.util.Pair;
import net.md_5.specialsource.util.FileLocator;
import net.md_5.specialsource.provider.JointProvider;
import net.md_5.specialsource.provider.JarProvider;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.objectweb.asm.ClassReader;

import static java.util.Arrays.asList;
import net.md_5.specialsource.provider.ClassLoaderProvider;

public class SpecialSource {

    private static OptionSet options;
    private static boolean verbose;
    public static boolean kill_source = false;
    public static boolean kill_lvt = false;
    public static boolean kill_generics = false;
    public static String identifier = null;
    public static boolean stable = false;

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

                acceptsAll(asList("access-transformer"), "Access transformer file")
                        .withRequiredArg()
                        .ofType(File.class);

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
                        .ofType(String.class);

                //acceptsAll(asList("G", "remap-reflect-field"), "Remap reflection calls to getDeclaredField()"); // TODO

                acceptsAll(asList("q", "quiet"), "Quiet mode");
                acceptsAll(asList("progress-interval"),"% markers at which to print progress")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(10);
                acceptsAll(asList("stable"), "Attempts to make output stable for a given input");

                acceptsAll(asList("v", "version"), "Displays version information");

                acceptsAll(asList("kill-source"), "Removes the \"SourceFile\" attribute");
                acceptsAll(asList("kill-lvt"), "Removes the \"LocalVariableTable\" attribute");
                acceptsAll(asList("kill-generics"), "Removes the \"LocalVariableTypeTable\" and \"Signature\" attributes");
                acceptsAll(asList("d", "identifier"), "Identifier to place on each class that is transformed, by default, none")
                        .withRequiredArg()
                        .ofType(String.class);
                acceptsAll(asList("e", "excluded-packages"), "A comma seperated list of packages that should not be transformed, even if the srg specifies they should")
                        .withRequiredArg()
                        .ofType(String.class);

                acceptsAll(asList("only"), "Process only the specified packages. Similar to --excluded-packages but applies at the processing rather than loading phase")
                        .withRequiredArg()
                        .ofType(String.class);

                acceptsAll(asList("log"), "Output log to write")
                        .withRequiredArg()
                        .ofType(File.class);
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

        // has default
        ProgressMeter.printInterval = ((Number) options.valueOf("progress-interval")).doubleValue();

        if (options.has("version"))
        {
            System.out.println("SpecialSource v{something}");
            return;
        }

        if (options.has("in-jar") && !options.has("out-jar")) {
            System.err.println("No output jar given, in-jar requires out-jar");
            parser.printHelpOn(System.err);
            System.exit(-1);
            return;
        }

        JarMapping jarMapping;
        verbose = !options.has("quiet");
        kill_source = options.has("kill-source");
        kill_lvt = options.has("kill-lvt");
        kill_generics = options.has("kill-generics");
        
        if (options.has("identifier"))
        {
            identifier = (String)options.valueOf("identifier");
        }

        String[] excluded = new String[0];
        if (options.has("excluded-packages"))
        {
            excluded = ((String)options.valueOf("excluded-packages")).split(",");
        }

        FileLocator.useCache = !options.has("force-redownload");
        SpecialSource.stable = options.has("stable");

        Jar jar1 = null, jar2 = null, jar3 = null;

        if (options.has("first-jar") && options.has("second-jar")) {
            // Generate mappings from two otherwise-identical jars
            log("Reading jars");
            jar1 = Jar.init(FileLocator.getFile((String) options.valueOf("first-jar")));
            jar2 = Jar.init(FileLocator.getFile((String) options.valueOf("second-jar")));

            if (jar1.getMain() == null || jar2.getMain() == null) {
                System.err.println("Jars for comparison must both define Main-Class manifest attribute");
                System.exit(-1);
                return;
            }

            log("Creating jar compare");
            JarComparer visitor1 = new JarComparer(jar1);
            JarComparer visitor2 = new JarComparer(jar2);
            visit(new Pair<Jar>(jar1, jar2), new Pair<JarComparer>(visitor1, visitor2), new Pair<String>(jar1.getMain(), jar2.getMain()));

            jarMapping = new JarMapping(visitor1, visitor2, (File) options.valueOf("srg-out"), options.has("compact"), options.has("generate-dupes"));
            for (String pkg : excluded)
            {
                jarMapping.addExcludedPackage(pkg);
            }
        } else if (options.has("srg-in")) {
            log("Loading mappings");

            jarMapping = new JarMapping();
            for (String pkg : excluded)
            {
                jarMapping.addExcludedPackage(pkg);
            }

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

        JointProvider inheritanceProviders = new JointProvider();
        jarMapping.setFallbackInheritanceProvider(inheritanceProviders);

        if (options.has("live")) {
            inheritanceProviders.add(new ClassLoaderProvider(ClassLoader.getSystemClassLoader()));
        }

        if (options.has("read-inheritance")) {
            InheritanceMap inheritanceMap = new InheritanceMap();

            BiMap<String, String> inverseClassMap = HashBiMap.create(jarMapping.classes).inverse();
            File inheritanceFile = FileLocator.getFile((String) options.valueOf("read-inheritance"));
            try (BufferedReader reader = new BufferedReader(new FileReader(inheritanceFile))) {
                inheritanceMap.load(reader, inverseClassMap);
            }
            log("Loaded inheritance map for " + inheritanceMap.size() + " classes");

            inheritanceProviders.add(inheritanceMap);
        }

        RemapperProcessor accessMapper = null;
        AccessMap access = null;
        if (options.has("access-transformer")) {
            access = new AccessMap();
            access.loadAccessTransformer((File) options.valueOf("access-transformer"));
            accessMapper = new RemapperProcessor(null, jarMapping, access);
        }

        if (options.has("in-jar") && options.has("out-jar")) {
            @SuppressWarnings("unchecked")
            List<String> filenames = (List<String>) options.valuesOf("in-jar");
            List<File> files = new ArrayList<File>();
            for (String filename : filenames) {
                files.add(FileLocator.getFile(filename));
            }

            jar3 = Jar.init(files);

            inheritanceProviders.add(new JarProvider(jar3));

            log("Remapping final jar");
            JarRemapper jarRemapper = new JarRemapper(null, jarMapping, accessMapper);
            if (options.has("log")) {
                File logOutput = (File) options.valueOf("log");
                jarRemapper.setLogFile(logOutput);
            }

            jarRemapper.remapJar(jar3, (File) options.valueOf("out-jar"), new HashSet<String>((Collection<String>) options.valuesOf("only")));
        }


        if (options.has("write-inheritance")) {
            InheritanceMap inheritanceMap = new InheritanceMap();

            inheritanceMap.generate(inheritanceProviders, jarMapping.classes.values());
            try (PrintWriter printWriter = new PrintWriter((File) options.valueOf("write-inheritance"))) {
                inheritanceMap.save(printWriter);
            }
        }

        if (access != null) {
            for (String entry : access.getMap().keySet()) {
                if (!access.getAppliedMaps().contains(entry)) {
                    System.out.println("[WARN] Access map not applied: " + entry);
                }
            }
        }
        if (jar1 != null) jar1.close();
        if (jar2 != null) jar2.close();
        if (jar3 != null) jar3.close();
    }

    public static void log(String message) {
        if (options != null && !options.has("quiet")) {
            System.out.println(message);
        }
    }

    private static void visit(Pair<Jar> jars, Pair<JarComparer> visitors, Pair<String> classes) throws IOException {
        JarComparer visitor1 = visitors.first;
        JarComparer visitor2 = visitors.second;

        ClassReader clazz1, clazz2;
        try (InputStream first = jars.first.getClass(classes.first);
             InputStream second = jars.second.getClass(classes.second)) {
            clazz1 = new ClassReader(first);
            clazz2 = new ClassReader(second);
        }
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
            throw new IllegalStateException("classes " + visitor1.classes.size() + " != " + visitor2.classes.size());
        }
        if (visitor1.fields.size() != visitor2.fields.size()) {
            throw new IllegalStateException("fields " + visitor1.fields.size() + " != " + visitor2.fields.size());
        }
        if (visitor1.methods.size() != visitor2.methods.size()) {
            throw new IllegalStateException("methods " + visitor1.methods.size() + " != " + visitor2.methods.size());
        }
    }

    public static boolean verbose() {
        return verbose;
    }
}

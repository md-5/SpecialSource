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

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.objectweb.asm.ClassReader;

import static java.util.Arrays.asList;

public class SpecialSource {

    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser() {
            {
                acceptsAll(asList("?", "help"), "Show the help");

                acceptsAll(asList("a", "first-jar"), "First jar")
                        .withRequiredArg()
                        .ofType(File.class);

                acceptsAll(asList("b", "second-jar"), "Second jar")
                        .withRequiredArg()
                        .ofType(File.class);

                acceptsAll(asList("s", "srg-out"), "Mapping srg output")
                        .withRequiredArg()
                        .ofType(File.class);

                acceptsAll(asList("i", "remap-jar"), "Input jar to remap")
                        .withRequiredArg()
                        .ofType(File.class);

                acceptsAll(asList("o", "out-jar"), "Output jar to write")
                        .withRequiredArg()
                        .ofType(File.class);
            }
        };

        OptionSet options = null;

        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println(ex.getLocalizedMessage());
            return;
        }

        if (options == null || options.has("?") || !options.has("a") || !options.has("b")) {
            try {
                parser.printHelpOn(System.out);
                return;
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
                return;
            }
        }

        /* TODO: move to help
        if (args.length != 2 && args.length != 3) {
            System.err.println("SpecialSource takes 2 or 3 arguments. It will take 2 jars to generate a difference between, and a 3rd jar based on the first jar to rename to the second jar.");
            System.err.println("Usage: java -jar SpecialSource.jar <first jar> <second jar> [<jar of first names>]");
            System.err.println("It is currently tuned to only accept a Minecraft v1.4.5 server jar as the 2 jars to compare");
            return;
        }*/

        System.out.println("Reading jars");
        Jar jar1 = Jar.init((File)options.valueOf("first-jar"));
        Jar jar2 = Jar.init((File)options.valueOf("second-jar"));

        System.out.println("Creating jar compare");
        JarComparer visitor1 = new JarComparer(jar1);
        JarComparer visitor2 = new JarComparer(jar2);
        visit(new Pair<Jar>(jar1, jar2), new Pair<JarComparer>(visitor1, visitor2), new Pair<String>(jar1.main, jar2.main));

        JarMapping jarMapping = new JarMapping(visitor1, visitor2, (File)options.valueOf("srg-out"));

        if (options.has("in-jar")) {
            System.out.println("Remapping final jar");
            Jar jar3 = Jar.init((File)options.valueOf("remap-jar"));
            JarRemapper.renameJar(jar3, (File)options.valueOf("out-jar"), jarMapping);
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
            throw new IllegalStateException("classes");
        }
        if (visitor1.fields.size() != visitor2.fields.size()) {
            throw new IllegalStateException("fields");
        }
        if (visitor1.methods.size() != visitor2.methods.size()) {
            throw new IllegalStateException("methods");
        }
    }
}

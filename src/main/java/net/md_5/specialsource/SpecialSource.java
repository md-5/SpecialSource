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
import org.objectweb.asm.ClassReader;

public class SpecialSource {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("SpecialSource takes 3 arguments. It will take 2 jars to generate a difference between, and a 3rd jar based on the first jar to rename to the second jar.");
            System.err.println("Usage: java -jar SpecialSource.jar <first jar> <second jar> <jar of first names>");
            System.err.println("It is currently tuned to only accept a Minecraft v1.4.5 server jar as the 2 jars to compare");
            return;
        }

        System.out.println("Reading jars");
        Jar jar1 = Jar.init(args[0]);
        Jar jar2 = Jar.init(args[1]);

        System.out.println("Creating jar compare");
        JarComparer visitor1 = new JarComparer(jar1);
        JarComparer visitor2 = new JarComparer(jar2);
        visit(new Pair<Jar>(jar1, jar2), new Pair<JarComparer>(visitor1, visitor2), new Pair<String>(jar1.main, jar2.main));

        System.out.println("Checking vailidity");
        if (visitor1.classes.size() != 1004 || visitor2.classes.size() != 1004) {
            throw new IllegalStateException("classes");
        }
        if (visitor1.fields.size() != 3582 || visitor2.fields.size() != 3582) {
            throw new IllegalStateException("fields");
        }
        if (visitor1.methods.size() != 6531 + 4 || visitor2.methods.size() != 6531 + 4) { // 3 broken enums (EnumEntitySize, EnumFacing, EnumGameType), and 1 main method
            throw new IllegalStateException("methods");
        }

        System.out.println("Renaming final jar");
        JarRemapper.renameJar(Jar.init(args[2]), new File("out.jar"), visitor1, visitor2);
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

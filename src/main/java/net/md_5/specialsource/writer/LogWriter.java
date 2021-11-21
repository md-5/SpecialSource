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
package net.md_5.specialsource.writer;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import lombok.Data;
import org.objectweb.asm.Type;

@Data
public class LogWriter implements Closeable {

    private final PrintWriter out;

    public LogWriter(File file) throws FileNotFoundException {
        this.out = new PrintWriter(file);
    }

    public void addClassMap(String oldName, String newName) {
        writeLn(type(Type.getObjectType(oldName)) + " -> " + type(Type.getObjectType(newName)));
    }

    public void addFieldMap(String oldDesc, String oldName, String newName) {
        writeLn("    " + type(Type.getType(oldDesc)) + " " + oldName + " -> " + newName);
    }

    public void addMethodMap(int startLine, int endLine, String oldDesc, String oldName, String newName) {
        String lines = "";
        if (startLine != Integer.MAX_VALUE && endLine != Integer.MIN_VALUE) {
            lines = startLine + ":" + endLine + ":";
        }

        writeLn("    " + lines + method(oldDesc, oldName) + " -> " + newName);
    }

    private static String method(String desc, String name) {
        Type type = Type.getMethodType(desc);
        Type ret = type.getReturnType();
        Type[] args = type.getArgumentTypes();

        return type(ret) + " " + name + "(" + type(args) + ")";
    }

    private static String type(Type... type) {
        return Joiner.on(',').join(Iterables.transform(Arrays.asList(type), new Function<Type, String>() {
            @Override
            public String apply(Type input) {
                return type(input);
            }
        }));
    }

    private static String type(Type type) {
        return type.getClassName();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private void writeLn(String s) {
        out.println(s);
    }
}

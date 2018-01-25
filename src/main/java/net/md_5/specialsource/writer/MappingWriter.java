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

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.md_5.specialsource.Ownable;

@RequiredArgsConstructor
public abstract class MappingWriter {

    private static final String HEADER = ""
            + "# THESE ARE AUTOMATICALLY GENERATED MAPPINGS BETWEEN {0} and {1}\n"
            + "# THEY WERE GENERATED ON {2} USING Special Source (c) md_5 2012-2013.\n"
            + "# PLEASE DO NOT REMOVE THIS HEADER!\n";
    private final List<String> lines = new ArrayList<String>();
    private final String oldJarName;
    private final String newJarName;

    public abstract void addClassMap(String oldClass, String newClass);

    public abstract void addFieldMap(Ownable oldField, Ownable newField);

    public abstract void addMethodMap(Ownable oldMethod, Ownable newMethod);

    public final void write(PrintWriter out) {
        // Sort lines for easy finding
        Collections.sort(lines);
        // Format header
        out.println(MessageFormat.format(HEADER, oldJarName, newJarName, new Date()));
        // Write out lines
        for (String s : lines) {
            out.println(s);
        }
        // Caller is in charge of closes the output stream
    }

    protected final void addLine(String line) {
        lines.add(line);
    }
}

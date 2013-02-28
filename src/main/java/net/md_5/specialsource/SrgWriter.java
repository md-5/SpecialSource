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

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class SrgWriter implements ISrgWriter {

    private static final String HEADER = ""
            + "# THESE ARE AUTOMATICALLY GENERATED MAPPINGS BETWEEN {0} and {1}\n"
            + "# THEY WERE GENERATED ON {2} USING Special Source (c) md_5 2012-2013.\n"
            + "# PLEASE DO NOT REMOVE THIS HEADER!\n";
    private List<String> lines;
    private PrintWriter out;
    private String oldJarName;
    private String newJarName;

    public SrgWriter(PrintWriter out, String oldJarName, String newJarName) {
        this.lines = new ArrayList<String>();

        this.out = out;
        this.oldJarName = oldJarName;
        this.newJarName = newJarName;
    }

    @Override
    public void addClassMap(String oldClass, String newClass) {
        lines.add("CL: " + oldClass + " " + newClass);
    }

    @Override
    public void addFieldMap(Ownable oldField, Ownable newField) {
        lines.add("FD: " + oldField.owner + "/" + oldField.name + " " + newField.owner + "/" + newField.name);
    }

    @Override
    public void addMethodMap(Ownable oldMethod, Ownable newMethod) {
        lines.add("MD: " + oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor + " " + newMethod.owner + "/" + newMethod.name + " " + newMethod.descriptor);
    }

    @Override
    public void write() throws IOException {
        Collections.sort(lines);
        // No try with resources for us!
        try {
            out.println(MessageFormat.format(HEADER, oldJarName, newJarName, new Date()));
            for (String s : lines) {
                out.println(s);
            }
        } finally {
            out.close();
        }
    }
}

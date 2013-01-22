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
import java.io.PrintWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.*;

public class JarMapping {
    // Mappings from old to new name
    public final Map<String, String> classes = new HashMap<String, String>();
    public final Map<String, String> fields = new HashMap<String, String>();
    public final Map<String, String> methods = new HashMap<String, String>();

    private static final String HEADER = ""
        + "# THESE ARE AUTOMATICALLY GENERATED MAPPINGS BETWEEN {0} and {1}\n"
        + "# THEY WERE GENERATED ON {2} USING Special Source (c) md_5 2012.\n"
        + "# PLEASE DO NOT REMOVE THIS HEADER!\n";

    public JarMapping(JarComparer oldJar, JarComparer newJar, File logfile) throws IOException {
        SpecialSource.validate(oldJar, newJar);

        List<String> searge = new ArrayList<String>(); // TODO: refactor

        for (int i = 0; i < oldJar.classes.size(); i++) {
            String oldClass = oldJar.classes.get(i);
            String newClass = newJar.classes.get(i);
            classes.put(oldClass, newClass);
            if (!Objects.equals(oldClass, newClass)) {
                searge.add("CL: " + oldClass + " " + newClass);
            }
        }
        for (int i = 0; i < oldJar.fields.size(); i++) {
            Ownable oldField = oldJar.fields.get(i);
            Ownable newField = newJar.fields.get(i);
            String key = oldField.owner + "/" + oldField.name;
            fields.put(key, newField.name);

            if (!Objects.equals(oldField.name, newField.name)) {
                searge.add("FD: " + oldField.owner + "/" + oldField.name + " " + newField.owner + "/" + newField.name);
            }
        }
        for (int i = 0; i < oldJar.methods.size(); i++) {
            Ownable oldMethod = oldJar.methods.get(i);
            Ownable newMethod = newJar.methods.get(i);
            String key = oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor;
            methods.put(key, newMethod.name);

            String oldDescriptor = oldMethod.descriptor;
            for (Map.Entry<String, String> entry : classes.entrySet()) {
                oldDescriptor = oldDescriptor.replaceAll("L" + entry.getKey() + ";", "L" + entry.getValue() + ";"); // TODO: efficiency
            }

            if (!Objects.equals(oldMethod.name + " " + oldDescriptor, newMethod.name + " " + newMethod.descriptor)) {
                searge.add("MD: " + oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor + " " + newMethod.owner + "/" + newMethod.name + " " + newMethod.descriptor);
            }
        }

        PrintWriter out;

        if (logfile == null) {
            out = new PrintWriter(System.out);
        } else {
            System.out.println("Writing mapping file");
            out = new PrintWriter(logfile);
        }

        Collections.sort(searge);
        // No try with resources for us!
        try {
            out.println(MessageFormat.format(HEADER, oldJar.jar.file.getName(), newJar.jar.file.getName(), new Date()));
            for (String s : searge) {
                out.println(s);
            }
        } finally {
            out.close();
        }
    }
}

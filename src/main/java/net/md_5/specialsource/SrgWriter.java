package net.md_5.specialsource;

import java.io.File;
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
            + "# THEY WERE GENERATED ON {2} USING Special Source (c) md_5 2012.\n"
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

    public void addClassMap(String oldClass, String newClass) {
        lines.add("CL: " + oldClass + " " + newClass);
    }

    public void addFieldMap(Ownable oldField, Ownable newField) {
        lines.add("FD: " + oldField.owner + "/" + oldField.name + " " + newField.owner + "/" + newField.name);
    }

    public void addMethodMap(Ownable oldMethod, Ownable newMethod) {
        lines.add("MD: " + oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor + " " + newMethod.owner + "/" + newMethod.name + " " + newMethod.descriptor);
    }

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

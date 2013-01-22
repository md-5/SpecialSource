package net.md_5.specialsource;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompactSrgWriter implements ISrgWriter {
    private PrintWriter out;
    private List<String> lines;

    public CompactSrgWriter(PrintWriter out) {
        this.out = out;
        this.lines = new ArrayList<String>();
    }

    @Override
    public void addClassMap(String oldClass, String newClass) {
        lines.add(oldClass+" "+newClass);
    }

    @Override
    public void addFieldMap(Ownable oldField, Ownable newField) {
        lines.add(oldField.owner+" "+oldField.name+" "+newField.name);
    }

    @Override
    public void addMethodMap(Ownable oldMethod, Ownable newMethod) {
        lines.add(oldMethod.owner+" "+oldMethod.name+" "+oldMethod.descriptor+" "+newMethod.name);
    }

    @Override
    public void write() throws IOException {
        Collections.sort(lines);

        for (String s : lines) {
            out.println(s);
        }
        out.close();
    }
}

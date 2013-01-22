package net.md_5.specialsource;

import java.io.File;
import java.io.IOException;

public interface ISrgWriter {
    void addClassMap(String oldClass, String newClass);

    void addFieldMap(Ownable oldField, Ownable newField);

    void addMethodMap(Ownable oldMethod, Ownable newMethod);

    void write() throws IOException;
}

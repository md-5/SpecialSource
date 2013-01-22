package net.md_5.specialsource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class CompactSrgReader {
    public CompactSrgReader(File file, JarMapping jarMapping) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));

        String line;
        while((line = reader.readLine()) != null) {
            String[] tokens = line.split(" ");
            if (tokens.length == 2) {
                String oldClassName = tokens[0];
                String newClassName = tokens[1];
                jarMapping.classes.put(oldClassName, newClassName);
            } else if (tokens.length == 3) {
                String oldClassName = tokens[0];
                String oldFieldName = tokens[1];
                String newFieldName = tokens[2];
                jarMapping.fields.put(oldClassName + "/" + oldFieldName, newFieldName);
            } else if (tokens.length == 4) {
                String oldClassName = tokens[0];
                String oldMethodName = tokens[1];
                String oldMethodDescriptor = tokens[2];
                String newMethodName = tokens[3];
                jarMapping.methods.put(oldClassName + "/" + oldMethodName + " " + oldMethodDescriptor, newMethodName);
            }
        }
    }
}

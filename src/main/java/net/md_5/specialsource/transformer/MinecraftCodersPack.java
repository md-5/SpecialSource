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
package net.md_5.specialsource.transformer;

import com.google.common.base.Preconditions;
import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.md_5.specialsource.JarRemapper;

/**
 * For reading a srg through MCP's fields.csv and methods.csv Maps func_### and
 * field_### in input srg to "descriptive" names.
 */
public class MinecraftCodersPack extends MappingTransformer {

    private final Map<String, String> fieldMap = new HashMap<String, String>(); // numeric srg name field_### -> descriptive csv name
    private final Map<String, String> methodMap = new HashMap<String, String>(); // numeric srg name func_### -> descriptive csv name
    private final Map<String, String> classPackageMap; // class src name -> repackaged full class name

    public MinecraftCodersPack(File fieldsCsv, File methodsCsv, File packagesCsv) throws IOException {
        if (fieldsCsv != null && fieldsCsv.exists()) {
            readIntoMap(fieldsCsv, fieldMap);
        }

        if (methodsCsv != null && methodsCsv.exists()) {
            readIntoMap(methodsCsv, methodMap);
        }

        if (packagesCsv != null && packagesCsv.exists()) {
            // repackaged (FML)
            classPackageMap = new HashMap<String, String>();

            Map<String, String> packages = new HashMap<String, String>();
            readIntoMap(packagesCsv, packages);
            for (Map.Entry<String, String> entry : packages.entrySet()) {
                classPackageMap.put("net/minecraft/src/" + entry.getKey(), entry.getValue() + "/" + entry.getKey());
            }
        } else {
            // flat package (vanilla MCP)
            classPackageMap = null;
        }
    }

    private void readIntoMap(File file, Map<String, String> map) throws IOException {
        try (FileReader fileReader = new FileReader(file);
             CSVReader csvReader = new CSVReader(fileReader)) {

            String[] line;
            while ((line = csvReader.readNextSilently()) != null) {
                if (line.length == 0) {
                    continue;
                }
                Preconditions.checkArgument(line.length >= 2, "Invalid csv line: %s", (Object) line);
                map.put(line[0], line[1]);
            }
        }
    }

    @Override
    public String transformFieldName(String className, String fieldName) {
        String mapped = fieldMap.get(fieldName);
        return (mapped != null) ? mapped : fieldName;
    }

    @Override
    public String transformMethodName(String className, String methodName, String methodDescriptor) {
        String mapped = methodMap.get(methodName);
        return (mapped != null) ? mapped : methodName;
    }

    @Override
    public String transformClassName(String className) {
        if (classPackageMap == null) {
            return className;
        }

        String mapped = classPackageMap.get(className);
        return (mapped != null) ? JarRemapper.mapTypeName(className, null, classPackageMap, className) : className;
    }

    @Override
    public String transformMethodDescriptor(String oldDescriptor) {
        if (classPackageMap == null) {
            return oldDescriptor;
        }

        MethodDescriptor methodDescriptorTransformer = new MethodDescriptor(null, classPackageMap);
        return methodDescriptorTransformer.transform(oldDescriptor);
    }
}

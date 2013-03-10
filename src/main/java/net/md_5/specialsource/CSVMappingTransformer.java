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

import au.com.bytecode.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * For reading a .srg through MCP's fields.csv and methods.csv
 * Maps func_### and field_### in input srg to "descriptive" names
 */
public class CSVMappingTransformer extends JarMappingLoadTransformer {

    private final Map<String, String> fieldMap; // numeric srg name field_### -> descriptive csv name
    private final Map<String, String> methodMap; // numeric srg name func_### -> descriptive csv name
    private final Map<String, String> classPackageMap; // class src name -> repackaged full class name

    public CSVMappingTransformer(File fieldsCsv, File methodsCsv, File packagesCsv) throws IOException {
        fieldMap = new HashMap<String, String>();
        methodMap = new HashMap<String, String>();

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
                String classSimpleName = entry.getKey();
                String newPackageName = entry.getValue();

                classPackageMap.put("net/minecraft/src/" + classSimpleName, newPackageName + "/" + classSimpleName);
            }
        } else {
            // flat package (vanilla MCP)
            classPackageMap = null;
        }
    }

    private void readIntoMap(File file, Map<String, String> map) throws IOException {
        CSVReader csvReader = new CSVReader(new FileReader(file));
        String[] line;

        while ((line = csvReader.readNext()) != null) {
            if (line.length == 0) {
                continue;
            }

            if (line.length < 2) {
                throw new IllegalArgumentException("Invalid csv line: " + line);
            }

            String key = line[0];
            String value = line[1];

            map.put(key, value);
        }
    }

    @Override
    public String transformFieldName(String className, String fieldName) {
        return fieldMap.containsKey(fieldName) ? fieldMap.get(fieldName) : fieldName;
    }

    @Override
    public String transformMethodName(String className, String methodName, String methodDescriptor) {
        return methodMap.containsKey(methodName) ? methodMap.get(methodName) : methodName;
    }

    @Override
    public String transformClassName(String className) {
        if (classPackageMap == null) {
            return className;
        }

        String newPackage = classPackageMap.get(className);
        if (newPackage == null) {
            return className;
        }

        return JarRemapper.mapTypeName(className, null, classPackageMap, className);
    }

    @Override
    public String transformMethodDescriptor(String oldDescriptor) {
        if (classPackageMap == null) {
            return oldDescriptor;
        }

        MethodDescriptorTransformer methodDescriptorTransformer = new MethodDescriptorTransformer(null, classPackageMap);
        return methodDescriptorTransformer.transform(oldDescriptor);
    }
}

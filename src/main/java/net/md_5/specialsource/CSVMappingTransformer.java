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

    private final Map<String, String> fieldMap;
    private final Map<String, String> methodMap;

    public CSVMappingTransformer(File fieldsCsv, File methodsCsv) throws IOException {
        fieldMap = new HashMap<String, String>();
        methodMap = new HashMap<String, String>();

        readIntoMap(fieldsCsv, fieldMap);
        readIntoMap(methodsCsv, methodMap);
    }

    private void readIntoMap(File file, Map<String, String> map) throws IOException {
        CSVReader csvReader = new CSVReader(new FileReader(file));
        String[] line;

        while ((line = csvReader.readNext()) != null) {
            if (line.length == 0) {
                continue;
            }

            if (line.length < 4) {
                throw new IllegalArgumentException("Invalid csv line: " + line);
            }

            String numericName = line[0];
            String descriptiveName = line[1];
            //String side = line[2];
            //String javadoc = line[3];

            map.put(numericName, descriptiveName);
        }
    }

    @Override
    public String transformFieldName(String fieldName) {
        return fieldMap.get(fieldName);
    }

    @Override
    public String transformMethodName(String methodName) {
        return methodMap.get(methodName);
    }
}

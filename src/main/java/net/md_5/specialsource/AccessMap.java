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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AccessMap {

    private Map<String, AccessChange> map = new HashMap<String, AccessChange>();

    public AccessMap() {
    }

    public void loadAccessTransformer(BufferedReader reader) throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            // strip comments and trailing whitespace
            int n = line.indexOf('#');
            if (n != -1) {
                line = line.substring(0, n);
            }
            n = line.lastIndexOf(' ');
            if (n != -1) {
                line = line.substring(0, n);
            }
            if (line.isEmpty()){
                continue;
            }

            // _at.cfg format:
            // protected/public/private[+/-modifiers] symbol
            n = line.indexOf(' ');
            if (n == -1) {
                throw new IOException("loadAccessTransformer invalid line: " + line);
            }
            String accessString = line.substring(0, n);
            String symbolString = line.substring(n + 1);

            addAccessChange(symbolString, accessString);
        }
    }

    public void loadAccessTransformer(File file) throws IOException {
        loadAccessTransformer(new BufferedReader(new FileReader(file)));
    }

    public void loadAccessTransformer(String filename) throws IOException {
        loadAccessTransformer(URLDownloader.getLocalFile(filename));
    }

    /**
     * Convert a symbol name pattern from AT config to internal format
     */
    public static String convertSymbolPattern(String s) {
        // source name to internal name
        s = s.replace('.', '/');

        // method descriptor separated from name by a space
        if (s.indexOf('(') != -1) {
            s = s.replaceFirst("(?=[^ ])[(]", " (");
        }

        // now it matches the symbol name format used in the rest of SpecialSource
        // (but also possibly with wildcards)

        return s;
    }

    public void addAccessChange(String symbolString, String accessString) {
        addAccessChange(convertSymbolPattern(symbolString), new AccessChange(accessString));
    }

    public void addAccessChange(String key, AccessChange accessChange) {
        if (map.containsKey(key)) {
            System.out.println("INFO: merging AccessMap "+key+" from "+map.get(key)+" with "+accessChange);
            map.get(key).merge(accessChange);
        }
        map.put(key, accessChange);
    }

    public int applyClassAccess(String className, int access) {
        int old = access;

        access = apply(className, access);
        access = apply("*", access);

        //System.out.println("AT: class: "+className+" "+old+" -> "+access); // TODO: debug logging

        return access;
    }

    public int applyFieldAccess(String className, String fieldName, int access) {
        int old = access;

        access = apply("*/*", access);
        access = apply(className + "/*", access);
        access = apply(className + "/" + fieldName, access);

        //System.out.println("AT: field: "+className+"/"+fieldName+" "+old+" -> "+access);

        return access;
    }

    public int applyMethodAccess(String className, String methodName, String methodDesc,  int access) {
        int old = access;

        access = apply("*/* ()", access);
        access = apply(className + "/* ()", access);
        access = apply(className + "/" + methodName + " " + methodDesc, access);

        //System.out.println("AT: method: "+className+"/"+methodName+" "+methodDesc+" "+old+" -> "+access);

        return access;
    }

    private int apply(String key, int existing) {
        AccessChange change = map.get(key);
        if (change == null) {
            return existing;
        } else {
            return change.apply(existing);
        }
    }
}

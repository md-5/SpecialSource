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

import java.util.Map;

public class MethodDescriptorTransformer {
    private Map<String, String> packageMap;
    private Map<String, String> classMap;

    public MethodDescriptorTransformer(Map<String, String> packageMap, Map<String, String> classMap) {
        this.packageMap = packageMap;
        this.classMap = classMap;
    }

    public String transform(String input) {
        StringBuilder output = new StringBuilder();

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);

            switch (c) {
                // class
                case 'L':
                    String rest = input.substring(i);
                    int end = rest.indexOf(';');
                    if (end == -1) {
                        throw new IllegalArgumentException("Invalid method descriptor, found L but missing ;: " + input);
                    }
                    String className = rest.substring(1, end);
                    i += className.length() + 1;

                    String newClassName = JarRemapper.mapTypeName(className, packageMap, classMap);

                    output.append("L").append(newClassName).append(";");
                    break;

                // primitive type
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'V':
                case 'Z':

                // arguments
                case '(':
                case ')':

                // array
                case '[':
                    output.append(c);
                    break;

                case 'T':
                    throw new IllegalArgumentException("Method descriptors with type variables unsupported: " + c);
                case '<':
                    throw new IllegalArgumentException("Method descriptors with optional arguments unsupported: " + c);
                case '*':
                case '+':
                case '-':
                    throw new IllegalArgumentException("Method descriptors with wildcards unsupported: " + c);
                case '!':
                case '|':
                case 'Q':
                    throw new IllegalArgumentException("Method descriptors with advanced types unsupported: " + c);
                default:
                    throw new IllegalArgumentException("Unrecognized type in method descriptor: " + c);
            }

            i += 1;
        }

        return output.toString();
    }
}

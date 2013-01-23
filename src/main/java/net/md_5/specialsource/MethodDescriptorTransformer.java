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
        while(i < input.length()) {
            char c = input.charAt(i);

            switch(c)
            {
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

                    output.append("L" + newClassName + ";");
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
                    throw new IllegalArgumentException("Method descriptors with type variables unsupported: "+c);
                case '<':
                    throw new IllegalArgumentException("Method descriptors with optional arguments unsupported: "+c);
                case '*':
                case '+':
                case '-':
                    throw new IllegalArgumentException("Method descriptors with wildcards unsupported: "+c);
                case '!':
                case '|':
                case 'Q':
                    throw new IllegalArgumentException("Method descriptors with advanced types unsupported: "+c);
                default:
                    throw new IllegalArgumentException("Unrecognized type in method descriptor: " + c);
            }

            i += 1;
        }

        return output.toString();
    }
}

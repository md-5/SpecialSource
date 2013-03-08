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

import lombok.ToString;
import lombok.libs.org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents symbol access specifiers to be added or removed
 */
@ToString
public class AccessChange {

    private int clear; // bits to clear to 0
    private int set; // bits to set to 1 (overrides clear)

    private final static Map<String, Integer> accessCodes = new HashMap<String, Integer>();

    static {
        accessCodes.put("public", Opcodes.ACC_PUBLIC);
        accessCodes.put("private", Opcodes.ACC_PRIVATE);
        accessCodes.put("protected", Opcodes.ACC_PROTECTED);
        accessCodes.put("default", 0);
        accessCodes.put("", 0);
        accessCodes.put("package-private", 0);
        accessCodes.put("static", Opcodes.ACC_STATIC);
        accessCodes.put("final", Opcodes.ACC_FINAL);
        accessCodes.put("f", Opcodes.ACC_FINAL); // FML
        accessCodes.put("super", Opcodes.ACC_SUPER);
        accessCodes.put("synchronized", Opcodes.ACC_SYNCHRONIZED);
        accessCodes.put("volatile", Opcodes.ACC_VOLATILE);
        accessCodes.put("bridge", Opcodes.ACC_BRIDGE);
        accessCodes.put("varargs", Opcodes.ACC_VARARGS);
        accessCodes.put("transient", Opcodes.ACC_TRANSIENT);
        accessCodes.put("native", Opcodes.ACC_NATIVE);
        accessCodes.put("interface", Opcodes.ACC_INTERFACE);
        accessCodes.put("abstract", Opcodes.ACC_ABSTRACT);
        accessCodes.put("strict", Opcodes.ACC_STRICT);
        accessCodes.put("synthetic", Opcodes.ACC_SYNTHETIC);
        accessCodes.put("annotation", Opcodes.ACC_ANNOTATION);
        accessCodes.put("enum", Opcodes.ACC_ENUM);
        accessCodes.put("deprecated", Opcodes.ACC_DEPRECATED);
    }

    private final static int MASK_ALL_VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;

    public AccessChange(String s) {
        String[] parts = s.split("(?=[+-])"); // preserve delimiters
        if (parts.length < 1) {
            throw new IllegalArgumentException("Invalid access string: " + s);
        }

        // Symbol visibility
        clear = MASK_ALL_VISIBILITY; // always clear lower 3 bits, so visibility can be set below
        String visibilityString = parts[0];
        set = accessCodes.get(visibilityString);
        if (set > Opcodes.ACC_PROTECTED) {
            throw new IllegalArgumentException("Invalid access visibility: " + visibilityString);
        }

        // Modifiers
        for (int i = 1; i < parts.length; ++i) {
            if (parts[i].length() < 2) {
                throw new IllegalArgumentException("Invalid modifier length "+parts[i]+" in access string: " + s);
            }

            // Name
            char actionChar = parts[i].charAt(0);
            String modifierString = parts[i].substring(1);
            int modifier;

            if (!accessCodes.containsKey(modifierString)) {
                throw new IllegalArgumentException("Invalid modifier string "+modifierString+" in access string: " + s);
            }
            modifier = accessCodes.get(modifierString);

            // Toggle
            switch (actionChar) {
                case '+': set |= modifier; break;
                case '-': clear |= modifier; break;
                default: throw new IllegalArgumentException("Invalid action "+actionChar+" in access string: " + s);
            }
        }
    }

    public int apply(int access) {
        access &= ~clear;
        access |= set;

        return access;
    }

    /**
     * Combine this access change with another, setting/clearing bits from both
     */
    public void merge(AccessChange rhs) {
        clear |= rhs.clear;

        if ((rhs.set & MASK_ALL_VISIBILITY) != 0) {
            // visibility change - clear old visibility bits
            set &= ~MASK_ALL_VISIBILITY;
        }

        set |= rhs.set;
    }
}

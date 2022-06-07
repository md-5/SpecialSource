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

import java.util.Stack;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SignatureRemapper;
import org.objectweb.asm.signature.SignatureVisitor;

public abstract class CustomRemapper extends Remapper {

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        return mapMethodName(owner, name, desc, 0);
    }

    public String mapMethodName(String owner, String name, String desc, int access) {
        return name;
    }

    @Override
    public String mapRecordComponentName(String owner, String name, String desc) {
        return mapFieldName(owner, name, desc);
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        return mapFieldName(owner, name, desc, 0);
    }

    public String mapFieldName(String owner, String name, String desc, int access) {
        return name;
    }

    @Override
    public String mapSignature(String signature, boolean typeSignature) {
        // JDT decorates some lambdas with this and SignatureReader chokes on it
        if (signature != null && signature.contains("!*")) {
            return null;
        }
        return super.mapSignature(signature, typeSignature);
    }

    @Override
    protected SignatureVisitor createSignatureRemapper(SignatureVisitor v) {
        return new ProguardSignatureFixer(v, this);
    }

    /**
     * Proguard has a problem where it will sometimes incorrectly output a method signature.
     * It will put the fully qualified obf name for the inner instead of the inner name.
     * So here we try and detect and fix that.
     * Example:
     *   Bad:  (TK;)Lzt<TK;TT;TR;>.zt$a;
     *   Good: (TK;)Lzt<TK;TT;TR;>.a;
     */
    static class ProguardSignatureFixer extends SignatureRemapper {
        private final Stack<String> classNames = new Stack<String>();

        ProguardSignatureFixer(SignatureVisitor sv, Remapper m) {
            super(sv, m);
        }

        @Override
        public void visitClassType(String name) {
            classNames.push(name);
            super.visitClassType(name);
        }

        @Override
        public void visitInnerClassType(String name) {
            String outerClassName = classNames.pop();

            if (name.startsWith(outerClassName + '$')) {
                name = name.substring(outerClassName.length() + 1);
            }

            String className = outerClassName + '$' + name;
            classNames.push(className);
            super.visitInnerClassType(name);
        }

        @Override
        public void visitEnd() {
            classNames.pop();
            super.visitEnd();
        }
    }
}

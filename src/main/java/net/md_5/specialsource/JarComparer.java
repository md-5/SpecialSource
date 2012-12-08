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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class JarComparer extends ClassVisitor {

    private final MethodReferenceFinder methodVisitor = new MethodReferenceFinder();
    public final Jar jar;
    private String myName;
    public int iterDepth;
    public NoDupeList<String> classes = new NoDupeList<String>();
    public NoDupeList<Ownable> fields = new NoDupeList<Ownable>();
    public NoDupeList<Ownable> methods = new NoDupeList<Ownable>();

    private void visitType(Type type) {
        // FIXME: Scan arrays too!
        if (type.getSort() == Type.OBJECT) {
            String name = type.getInternalName();
            if (jar.containsClass(name)) {
                classes.add(name);
            }
        }
    }

    public JarComparer(Jar jar) {
        super(Opcodes.ASM4);
        this.jar = jar;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        myName = name;
        // FIXME: Scan the super class too!
        for (String implement : interfaces) {
            if (jar.containsClass(implement)) {
                classes.add(implement);
            }
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        Ownable field = new Ownable(NodeType.FIELD, myName, name, desc);
        fields.add(field);
        return null; // No need to get more info about a field!
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Check for initializers
        if (!name.equals("<init>") && !name.equals("<clinit>")) {
            Ownable method = new Ownable(NodeType.METHOD, myName, name, desc);
            methods.add(method);
        }
        // FIXME: Scan return types too!
        for (Type t : Type.getArgumentTypes(desc)) {
            visitType(t);
        }
        return methodVisitor;
    }

    private class MethodReferenceFinder extends MethodVisitor {

        public MethodReferenceFinder() {
            super(Opcodes.ASM4);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (jar.containsClass(owner)) {
                classes.add(owner);
            }
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof Type) {
                visitType((Type) cst);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (jar.containsClass(owner)) {
                classes.add(owner);
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (jar.containsClass(type)) {
                classes.add(type);
            }
        }
    }
}

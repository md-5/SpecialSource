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

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import net.md_5.specialsource.provider.InheritanceProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.repo.ClassRepo;
import net.md_5.specialsource.repo.JarRepo;
import net.md_5.specialsource.util.NoDupeList;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class JarComparer extends ClassVisitor {

    private final MethodReferenceFinder methodVisitor = new MethodReferenceFinder();
    public final Jar jar;
    private final ClassRepo jarRepo;
    private final InheritanceProvider inheritance;
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
        if (type.getSort() == Type.ARRAY) {
            visitType(type.getElementType());
        }
    }

    public JarComparer(Jar jar) {
        super(Opcodes.ASM9);
        this.jar = jar;
        this.jarRepo = new JarRepo(jar);
        this.inheritance = new JarProvider(jar);
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
        if (jar.containsClass(superName)) {
            classes.add(superName);
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        Ownable field = new Ownable(NodeType.FIELD, myName, name, desc, access);
        fields.add(field);
        return null; // No need to get more info about a field!
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        Ownable method = new Ownable(NodeType.METHOD, myName, name, desc, access);

        String newN = getDeclarer(myName, method);
        if (newN != null) {
            method = new Ownable(method.type, newN, method.name, method.descriptor, method.access);
        }
        methods.add(method);

        // FIXME: Scan return types too!
        for (Type t : Type.getArgumentTypes(desc)) {
            visitType(t);
        }
        visitType(Type.getReturnType(desc));
        return methodVisitor;
    }

    public String getDeclarer(String currentParent, Ownable node) {

        String newParent = null;

        ClassNode n = jarRepo.findClass(currentParent);
        if (n == null) {
            return newParent;
        }
        switch (node.type) {
            case FIELD:
                for (FieldNode field : n.fields) {
                    if (field.name.equals(node.name) && field.desc.equals(node.descriptor)) {
                        newParent = currentParent;
                        fields.remove(new Ownable(NodeType.FIELD, currentParent, node.name, node.descriptor, node.access));
                        break;
                    }
                }
                break;
            case METHOD:
                for (MethodNode method : n.methods) {
                    if (method.name.equals(node.name) && method.desc.equals(node.descriptor) && (method.access == -1 || (!Modifier.isPrivate(method.access) && !Modifier.isStatic(method.access)))) {
                        newParent = currentParent;
                        methods.remove(new Ownable(NodeType.METHOD, currentParent, node.name, node.descriptor, node.access));
                        methods.remove(node);
                        break;
                    }
                }
                break;
        }

        if ((node.owner.equals(newParent) || newParent == null) && (node.access == -1 || (!Modifier.isPrivate(node.access) && !Modifier.isStatic(node.access)))) {
            Collection<String> parents = inheritance.getParents(currentParent);

            if (parents != null) {
                // climb the inheritance tree
                for (String parent : parents) {
                    newParent = getDeclarer(parent, node);
                    if (newParent != null) {
                        return newParent;
                    }
                }
            }
        }

        return newParent;
    }

    private class MethodReferenceFinder extends MethodVisitor {

        public MethodReferenceFinder() {
            super(Opcodes.ASM9);
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
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
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

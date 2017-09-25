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

import java.util.Arrays;
import java.util.Collection;

import com.google.common.base.Preconditions;
import net.md_5.specialsource.repo.ClassRepo;
import net.md_5.specialsource.repo.RuntimeRepo;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class UnsortedRemappingMethodAdapter extends MethodRemapper {

    private static final Collection<Handle> META_FACTORIES = Arrays.asList(
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "altMetafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false)
    );

    protected final CustomRemapper remapper;
    private final ClassRepo classRepo;

    public UnsortedRemappingMethodAdapter(final MethodVisitor mv, final CustomRemapper remapper, ClassRepo classRepo) {
        super(mv, remapper);
        Preconditions.checkArgument(mv != null, "mv");
        this.remapper = remapper;
        this.classRepo = classRepo;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name,
            String desc) {
        mv.visitFieldInsn(opcode, remapper.mapType(owner),
                remapper.mapFieldName(owner, name, desc, findAccess(NodeType.FIELD, owner, name, desc)),
                remapper.mapDesc(desc));
    }

    private int findAccess(NodeType type, String owner, String name, String desc, ClassRepo repo) {
        int access = -1;
        if (repo != null) {
            ClassNode clazz = classRepo.findClass(owner);
            if (clazz != null) {
                switch (type) {
                    case FIELD:
                        for (FieldNode f : clazz.fields) {
                            if (f.name.equals(name) && f.desc.equals(desc)) {
                                access = f.access;
                                break;
                            }
                        }
                        break;
                    case METHOD:
                        for (MethodNode m : classRepo.findClass(owner).methods) {
                            if (m.name.equals(name) && m.desc.equals(desc)) {
                                access = m.access;
                                break;
                            }
                        }
                        break;
                }
            }
        }

        return access;
    }

    public int findAccess(NodeType type, String owner, String name, String desc) {
        int access;
        access = findAccess(type, owner, name, desc, classRepo);
        if (access == -1) {
            access = findAccess(type, owner, name, desc, RuntimeRepo.getInstance());
        }

        return access;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        mv.visitMethodInsn(opcode, remapper.mapType(owner),
                remapper.mapMethodName(owner, name, desc, findAccess(NodeType.METHOD, owner, name, desc)),
                remapper.mapMethodDesc(desc), itf);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {

        // Special case lambda metaFactory to get new name
        if (META_FACTORIES.contains(bsm)) {
            String owner = Type.getReturnType(desc).getInternalName();
            String odesc = ((Type) bsmArgs[0]).getDescriptor(); // First constant argument is "samMethodType - Signature and return type of method to be implemented by the function object."
            // index 2 is the signature, but with generic types. Should we use that instead?
            name = remapper.mapMethodName(owner, name, odesc, findAccess(NodeType.METHOD, owner, name, odesc));
        } else {
            name = remapper.mapInvokeDynamicMethodName(name, desc);
        }

        for (int i = 0; i < bsmArgs.length; i++) {
            bsmArgs[i] = remapper.mapValue(bsmArgs[i]);
        }

        mv.visitInvokeDynamicInsn(
                name,
                remapper.mapMethodDesc(desc), (Handle) remapper.mapValue(bsm),
                bsmArgs);
    }
}

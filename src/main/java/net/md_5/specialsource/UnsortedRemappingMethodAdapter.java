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
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.MethodRemapper;

public class UnsortedRemappingMethodAdapter extends MethodRemapper {

    private static final Collection<Handle> META_FACTORIES = Arrays.asList(
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "altMetafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false)
    );

    protected final RepoRemapper remapper;

    public UnsortedRemappingMethodAdapter(final MethodVisitor mv, final CustomRemapper remapper, ClassRepo classRepo) {
        super(mv, remapper);
        Preconditions.checkArgument(mv != null, "mv");
        this.remapper = new RepoRemapper(remapper, classRepo);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name,
            String desc) {
        mv.visitFieldInsn(opcode, remapper.mapType(owner),
                remapper.mapFieldName(owner, name, desc),
                remapper.mapDesc(desc));
    }

    @Deprecated
    public int findAccess(NodeType type, String owner, String name, String desc) {
        return remapper.findAccess(type, owner, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        mv.visitMethodInsn(opcode, remapper.mapType(owner),
                remapper.mapMethodName(owner, name, desc),
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
            name = remapper.mapMethodName(owner, name, odesc );
        } else {
            name = remapper.mapInvokeDynamicMethodName(name, desc);
        }

        if (bsm.getOwner().equals("java/lang/runtime/ObjectMethods")) {
            // TODO: consider asserting name (the parameter) equals hashCode, toString, or equals
            Type clazz = (Type) bsmArgs[0];

            // TODO: consider asserting (String)bsmArgs[1] == "step;feature"
            for (int i = 2; i < bsmArgs.length; i++) {
                Handle h = (Handle) bsmArgs[i];
                String newName = remapper.mapFieldName(clazz.getInternalName(), h.getName(), h.getDesc(), 0);
                bsmArgs[i] = new Handle(h.getTag(), h.getOwner(), newName, h.getDesc(), h.isInterface());
            }
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

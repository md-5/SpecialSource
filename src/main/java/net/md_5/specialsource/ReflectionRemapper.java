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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ReflectionRemapper {

    private JarMapping jarMapping;
    public boolean debug = false;

    public ReflectionRemapper(JarMapping jarMapping) {
        this.jarMapping = jarMapping;
    }

    @SuppressWarnings("unchecked")
    public byte[] remapClassFile(InputStream is) throws IOException {
        ClassReader cr = new ClassReader(is);
        ClassNode classNode = new ClassNode();
        ClassWriter cw = new ClassWriter(0);

        cr.accept(classNode, ClassReader.SKIP_DEBUG);

        for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {
            AbstractInsnNode insn = methodNode.instructions.getFirst();
            while (insn != null) {
                if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    remapGetDeclaredField(insn);
                }

                insn = insn.getNext();
            }
        }

        classNode.accept(cw);

        return cw.toByteArray();
    }

    private void remapGetDeclaredField(AbstractInsnNode insn) {
        MethodInsnNode mi = (MethodInsnNode) insn;

        if (!mi.owner.equals("java/lang/Class") || !mi.name.equals("getDeclaredField") || !mi.desc.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
            return;
        }

        log("ReflectionRemapper found getDeclaredField!");

        if (insn.getPrevious() == null || insn.getPrevious().getOpcode() != Opcodes.LDC) {
            log("- not constant field; skipping");
            return;
        }
        LdcInsnNode ldcField = (LdcInsnNode) insn.getPrevious();
        if (!(ldcField.cst instanceof String)) {
            log("- not field string; skipping: " + ldcField.cst);
            return;
        }
        String fieldName = (String) ldcField.cst;

        if (ldcField.getPrevious() == null || ldcField.getPrevious().getOpcode() != Opcodes.LDC) {
            log("- not constant class; skipping: field=" + ldcField.cst);
            return;
        }
        LdcInsnNode ldcClass = (LdcInsnNode) ldcField.getPrevious();
        if (!(ldcClass.cst instanceof Type)) {
            log("- not class type; skipping: field=" + ldcClass.cst + ", class=" + ldcClass.cst);
            return;
        }
        String className = ((Type) ldcClass.cst).getInternalName();

        String newName = jarMapping.tryClimb(jarMapping.fields, NodeType.FIELD, className, fieldName);
        log("Remapping "+className+"/"+fieldName + " -> " + newName);

        if (newName != null) {
            // Change the string literal to the correct name
            ldcField.cst = newName;
            //ldcClass.cst = className; // not remapped here - taken care of by JarRemapper
        }
    }

    private void log(String message) {
        if (debug) {
            System.out.println("[ReflectionRemapper] " + message);
        }
    }
}

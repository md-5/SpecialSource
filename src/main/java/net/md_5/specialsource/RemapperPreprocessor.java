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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * "Pre-process" a class file, intended to be used before remapping with JarRemapper.
 *
 * Currently includes:
 * - Extracting inheritance
 */

public class RemapperPreprocessor {

    public boolean debug = false;

    private InheritanceMap inheritanceMap;
    private JarMapping jarMapping;
    private AccessMap accessMap;

    /**
     *
     * @param inheritanceMap Map to add extracted inheritance information too, or null to not extract inheritance
     * @param jarMapping Mapping for reflection remapping, or null to not remap reflection
     * @throws IOException
     */
    public RemapperPreprocessor(InheritanceMap inheritanceMap, JarMapping jarMapping, AccessMap accessMap) {
        this.inheritanceMap = inheritanceMap;
        this.jarMapping = jarMapping;
        this.accessMap = accessMap;
    }

    public RemapperPreprocessor(InheritanceMap inheritanceMap, JarMapping jarMapping) {
        this(inheritanceMap, jarMapping, null);
    }

    public byte[] preprocess(InputStream inputStream) throws IOException {
        return preprocess(new ClassReader(inputStream));
    }

    public byte[] preprocess(byte[] bytecode) throws IOException {
        return preprocess(new ClassReader(bytecode));
    }

    @SuppressWarnings("unchecked")
    public byte[] preprocess(ClassReader classReader) {
        byte[] bytecode = null;
        ClassNode classNode = new ClassNode();
        int flags = ClassReader.SKIP_DEBUG;

        if (!isRewritingNeeded()) {
            // Not rewriting the class - skip the code, not needed
            flags |= ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES;
        }

        classReader.accept(classNode, flags);

        String className = classNode.name;

        // Inheritance extraction
        if (inheritanceMap != null) {
            logI("Loading plugin class inheritance for "+className);

            // Get inheritance
            ArrayList<String> parents = new ArrayList<String>();

            for (String iface : (List<String>) classNode.interfaces) {
                parents.add(iface);
            }
            parents.add(classNode.superName);

            inheritanceMap.setParents(className.replace('.', '/'), parents);

            logI("Inheritance added "+className+" parents "+parents.size());
        }

        if (isRewritingNeeded()) {
            // Class access
            if (accessMap != null) {
                classNode.access = accessMap.applyClassAccess(className, classNode.access);
                // TODO: inner classes?
            }

            // Field access
            if (accessMap != null) {
                for (FieldNode fieldNode : (List<FieldNode>) classNode.fields) {
                    fieldNode.access = accessMap.applyFieldAccess(className, fieldNode.name, fieldNode.access);
                }
            }

            for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {
                // Method access
                if (accessMap != null) {
                    methodNode.access = accessMap.applyMethodAccess(className, methodNode.name, methodNode.desc, methodNode.access);
                }

                // Reflection remapping
                if (jarMapping != null) {
                    AbstractInsnNode insn = methodNode.instructions.getFirst();
                    while (insn != null) {
                        if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            remapGetDeclaredField(insn);
                        }

                        insn = insn.getNext();
                    }
                }
            }

            ClassWriter cw = new ClassWriter(0);
            classNode.accept(cw);
            bytecode = cw.toByteArray();
        }

        return bytecode;
    }

    private boolean isRewritingNeeded() {
        return jarMapping != null || accessMap != null;
    }

    /**
     * Replace class.getDeclaredField("string") with a remapped field string
     * @param insn Method call instruction
     */
    private void remapGetDeclaredField(AbstractInsnNode insn) {
        MethodInsnNode mi = (MethodInsnNode) insn;

        if (!mi.owner.equals("java/lang/Class") || !mi.name.equals("getDeclaredField") || !mi.desc.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
            return;
        }

        logR("ReflectionRemapper found getDeclaredField!");

        if (insn.getPrevious() == null || insn.getPrevious().getOpcode() != Opcodes.LDC) {
            logR("- not constant field; skipping");
            return;
        }
        LdcInsnNode ldcField = (LdcInsnNode) insn.getPrevious();
        if (!(ldcField.cst instanceof String)) {
            logR("- not field string; skipping: " + ldcField.cst);
            return;
        }
        String fieldName = (String) ldcField.cst;

        if (ldcField.getPrevious() == null || ldcField.getPrevious().getOpcode() != Opcodes.LDC) {
            logR("- not constant class; skipping: field=" + ldcField.cst);
            return;
        }
        LdcInsnNode ldcClass = (LdcInsnNode) ldcField.getPrevious();
        if (!(ldcClass.cst instanceof Type)) {
            logR("- not class type; skipping: field=" + ldcClass.cst + ", class=" + ldcClass.cst);
            return;
        }
        String className = ((Type) ldcClass.cst).getInternalName();

        String newName = jarMapping.tryClimb(jarMapping.fields, NodeType.FIELD, className, fieldName);
        logR("Remapping "+className+"/"+fieldName + " -> " + newName);

        if (newName != null) {
            // Change the string literal to the correct name
            ldcField.cst = newName;
            //ldcClass.cst = className; // not remapped here - taken care of by JarRemapper
        }
    }

    private void logI(String message) {
        if (debug) {
            System.out.println("[Inheritance] " + message);
        }
    }

    private void logR(String message) {
        if (debug) {
            System.out.println("[ReflectionRemapper] " + message);
        }
    }
}

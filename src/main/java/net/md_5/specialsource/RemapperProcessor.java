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
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * "Pre-process" a class file, intended to be used before remapping with
 * JarRemapper.
 *
 * Currently includes:
 * - Extracting inheritance
 * - Applying access transformers
 * - Remapping reflected field string constants
 * - Remapping reflected class name string constants
 */
public class RemapperProcessor {

    public boolean debug = false;
    private InheritanceMap inheritanceMap;
    private JarMapping jarMapping;
    private AccessMap accessMap;
    private boolean remapReflectField;
    private boolean remapReflectClass;

    /**
     *
     * @param inheritanceMap Map to add extracted inheritance information too,
     * or null to not extract inheritance
     * @param jarMapping Mapping for reflection remapping, or null to not remap
     * reflection
     * @param accessMap Access transformer mappings, or null to not apply AT
     */
    public RemapperProcessor(InheritanceMap inheritanceMap, JarMapping jarMapping, AccessMap accessMap) {
        this.inheritanceMap = inheritanceMap;
        this.jarMapping = jarMapping;
        this.accessMap = accessMap;
        this.remapReflectField = true;
        this.remapReflectClass = false;
    }

    public RemapperProcessor(InheritanceMap inheritanceMap, JarMapping jarMapping) {
        this(inheritanceMap, jarMapping, null);
    }

    public byte[] process(InputStream inputStream) throws IOException {
        return process(new ClassReader(inputStream));
    }

    public byte[] process(byte[] bytecode) {
        return process(new ClassReader(bytecode));
    }

    /**
     * Enable or disable remapping reflection field string constants.
     * Requires a jarMapping, enabled by default if present.
     */
    public void setRemapReflectField(boolean b) {
        remapReflectField = b;
    }

    /**
     * Enable or disable remapping reflection class name string constants.
     * Requires a jarMapping.
     */
    public void setRemapReflectClass(boolean b) {
        remapReflectClass = b;
    }

    @SuppressWarnings("unchecked")
    public byte[] process(ClassReader classReader) {
        byte[] bytecode = null;
        ClassNode classNode = new ClassNode();
        int flags = 0;

        if (!isRewritingNeeded()) {
            // Not rewriting the class - skip the code, not needed
            flags |= ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG;
        }

        classReader.accept(classNode, flags);

        String className = classNode.name;

        // Inheritance extraction
        if (inheritanceMap != null) {
            logI("Loading plugin class inheritance for " + className);

            // Get inheritance
            ArrayList<String> parents = new ArrayList<String>();

            for (String iface : classNode.interfaces) {
                parents.add(iface);
            }
            parents.add(classNode.superName);

            inheritanceMap.setParents(className.replace('.', '/'), parents);

            logI("Inheritance added " + className + " parents " + parents.size());
        }

        if (isRewritingNeeded()) {
            // Class access
            if (accessMap != null) {
                classNode.access = accessMap.applyClassAccess(className, classNode.access);
                for (InnerClassNode inner : classNode.innerClasses) {
                    inner.access = accessMap.applyClassAccess(inner.name, inner.access);
                }
            }

            // Field access
            if (accessMap != null) {
                for (FieldNode fieldNode : classNode.fields) {
                    fieldNode.access = accessMap.applyFieldAccess(className, fieldNode.name, fieldNode.access);
                }
            }

            for (MethodNode methodNode : classNode.methods) {
                // Method access
                if (accessMap != null) {
                    methodNode.access = accessMap.applyMethodAccess(className, methodNode.name, methodNode.desc, methodNode.access);
                }

                // Reflection remapping
                if (jarMapping != null) {
                    AbstractInsnNode insn = methodNode.instructions.getFirst();
                    while (insn != null) {
                        switch (insn.getOpcode())
                        {
                            case Opcodes.INVOKEVIRTUAL:
                                remapGetDeclaredField(insn);
                                break;

                            case Opcodes.INVOKESTATIC:
                                remapClassForName(insn);
                                break;
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
     *
     * @param insn Method call instruction
     */
    private void remapGetDeclaredField(AbstractInsnNode insn) {
        if (!this.remapReflectField) {
            return;
        }

        MethodInsnNode mi = (MethodInsnNode) insn;

        if (!mi.owner.equals("java/lang/Class") || !mi.name.equals("getDeclaredField") || !mi.desc.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
            return;
        }

        logR("ReflectionRemapper found getDeclaredField!");

        if (insn.getPrevious() == null || insn.getPrevious().getOpcode() != Opcodes.LDC) {
            logR("- not constant field; skipping, prev=" + insn.getPrevious());
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

        String newName = jarMapping.tryClimb(jarMapping.fields, NodeType.FIELD, className, fieldName, null, 0);
        logR("Remapping " + className + "/" + fieldName + " -> " + newName);

        if (newName != null) {
            // Change the string literal to the correct name
            ldcField.cst = newName;
            //ldcClass.cst = className; // not remapped here - taken care of by JarRemapper
        }
    }


     /**
     * Replace Class.forName("string") with a remapped field string
     *
     * @param insn Method call instruction
     */
     private void remapClassForName(AbstractInsnNode insn) {
         if (!this.remapReflectClass) {
             return;
         }

         MethodInsnNode mi = (MethodInsnNode) insn;

         if (!mi.owner.equals("java/lang/Class") || !mi.name.equals("forName") || !mi.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
             return;
         }

         logR("ReflectionRemapped found Class forName!");

         // TODO: refactor with remapGetDeclaredField()
         if (insn.getPrevious() == null || insn.getPrevious().getOpcode() != Opcodes.LDC) {
             logR("- not constant field; skipping, prev=" + insn.getPrevious());
             return;
         }
         LdcInsnNode ldcClassName = (LdcInsnNode) insn.getPrevious();
         if (!(ldcClassName.cst instanceof String)) {
             logR("- not field string; skipping: " + ldcClassName.cst);
             return;
         }
         String className = (String) ldcClassName.cst;

         String newName = jarMapping.classes.get(className.replace('.', '/')); // TODO: ToInternalName
         logR("Remapping " + className + " -> " + newName);

         if (newName != null) {
             // Change the string literal to the correct name
             ldcClassName.cst = newName.replace('/', '.');
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

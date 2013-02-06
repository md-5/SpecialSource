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

        System.out.println("methodnode "+mi.owner+" "+mi.name+" "+mi.desc);
        if (!mi.owner.equals("java/lang/Class") || !mi.name.equals("getDeclaredField") || !mi.desc.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
            return;
        }

        System.out.println("found getDeclaredField!");

        if (insn.getPrevious() == null || insn.getPrevious().getOpcode() != Opcodes.LDC) {
            System.out.println("- not constant field; skipping");
            return;
        }
        LdcInsnNode ldcField = (LdcInsnNode) insn.getPrevious();
        System.out.println("field constant = "+ldcField.cst);
        if (!(ldcField.cst instanceof String)) {
            System.out.println("- not field string; skipping");
            return;
        }
        String fieldName = (String) ldcField.cst;

        if (ldcField.getPrevious() == null || ldcField.getPrevious().getOpcode() != Opcodes.LDC) {
            System.out.println("- not constant class; skipping");
            return;
        }
        LdcInsnNode ldcClass = (LdcInsnNode) ldcField.getPrevious();
        if (!(ldcClass.cst instanceof Type)) {
            System.out.println("- not class type; skipping");
            return;
        }
        System.out.println("class = "+ldcClass.cst);
        String className = ((Type) ldcClass.cst).getInternalName();

        System.out.println("Remapping "+className+"/"+fieldName);

        String newName = jarMapping.tryClimb(jarMapping.fields, NodeType.FIELD, className, fieldName);
        if (newName != null) {
            // Change the string literal to the correct name
            ldcField.cst = newName;
            //ldcClass.cst = className; // not remapped here - taken care of by JarRemapper
            System.out.println(" -> to "+newName);
        }
    }
}

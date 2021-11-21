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

/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
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
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.common.base.Preconditions;
import lombok.Setter;
import net.md_5.specialsource.repo.ClassRepo;
import net.md_5.specialsource.writer.LogWriter;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.FieldRemapper;

public class RemappingClassAdapter extends ClassRemapper {

    protected final CustomRemapper remapper;
    protected ClassRepo repo;
    @Setter
    protected LogWriter logWriter;

    public RemappingClassAdapter(final ClassVisitor cv, final CustomRemapper remapper, ClassRepo repo) {
        super(cv, remapper);
        Preconditions.checkArgument(cv != null, "cv");

        this.repo = repo;
        this.remapper = remapper;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.className = name;

        String newName = remapper.mapType(name);
        cv.visit(version,
                access,
                newName,
                remapper.mapSignature(signature, false),
                remapper.mapType(superName),
                interfaces == null ? null : remapper.mapTypes(interfaces));

        if (logWriter != null) {
            logWriter.addClassMap(className, newName);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {
        String newName = remapper.mapMethodName(className, name, desc, access);
        String newDesc = remapper.mapMethodDesc(desc);
        MethodVisitor mv = cv.visitMethod(access, newName, newDesc, remapper.mapSignature(
                signature, false),
                exceptions == null ? null : remapper.mapTypes(exceptions));
        return mv == null ? null : createMethodRemapper(mv, desc, name, newName);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc,
            String signature, Object value) {
        String newName = remapper.mapFieldName(className, name, desc, access);
        FieldVisitor fv = cv.visitField(access,
                newName,
                remapper.mapDesc(desc), remapper.mapSignature(signature, true),
                remapper.mapValue(value));

        if (logWriter != null) {
            logWriter.addFieldMap(desc, name, newName);
        }

        return fv == null ? null : createFieldRemapper(fv);
    }

    @Override
    public void visitInnerClass(String name, String outerName,
            String innerName, int access) {
        String newName = remapper.mapType(name);
        cv.visitInnerClass(newName,
            outerName == null ? null : remapper.mapType(outerName),
            innerName == null ? null : newName.substring(newName.lastIndexOf(newName.contains("$") ? '$' : '/') + 1),
            access);
    }

    @Override
    protected FieldVisitor createFieldRemapper(FieldVisitor fv) {
        return new FieldRemapper(fv, remapper) {
            @Override
            public void visitAttribute(Attribute attr) {
                if (SpecialSource.kill_lvt && attr.type.equals("LocalVariableTable")) {
                    return;
                }
                if (SpecialSource.kill_generics && attr.type.equals("LocalVariableTypeTable")) {
                    return;
                }

                super.visitAttribute(attr);
            }
        };
    }

    @Override
    protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
        throw new UnsupportedOperationException("Not implemented");
    }

    protected MethodVisitor createMethodRemapper(MethodVisitor mv, final String oldDesc, final String oldName, final String newName) {
        return new UnsortedRemappingMethodAdapter(mv, remapper, repo) {
            @Override
            public void visitAttribute(Attribute attr) {
                if (SpecialSource.kill_lvt && attr.type.equals("LocalVariableTable")) {
                    return;
                }
                if (SpecialSource.kill_generics && attr.type.equals("LocalVariableTypeTable")) {
                    return;
                }

                super.visitAttribute(attr);
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                if (!SpecialSource.kill_lvt) {
                    super.visitLocalVariable(name, desc, signature, start, end, index);
                }
            }

            private int startLine = Integer.MAX_VALUE;
            private int endLine = Integer.MIN_VALUE;

            @Override
            public void visitLineNumber(int line, Label start) {
                startLine = Math.min(startLine, line);
                endLine = Math.max(endLine, line);

                super.visitLineNumber(line, start);
            }

            @Override
            public void visitEnd() {
                if (logWriter != null) {
                    logWriter.addMethodMap(startLine, endLine, oldDesc, oldName, newName);
                }

                super.visitEnd();
            }
        };
    }

    @Override
    public void visitSource(String source, String debug) {
        if (!SpecialSource.kill_source) {
            super.visitSource(source, debug);
        }
    }

    @Override
    public void visitAttribute(Attribute attr) {
        if (SpecialSource.kill_generics && attr.type.equals("Signature")) {
            return;
        }

        super.visitAttribute(attr);
    }
}

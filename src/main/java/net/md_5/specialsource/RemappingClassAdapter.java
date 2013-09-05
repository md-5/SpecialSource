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

import net.md_5.specialsource.repo.ClassRepo;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingAnnotationAdapter;

/**
 * A {@link ClassVisitor} for type remapping.
 * 
 * @author Eugene Kuleshov
 */
public class RemappingClassAdapter extends ClassVisitor {

    protected final Remapper remapper;
    protected ClassRepo repo;
    protected String className;
    
    public RemappingClassAdapter(final ClassVisitor cv, final Remapper remapper, ClassRepo repo) {
        this(Opcodes.ASM4, cv, remapper);
        this.repo = repo;
    }

    protected RemappingClassAdapter(final int api, final ClassVisitor cv, final Remapper remapper) {
        super(api, cv);
        this.remapper = remapper;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, remapper.mapType(name), remapper
                .mapSignature(signature, false), remapper.mapType(superName),
                interfaces == null ? null : remapper.mapTypes(interfaces));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av;
        av = super.visitAnnotation(remapper.mapDesc(desc), visible);
        return av == null ? null : createRemappingAnnotationAdapter(av);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {
        String newDesc = remapper.mapMethodDesc(desc);
        MethodVisitor mv = super.visitMethod(access, remapper.mapMethodName(
                className, name, desc, access), newDesc, remapper.mapSignature(
                signature, false),
                exceptions == null ? null : remapper.mapTypes(exceptions));
        return mv == null ? null : createRemappingMethodAdapter(access,
                newDesc, mv);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc,
            String signature, Object value) {
        FieldVisitor fv = super.visitField(access,
                remapper.mapFieldName(className, name, desc, access),
                remapper.mapDesc(desc), remapper.mapSignature(signature, true),
                remapper.mapValue(value));
        return fv == null ? null : createRemappingFieldAdapter(fv);
    }

    @Override
    public void visitInnerClass(String name, String outerName,
            String innerName, int access) {
        // TODO should innerName be changed?
        super.visitInnerClass(remapper.mapType(name), outerName == null ? null
                : remapper.mapType(outerName), innerName, access);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        super.visitOuterClass(remapper.mapType(owner), name == null ? null
                : remapper.mapMethodName(owner, name, desc),
                desc == null ? null : remapper.mapMethodDesc(desc));
    }
    
    protected FieldVisitor createRemappingFieldAdapter(FieldVisitor sup) {
        return new FieldVisitor(Opcodes.ASM4, sup) {
            @Override
            public void visitAttribute(Attribute attr) {
                if (SpecialSource.kill_lvt && attr.type.equals("LocalVariableTable")) {
                    return;
                }
                if (SpecialSource.kill_generics && attr.type.equals("LocalVariableTypeTable")) {
                    return;
                }
                if (fv != null) {
                    fv.visitAttribute(attr);
                }
            }
        };
    }

    protected MethodVisitor createRemappingMethodAdapter(int access, String newDesc, MethodVisitor sup) {
        MethodVisitor remap = new UnsortedRemappingMethodAdapter(access, newDesc, sup, remapper, repo);
        return new MethodVisitor(Opcodes.ASM4, remap) {
            @Override
            public void visitAttribute(Attribute attr) {
                if (SpecialSource.kill_lvt && attr.type.equals("LocalVariableTable")) {
                    return;
                }
                if (SpecialSource.kill_generics && attr.type.equals("LocalVariableTypeTable")) {
                    return;
                }
                if (mv != null) {
                    mv.visitAttribute(attr);
                }
            }
        };
    }

    protected AnnotationVisitor createRemappingAnnotationAdapter(
            AnnotationVisitor av) {
        return new RemappingAnnotationAdapter(av, remapper);
    }
    
    @Override
    public void visitSource(String source, String debug) {
        if (!SpecialSource.kill_source && cv != null) {
            cv.visitSource(source, debug);
        }
    }

    @Override
    public void visitAttribute(Attribute attr) {
        if (SpecialSource.kill_generics && attr.type.equals("Signature")) {
            return;
        }
        if (cv != null) {
            cv.visitAttribute(attr);
        }
    }
    
}

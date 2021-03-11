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

import net.md_5.specialsource.repo.ClassRepo;
import net.md_5.specialsource.repo.RuntimeRepo;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Wraps a {@link CustomRemapper} with "true" access lookup.
 */
public class RepoRemapper extends CustomRemapper {
    private final CustomRemapper remapper;
    private final ClassRepo classRepo;

    public RepoRemapper(CustomRemapper remapper, ClassRepo classRepo) {
        this.remapper = remapper;
        this.classRepo = classRepo;
    }

    @Override
    public String map(String typeName) {
        return remapper.map(typeName);
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        return mapMethodName(owner, name, desc, findAccess(NodeType.METHOD, owner, name, desc));
    }

    @Override
    public String mapMethodName(String owner, String name, String desc, int access) {
        return remapper.mapMethodName(owner, name, desc, access);
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        return mapFieldName(owner, name, desc, findAccess(NodeType.FIELD, owner, name, desc));
    }

    @Override
    public String mapFieldName(String owner, String name, String desc, int access) {
        return remapper.mapFieldName(owner, name, desc, access);
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
}

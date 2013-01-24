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

import java.util.List;
import java.util.ArrayList;

/**
 * Lookup class inheritance from classes loaded at runtime.
 */
public class RuntimeInheritanceProvider implements IInheritanceProvider {

    // TODO: option to transform through a jarRemapper at runtime
    @Override
    public List<String> getParents(String internalClassName) {
        List<String> parents = new ArrayList<String>();
        String sourceClassName = toSourceName(internalClassName);
        Class clazz;
        try {
            clazz = ClassLoader.getSystemClassLoader().loadClass(sourceClassName); // load class without initializing
            //clazz = Class.forName(toSourceName(sourceClassName)); // runs static initializers - avoid!
        } catch (Throwable t) {
            SpecialSource.log("RuntimeInheritanceProvider failed: " + t);
            return null;
        }

        for (Class iface : clazz.getInterfaces()) {
            parents.add(toInternalName(iface.getName()));
        }

        Class superClass = clazz.getSuperclass();
        if (superClass != null) {
            parents.add(toInternalName(superClass.getName()));
        }

        return parents;
    }

    // Convert class name from internal name to source name
    public String toSourceName(String className) {
        return className.replace('/', '.');
    }

    //  .. and vice versa
    public String toInternalName(String className) {
        return className.replace('.', '/');
    }
}

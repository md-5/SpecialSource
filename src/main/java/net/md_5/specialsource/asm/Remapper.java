package net.md_5.specialsource.asm;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

public abstract class Remapper {

    public String mapDesc(String desc) {
        Type t = Type.getType(desc);
        switch (t.getSort()) {
            case Type.ARRAY:
                String s = mapDesc(t.getElementType().getDescriptor());
                for (int i = 0; i < t.getDimensions(); ++i) {
                    s = '[' + s;
                }
                return s;
            case Type.OBJECT:
                String newType = map(t.getInternalName());
                if (newType != null) {
                    return 'L' + newType + ';';
                }
        }
        return desc;
    }

    private Type mapType(Type t) {
        switch (t.getSort()) {
            case Type.ARRAY:
                String s = mapDesc(t.getElementType().getDescriptor());
                for (int i = 0; i < t.getDimensions(); ++i) {
                    s = '[' + s;
                }
                return Type.getType(s);
            case Type.OBJECT:
                s = map(t.getInternalName());
                return s != null ? Type.getObjectType(s) : t;
            case Type.METHOD:
                return Type.getMethodType(mapMethodDesc(t.getDescriptor()));
        }
        return t;
    }

    public String mapType(String type) {
        if (type == null) {
            return null;
        }
        return mapType(Type.getObjectType(type)).getInternalName();
    }

    public String[] mapTypes(String[] types) {
        String[] newTypes = null;
        boolean needMapping = false;
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            String newType = map(type);
            if (newType != null && newTypes == null) {
                newTypes = new String[types.length];
                if (i > 0) {
                    System.arraycopy(types, 0, newTypes, 0, i);
                }
                needMapping = true;
            }
            if (needMapping) {
                newTypes[i] = newType == null ? type : newType;
            }
        }
        return needMapping ? newTypes : types;
    }

    public String mapMethodDesc(String desc) {
        if ("()V".equals(desc)) {
            return desc;
        }

        Type[] args = Type.getArgumentTypes(desc);
        StringBuffer s = new StringBuffer("(");
        for (int i = 0; i < args.length; i++) {
            s.append(mapDesc(args[i].getDescriptor()));
        }
        Type returnType = Type.getReturnType(desc);
        if (returnType == Type.VOID_TYPE) {
            s.append(")V");
            return s.toString();
        }
        s.append(')').append(mapDesc(returnType.getDescriptor()));
        return s.toString();
    }

    public Object mapValue(Object value) {
        if (value instanceof Type) {
            return mapType((Type) value);
        }
        if (value instanceof Handle) {
            Handle h = (Handle) value;
            return new Handle(h.getTag(), mapType(h.getOwner()), mapMethodName(
                    h.getOwner(), h.getName(), h.getDesc()),
                    mapMethodDesc(h.getDesc()));
        }
        return value;
    }

    /**
     *
     * @param typeSignature true if signature is a FieldTypeSignature, such as
     * the signature parameter of the ClassVisitor.visitField or
     * MethodVisitor.visitLocalVariable methods
     */
    public String mapSignature(String signature, boolean typeSignature) {
        if (signature == null) {
            return null;
        }
        SignatureReader r = new SignatureReader(signature);
        SignatureWriter w = new SignatureWriter();
        SignatureVisitor a = createRemappingSignatureAdapter(w);
        if (typeSignature) {
            r.acceptType(a);
        } else {
            r.accept(a);
        }
        return w.toString();
    }

    protected SignatureVisitor createRemappingSignatureAdapter(SignatureVisitor v) {
        return new SignatureRemapper(v, this);
    }

    /**
     * Map method name to the new name. Subclasses can override.
     *
     * @param owner owner of the method.
     * @param name name of the method.
     * @param desc descriptor of the method.
     * @return new name of the method
     */
    public String mapMethodName(String owner, String name, String desc) {
        return name;
    }

    /**
     * Map invokedynamic method name to the new name. Subclasses can override.
     *
     * @param name name of the invokedynamic.
     * @param desc descriptor of the invokedynamic.
     * @return new invokdynamic name.
     */
    public String mapInvokeDynamicMethodName(String name, String desc) {
        return name;
    }

    /**
     * Map field name to the new name. Subclasses can override.
     *
     * @param owner owner of the field.
     * @param name name of the field
     * @param desc descriptor of the field
     * @return new name of the field.
     */
    public String mapFieldName(String owner, String name, String desc) {
        return name;
    }

    /**
     * Map type name to the new name. Subclasses can override.
     */
    public String map(String typeName) {
        return typeName;
    }
}

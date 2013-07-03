package net.md_5.specialsource.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

public class SignatureRemapper extends SignatureVisitor {

    private final SignatureVisitor v;
    private final Remapper remapper;
    private String className;

    protected SignatureRemapper(SignatureVisitor v, Remapper remapper) {
        super(Opcodes.ASM4);
        this.v = v;
        this.remapper = remapper;
    }

    @Override
    public void visitClassType(String name) {
        className = name;
        v.visitClassType(remapper.mapType(name));
    }

    @Override
    public void visitInnerClassType(String name) {
        className = className + '$' + name;
        String remappedName = remapper.mapType(className);
        v.visitInnerClassType(remappedName.substring(remappedName.lastIndexOf('$') + 1));
    }

    @Override
    public void visitFormalTypeParameter(String name) {
        v.visitFormalTypeParameter(name);
    }

    @Override
    public void visitTypeVariable(String name) {
        v.visitTypeVariable(name);
    }

    @Override
    public SignatureVisitor visitArrayType() {
        v.visitArrayType();
        return this;
    }

    @Override
    public void visitBaseType(char descriptor) {
        v.visitBaseType(descriptor);
    }

    @Override
    public SignatureVisitor visitClassBound() {
        v.visitClassBound();
        return this;
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        v.visitExceptionType();
        return this;
    }

    @Override
    public SignatureVisitor visitInterface() {
        v.visitInterface();
        return this;
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        v.visitInterfaceBound();
        return this;
    }

    @Override
    public SignatureVisitor visitParameterType() {
        v.visitParameterType();
        return this;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        v.visitReturnType();
        return this;
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        v.visitSuperclass();
        return this;
    }

    @Override
    public void visitTypeArgument() {
        v.visitTypeArgument();
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
        v.visitTypeArgument(wildcard);
        return this;
    }

    @Override
    public void visitEnd() {
        v.visitEnd();
    }
}

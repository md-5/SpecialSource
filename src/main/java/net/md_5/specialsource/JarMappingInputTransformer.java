package net.md_5.specialsource;

/**
 * Transform mapping files while reading as input
 */
public interface JarMappingInputTransformer {

    public String transformClassName(String className);

    public String transformMethodDescriptor(String oldDescriptor);
}

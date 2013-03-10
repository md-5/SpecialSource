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

import java.io.*;
import java.util.*;

public class JarMapping {

    public final LinkedHashMap<String, String> packages = new LinkedHashMap<String, String>();
    public final Map<String, String> classes = new HashMap<String, String>();
    public final Map<String, String> fields = new HashMap<String, String>();
    public final Map<String, String> methods = new HashMap<String, String>();

    private InheritanceMap inheritanceMap = new InheritanceMap();
    private IInheritanceProvider fallbackInheritanceProvider = null;

    public JarMapping() {
    }

    /**
     * Set the inheritance map used for caching superclass/interfaces. This call be omitted to
     * use a local cache, or set to your own global cache.
     */
    public void setInheritanceMap(InheritanceMap inheritanceMap) {
        this.inheritanceMap = inheritanceMap;
    }

    /**
     * Set the inheritance provider to be consulted if the inheritance map has no information on
     * the requested class (results will be cached in the inheritance map).
     */
    public void setFallbackInheritanceProvider(IInheritanceProvider fallbackInheritanceProvider) {
        this.fallbackInheritanceProvider = fallbackInheritanceProvider;
    }

    public String tryClimb(Map<String, String> map, NodeType type, String owner, String name) {
        String key = owner + "/" + name;

        String mapped = map.get(key);
        if (mapped == null) {
            List<String> parents = null;

            if (inheritanceMap.hasParents(owner)) {
                parents = inheritanceMap.getParents(owner);
            } else if (fallbackInheritanceProvider != null) {
                parents = fallbackInheritanceProvider.getParents(owner);
                inheritanceMap.setParents(owner, (ArrayList<String>) parents);
            }

            if (parents != null) {
                // climb the inheritance tree
                for (String parent : parents) {
                    mapped = tryClimb(map, type, parent, name);
                    if (mapped != null) {
                        return mapped;
                    }
                }
            }
        }
        return mapped;
    }

    /**
     * Load mappings from an MCP directory
     *
     * @param dirname MCP directory name, local file or remote URL ending in '/'
     * @param reverse If true, swap input and output
     * @param ignoreCsv If true, ignore fields.csv and methods.csv (but not packages.csv)
     * @param numericSrgNames If true, load numeric "srg" names (num->mcp instead of obf->mcp)
     */
    private void loadMappingsDir(String dirname, boolean reverse, boolean ignoreCsv, boolean numericSrgNames) throws IOException {
        File dir = new File(dirname);
        if (!URLDownloader.isHTTPURL(dirname) && !dir.isDirectory()) {
            throw new IllegalArgumentException("loadMappingsDir("+dir+"): not a directory");
        }

        String sep = System.getProperty("file.separator");

        List<File> srgFiles = new ArrayList<File>();

        File joinedSrg = URLDownloader.getLocalFile(dirname + sep + "joined.srg");
        if (joinedSrg.exists()) {
            // FML/MCP client/server joined
            srgFiles.add(joinedSrg);
        } else {
            // vanilla MCP separated sides
            File serverSrg = URLDownloader.getLocalFile(dirname + sep + "server.srg");
            File clientSrg = URLDownloader.getLocalFile(dirname + sep + "client.srg");
            if (serverSrg.exists()) {
                srgFiles.add(serverSrg);
            }
            if (clientSrg.exists()) {
                srgFiles.add(clientSrg);
            }
        }

        if (srgFiles.size() == 0) {
            throw new IOException("loadMappingsDir("+dirname+"): no joined.srg, client.srg, or client.srg found");
        }

        // Read output names through csv mappings, if available & enabled
        File fieldsCsv = URLDownloader.getLocalFile(dirname + sep + "fields.csv");
        File methodsCsv = URLDownloader.getLocalFile(dirname + sep + "methods.csv");
        File packagesCsv = URLDownloader.getLocalFile(dirname + sep + "packages.csv"); // FML repackaging, optional

        CSVMappingTransformer outputTransformer;
        JarMappingLoadTransformer inputTransformer;

        if (numericSrgNames) {
            // Wants numeric "srg" names -> descriptive "csv" names. To accomplish this:
            // 1. load obf->mcp (descriptive "csv") as chainMappings
            // 2. load again but chaining input (obf) through mcp, and ignoring csv on output
            // 3. result: mcp->srg, similar to MCP ./reobfuscate --srgnames
            JarMapping chainMappings = new JarMapping();
            chainMappings.loadMappingsDir(dirname, reverse, false/*ignoreCsv*/, false/*numeric*/);
            inputTransformer = new ChainTransformer(new JarRemapper(chainMappings));
            ignoreCsv = true; // keep numeric srg as output
        } else {
            inputTransformer = null;
        }

        if (fieldsCsv.exists() && methodsCsv.exists()) {
            outputTransformer = new CSVMappingTransformer(ignoreCsv ? null : fieldsCsv, ignoreCsv ? null : methodsCsv, packagesCsv);
        } else {
            outputTransformer = null;
        }

       for (File srg : srgFiles) {
            loadMappings(new BufferedReader(new FileReader(srg)), inputTransformer, outputTransformer, reverse);
        }
    }

    @Deprecated
    public void loadMappingsDir(String dirname, boolean reverse, boolean ignoreCsv) throws IOException {
        loadMappingsDir(dirname, reverse, ignoreCsv, false);
    }

    public void loadMappings(File file) throws IOException {
        loadMappings(new BufferedReader(new FileReader(file)), null, null, false);
    }

    /**
     *
     * @param filename A filename of a .srg/.csrg or an MCP directory of .srg+.csv, local or remote
     * @param reverse Swap input and output mappings
     * @param numericSrgNames When reading mapping directory, load numeric "srg" instead obfuscated names
     * @param inShadeRelocation Apply relocation on mapping input
     * @param outShadeRelocation Apply relocation on mapping output
     * @throws IOException
     */
    public void loadMappings(String filename, boolean reverse, boolean numericSrgNames, String inShadeRelocation, String outShadeRelocation) throws IOException {
        // Optional shade relocation, on input or output names
        JarMappingLoadTransformer inputTransformer = null;
        JarMappingLoadTransformer outputTransformer = null;

        if (inShadeRelocation != null) {
            inputTransformer = new ShadeRelocationSimulator(inShadeRelocation);
        }

        if (outShadeRelocation != null) {
            outputTransformer = new ShadeRelocationSimulator(outShadeRelocation);
        }

        if (new File(filename).isDirectory() || filename.endsWith("/")) {
            // Existing local dir or dir URL

            if (inputTransformer != null || outputTransformer != null) {
                throw new IllegalArgumentException("loadMappings("+filename+"): shade relocation not supported on directories"); // yet
            }

            loadMappingsDir(filename, reverse, false, numericSrgNames);
        } else {
            // File

            if (numericSrgNames) {
                throw new IllegalArgumentException("loadMappings("+filename+"): numeric only supported on directories, not files");
            }

            loadMappings(new BufferedReader(new FileReader(URLDownloader.getLocalFile(filename))), inputTransformer, outputTransformer, reverse);
        }
    }

    /**
     * Load a mapping given a .csrg file
     *
     * @param reader Mapping file reader
     * @param inputTransformer Transformation to apply on input
     * @param outputTransformer Transformation to apply on output
     * @param reverse Swap input and output mappings (after applying any input/output transformations)
     * @throws IOException
     */
    public void loadMappings(BufferedReader reader, JarMappingLoadTransformer inputTransformer, JarMappingLoadTransformer outputTransformer, boolean reverse) throws IOException {
        if (inputTransformer == null) {
            inputTransformer = ShadeRelocationSimulator.IDENTITY;
        }
        if (outputTransformer == null) {
            outputTransformer = ShadeRelocationSimulator.IDENTITY;
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty()){
                continue;
            }

            if (line.contains(":")) {
                // standard srg
                parseSrgLine(line, inputTransformer, outputTransformer, reverse);
            } else {
                // better 'compact' srg format
                parseCsrgLine(line, inputTransformer, outputTransformer, reverse);
            }
        }
    }

    /**
     * Parse a 'csrg' mapping format line and populate the data structures
     */
    private void parseCsrgLine(String line, JarMappingLoadTransformer inputTransformer, JarMappingLoadTransformer outputTransformer, boolean reverse) throws IOException {
        if (reverse) {
            throw new IllegalArgumentException("csrg reversed not supported"); // TODO: reverse csg (need to lookup remapped classes)
        }

        String[] tokens = line.split(" ");

        if (tokens.length == 2) {
            String oldClassName = inputTransformer.transformClassName(tokens[0]);
            String newClassName = outputTransformer.transformClassName(tokens[1]);

            if (oldClassName.endsWith("/")) {
                // Special case: mapping an entire hierarchy of classes
                packages.put(oldClassName.substring(0, oldClassName.length() - 1), newClassName);
            } else {
                classes.put(oldClassName, newClassName);
            }
        } else if (tokens.length == 3) {
            String oldClassName = inputTransformer.transformClassName(tokens[0]);
            String oldFieldName = inputTransformer.transformFieldName(tokens[0], tokens[1]);
            String newFieldName = outputTransformer.transformFieldName(tokens[0], tokens[2]);
            fields.put(oldClassName + "/" + oldFieldName, newFieldName);
        } else if (tokens.length == 4) {
            String oldClassName = inputTransformer.transformClassName(tokens[0]);
            String oldMethodName = inputTransformer.transformMethodName(tokens[0], tokens[1], tokens[2]);
            String oldMethodDescriptor = inputTransformer.transformMethodDescriptor(tokens[2]);
            String newMethodName = outputTransformer.transformMethodName(tokens[0], tokens[3], tokens[2]);
            methods.put(oldClassName + "/" + oldMethodName + " " + oldMethodDescriptor, newMethodName);
        } else {
            throw new IOException("Invalid csrg file line, token count " + tokens.length + " unexpected in "+line);
        }
    }

    /**
     * Parse a standard 'srg' mapping format line and populate the data structures
     */
    private void parseSrgLine(String line, JarMappingLoadTransformer inputTransformer, JarMappingLoadTransformer outputTransformer, boolean reverse) throws IOException {
        String[] tokens = line.split(" ");
        String kind = tokens[0];

        if (kind.equals("CL:")) {
            String oldClassName = inputTransformer.transformClassName(tokens[1]);
            String newClassName = outputTransformer.transformClassName(tokens[2]);

            if (reverse) {
                String temp = newClassName;
                newClassName = oldClassName;
                oldClassName = temp;
            }

            if (classes.containsKey(oldClassName) && !newClassName.equals(classes.get(oldClassName))) {
                throw new IllegalArgumentException("Duplicate class mapping: " + oldClassName + " -> " + newClassName +
                    " but already mapped to "+classes.get(oldClassName)+" in line="+line);
            }

            classes.put(oldClassName, newClassName);
        } else if (kind.equals("PK:")) {
            /* TODO: support .srg's package maps
            String oldPackageName = inputTransformer.transformClassName(tokens[1]);
            String newPackageName = outputTransformer.transformClassName(tokens[2]);

            if (reverse) {
                String temp = newPackageName;
                newPackageName = oldPackageName;
                oldPackageName = temp;
            }

            if (packages.containsKey(oldPackageName) && !newPackageName.equals(packages.get(oldPackageName))) {
                throw new IllegalArgumentException("Duplicate package mapping: " + oldPackageName + " ->" + newPackageName +
                    " but already mapped to "+packages.get(oldPackageName)+" in line="+line);
            }

            packages.put(oldPackageName, newPackageName);
            */
        } else if (kind.equals("FD:")) {
            String oldFull = tokens[1];
            String newFull = tokens[2];

            // Split the qualified field names into their classes and actual names
            int splitOld = oldFull.lastIndexOf('/');
            int splitNew = newFull.lastIndexOf('/');
            if (splitOld == -1 || splitNew == -1) {
                throw new IllegalArgumentException("Field name is invalid, not fully-qualified: " + oldFull +
                        " -> " + newFull + " in line="+line);
            }

            String oldClassName = inputTransformer.transformClassName(oldFull.substring(0, splitOld));
            String oldFieldName = inputTransformer.transformFieldName(oldFull.substring(0, splitOld), oldFull.substring(splitOld + 1));
            String newClassName = outputTransformer.transformClassName(newFull.substring(0, splitNew)); // TODO: verify with existing class map? (only used for reverse)
            String newFieldName = outputTransformer.transformFieldName(oldFull.substring(0, splitOld), newFull.substring(splitNew + 1));

            if (reverse) {
                oldClassName = newClassName;

                String temp = newFieldName;
                newFieldName = oldFieldName;
                oldFieldName = temp;
            }

            fields.put(oldClassName + "/" + oldFieldName, newFieldName);
        } else if (kind.equals("MD:")) {
            String oldFull = tokens[1];
            String newFull = tokens[3];

            // Split the qualified field names into their classes and actual names TODO: refactor with below
            int splitOld = oldFull.lastIndexOf('/');
            int splitNew = newFull.lastIndexOf('/');
            if (splitOld == -1 || splitNew == -1) {
                throw new IllegalArgumentException("Field name is invalid, not fully-qualified: " + oldFull +
                        " -> " + newFull + " in line="+line);
            }

            String oldClassName = inputTransformer.transformClassName(oldFull.substring(0, splitOld));
            String oldMethodName = inputTransformer.transformMethodName(oldFull.substring(0, splitOld), oldFull.substring(splitOld + 1), tokens[2]);
            String oldMethodDescriptor = inputTransformer.transformMethodDescriptor(tokens[2]);
            String newClassName = outputTransformer.transformClassName(newFull.substring(0, splitNew)); // TODO: verify with existing class map? (only used for reverse)
            String newMethodName = outputTransformer.transformMethodName(oldFull.substring(0, splitOld), newFull.substring(splitNew + 1), tokens[2]);
            String newMethodDescriptor = outputTransformer.transformMethodDescriptor(tokens[4]); // TODO: verify with existing class map? (only used for reverse)

            if (reverse) {
                oldClassName = newClassName;
                oldMethodDescriptor = newMethodDescriptor;

                String temp = newMethodName;
                newMethodName = oldMethodName;
                oldMethodName = temp;
            }

            methods.put(oldClassName + "/" + oldMethodName + " " + oldMethodDescriptor, newMethodName);
        } else {
            throw new IllegalArgumentException("Unable to parse srg file, unrecognized mapping type in line="+line);
        }
    }

    public JarMapping(JarComparer oldJar, JarComparer newJar, File logFile, boolean compact) throws IOException {
        this(oldJar, newJar, logFile, compact, false);
    }

    /**
     * Generate a mapping given an original jar and renamed jar
     *
     * @param oldJar Original jar
     * @param newJar Renamed jar
     * @param logfile Optional .srg file to output mappings to
     * @param compact If true, generate .csrg logfile instead of .srg
     * @param full if true, generate duplicates
     * @throws IOException
     */
    public JarMapping(JarComparer oldJar, JarComparer newJar, File logfile, boolean compact, boolean full) throws IOException {
        SpecialSource.validate(oldJar, newJar);

        PrintWriter out;
        if (logfile == null) {
            out = new PrintWriter(System.out);
        } else {
            out = new PrintWriter(logfile);
        }

        ISrgWriter srgWriter;
        if (compact) {
            srgWriter = new CompactSrgWriter(out);
        } else {
            srgWriter = new SrgWriter(out, oldJar.jar.getFilename(), newJar.jar.getFilename());
        }

        for (int i = 0; i < oldJar.classes.size(); i++) {
            String oldClass = oldJar.classes.get(i);
            String newClass = newJar.classes.get(i);
            classes.put(oldClass, newClass); // always output class names (no duplicate check)
            srgWriter.addClassMap(oldClass, newClass);
        }
        for (int i = 0; i < oldJar.fields.size(); i++) {
            Ownable oldField = oldJar.fields.get(i);
            Ownable newField = newJar.fields.get(i);
            String key = oldField.owner + "/" + oldField.name;
            fields.put(key, newField.name);

            if (full || !oldField.name.equals(newField.name)) {
                srgWriter.addFieldMap(oldField, newField);
            }
        }
        for (int i = 0; i < oldJar.methods.size(); i++) {
            Ownable oldMethod = oldJar.methods.get(i);
            Ownable newMethod = newJar.methods.get(i);
            String key = oldMethod.owner + "/" + oldMethod.name + " " + oldMethod.descriptor;
            methods.put(key, newMethod.name);

            MethodDescriptorTransformer methodDescriptorTransformer = new MethodDescriptorTransformer(null, classes);
            String oldDescriptor = methodDescriptorTransformer.transform(oldMethod.descriptor);

            if (full || !(oldMethod.name + " " + oldDescriptor).equals(newMethod.name + " " + newMethod.descriptor)) {
                srgWriter.addMethodMap(oldMethod, newMethod);
            }
        }

        srgWriter.write();
    }
}

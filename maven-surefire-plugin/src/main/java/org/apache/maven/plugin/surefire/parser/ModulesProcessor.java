/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.apache.maven.plugin.surefire.parser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.apache.maven.surefire.booter.SurefireBooter;
import org.apache.maven.surefire.jboss.config.Versions;

/**
 * TODO investigate being able to inject our fields using plexus
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class ModulesProcessor {

    private final Log log;
    
    private final MavenProject project;

    private final ArtifactRepository localRepository;
    
    private final ArtifactFactory artifactFactory;
    
    private final ArtifactMetadataSource metadataSource;
    
    private final ArtifactCollector artifactCollector;
    
    private final File testClassesDirectory;

    private final File classesDirectory;
    
    private final File moduleDefinitionFile;

    private final boolean cleanModulesDirectory;
    
    private final File modulesDirectory;

    private final DependencyTreeBuilder dependencyTreeBuilder;
    
    private final boolean arquillianAs; 
    
    private final Map<String, Artifact> dependencyArtifacts = new HashMap<String, Artifact>();

    public ModulesProcessor(Log log, MavenProject project, ArtifactRepository localRepository, ArtifactFactory artifactFactory,
            ArtifactMetadataSource metadataSource, ArtifactCollector artifactCollector, File testClassesDirectory, File classesDirectory,
            File moduleDefinitionFile, boolean cleanModulesDirectory, File modulesDirectory, DependencyTreeBuilder dependencyTreeBuilder,
            boolean arquillianAs) {
        this.log = log;
        this.project = project;
        this.localRepository = localRepository;
        this.artifactFactory = artifactFactory;
        this.metadataSource = metadataSource;
        this.artifactCollector = artifactCollector;
        this.testClassesDirectory = testClassesDirectory;
        this.classesDirectory = classesDirectory;
        this.moduleDefinitionFile = moduleDefinitionFile;
        this.cleanModulesDirectory = cleanModulesDirectory;
        this.modulesDirectory = modulesDirectory;
        this.dependencyTreeBuilder = dependencyTreeBuilder;
        this.arquillianAs = arquillianAs;
    }


    public void createModulesDirectory() throws MojoExecutionException {
        if (initializeModulesDirectory()) {
            return;
        }
        initializeModuleDefinitonFile();
        initializeDependencyArtifacts();

        processModules();
    }
    
    private boolean initializeModulesDirectory() throws MojoExecutionException {
        if (modulesDirectory.exists()) {
            if (!cleanModulesDirectory) {
                log.info("Reusing modules directory " + modulesDirectory.getAbsolutePath() + ". To recreate it next time run with -Djboss.modules.clean=true");
                return true;
            }
            log.info("Deleting existing modules directory " + modulesDirectory.getAbsolutePath() + ". It will be recreated. " +
            		"To keep it around for the next test-run, run with -Djboss.modules.clean=false");
            delete(modulesDirectory);
        } else {
            modulesDirectory.mkdirs();
            if (!modulesDirectory.exists()){
                throw new MojoExecutionException("Could not create directory " + modulesDirectory.getAbsolutePath());
            }
        }

        return false;
    }

    private void initializeModuleDefinitonFile() throws MojoExecutionException {
        if (!moduleDefinitionFile.exists()) {
            throw new MojoExecutionException("Could not find module definition file " + moduleDefinitionFile);
        }
    }

    private void initializeDependencyArtifacts() {
        DependencyNode node;
        try {
            node = dependencyTreeBuilder.buildDependencyTree( project, localRepository, artifactFactory,
                    metadataSource, null, artifactCollector );
        } catch (DependencyTreeBuilderException e) {
            throw new RuntimeException(e);
        }

        IndexingDependencyNodeVisitor visitor = new IndexingDependencyNodeVisitor();
        node.accept(visitor);
        
        //These get included implicitly
        try {
            dependencyArtifacts.put("org.jboss.maven.surefire.modular:surefire-booter", new DefaultArtifact("org.jboss.maven.surefire.modular", "surefire-booter", VersionRange.createFromVersionSpec(Versions.PLUGIN_FORK_VERSION), "compile", "jar", null, new DefaultArtifactHandler("jar")));
            dependencyArtifacts.put("org.apache.maven.surefire:surefire-api", new DefaultArtifact("org.apache.maven.surefire", "surefire-api", VersionRange.createFromVersionSpec(Versions.PROPER_SUREFIRE_VERSION), "compile", "jar", null, new DefaultArtifactHandler("jar")));
        } catch (InvalidVersionSpecificationException e) {
            // AutoGenerated
            throw new RuntimeException(e);
        }
        
    }

    private void processModules() {
        ModulesParser parser = new ModulesParser(moduleDefinitionFile);
        List<Module> modules = parser.parse();
        
        for (Module module : createSurefireModules(parser)) {
            processModule(module);    
        }
        
        for (Module module : modules) {
            processModule(module);
        }
    }
    
    private void processModule(Module module) {
        File moduleDir = createModuleDirectory(module.getName());
        List<MavenReplacement> replacements = new ArrayList<MavenReplacement>();
        module.findReplacementAttributes(replacements);
        for (MavenReplacement replacement : replacements) {
            File moduleFile = copyMavenArtifact(moduleDir, replacement);
            if (moduleFile != null) {
                replacement.updateAttributeValue(moduleFile.getName());
            }
        }
        module.output(moduleDir);
    }
    
    private File createModuleDirectory(String name) {
        String path = name.replace('.', File.separatorChar) + File.separator + "main";
        File file = new File(modulesDirectory, path);
        file.mkdirs();
        if (!file.exists()) {
            throw new RuntimeException("Could not create directory " + file.getAbsolutePath());
        }
        return file;
    }


    private List<Module> createSurefireModules(ModulesParser parser) {
        if (!"urn:jboss:module:1.0".equals(parser.getJBossModulesNamespaceUri())) {
            throw new IllegalArgumentException("Invalid jboss modules version");
        }
     
        Module module = new Module(parser.getJBossModulesNamespaceUri());
        module.addAttribute("name", "jboss.surefire.module");
        
        ChildElement mainClass = new ChildElement("main-class");
        mainClass.addAttribute("name", SurefireBooter.class.getName());
        module.addChild(mainClass);
        
        ChildElement resources = new ChildElement("resources");
        addResource(resources, "$org.apache.maven.surefire:surefire-api$");
        addResource(resources, "$org.jboss.maven.surefire.modular:surefire-booter$");

        module.addChild(resources);
        TestModuleResources testModuleResources = parser.getTestModuleResources();
        if (testModuleResources != null) {
            if (testModuleResources.getChildren() != null) {
                for (ChildElement resource : testModuleResources.getChildren()) {
                    resources.addChild(resource);
                }
            }
        }
        
        TestModuleDependencies testModuleDependencies = parser.getTestModuleDependencies();
        if (testModuleDependencies != null) {
            if (testModuleDependencies.getChildren() != null) {
                ChildElement deps = new ChildElement("dependencies");
                for (ChildElement dep : testModuleDependencies.getChildren()) {
                    deps.addChild(dep);
                }
                module.addChild(deps);
            }
        }

        return Collections.singletonList(module);
    }    
    
    private File copyMavenArtifact(File moduleDir, MavenReplacement replacement) {
        String mavenName = replacement.getAttributeValue();
        log.debug("Searching for artifact " + mavenName);

        int index = mavenName.indexOf(":");
        if (index == -1) {
            if (mavenName.equals("$CLASSES$")) {
                return copyDirectory(classesDirectory, moduleDir);
            } else if (mavenName.equals("$TEST.CLASSES$")) {
                return copyDirectory(testClassesDirectory, moduleDir);
            }
            return null;

        } else {
            Artifact artifact = dependencyArtifacts.get(mavenName);
            if (artifact == null) {
                log.warn("No artifact matching " + mavenName + " found in project dependencies");
                return null;
            }
            File srcFile = new File(new File(localRepository.getBasedir()), localRepository.pathOf(artifact));
            if (srcFile != null) {
                return copyFileToDir(srcFile, moduleDir);
            }
        }
        return null;
    }

    private File copyFileToDir(File src, File destDir) {
        if (!src.exists()) {
            throw new IllegalArgumentException("Maven repository jar " + src.getAbsolutePath() + " does not exist");
        }
        if (!destDir.exists() && !destDir.isDirectory()) {
            throw new IllegalArgumentException("No directory called " + destDir);
        }
        String name = src.getName();
        File dest = new File(destDir, name);

        copyFile(src, dest);
        return dest;
    }

    private void copyFile(File src, File dest) {
        OutputStream out;
        try {
            out = new BufferedOutputStream(new FileOutputStream(dest));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(src));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            int i = in.read();
            while (i != -1) {
                out.write(i);
                i = in.read();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            safeClose(in);
            safeClose(out);
        }
    }


    private File copyDirectory(File src, File destDir) {
        if (!src.exists()) {
            throw new IllegalArgumentException(src.getAbsolutePath() + " does not exist");
        }
        if (!destDir.exists() && !destDir.isDirectory()) {
            throw new IllegalArgumentException("No directory called " + destDir);
        }
        String name = src.getName();
        File dest = new File(destDir, name);
        dest.mkdir();
        copyDirectoryContents(src, dest);
        return dest;
    }

    private void copyDirectoryContents(File src, File destDir) {
        for (String curr : src.list()) {
            File original = new File(src, curr);
            File copy = new File(destDir, curr);
            if (original.isDirectory()) {
                copy.mkdir();
                copyDirectoryContents(original, copy);
            } else {
                copyFile(original, copy);
            }
        }
    }

    private static void delete(File file) {
        if (!file.isDirectory()) {
            file.delete();
            return;
        }
        for (String name :file.list()) {
            delete(new File(file, name));
        }
        file.delete();
    }

    private void safeClose(Closeable c) {
        try {
            c.close();
        } catch (IOException ignore) {
        }
    }

    public class IndexingDependencyNodeVisitor implements DependencyNodeVisitor
    {
        @Override
        public boolean endVisit(DependencyNode node) {
            return true;
        }

        @Override
        public boolean visit(DependencyNode node) {
            if (node.getState() == DependencyNode.INCLUDED) {
                addArtifact(node.getArtifact());
            }
            return true;
        }

        private void addArtifact(Artifact artifact) {
            String name = artifact.getGroupId() + ":" + artifact.getArtifactId();

            Artifact existing = dependencyArtifacts.get(name);
            if (existing != null && !existing.equals(artifact)) {
                log.warn("Ignoring found dependency for '" + name + "' since it was already resolved to '" + dependencyArtifacts.get(name) +
                        "'. Ignored value is '" + artifact + "'. Run 'mvn dependency:tree' and clean up your dependencies to avoid duplicates");
            } else {
                dependencyArtifacts.put(name, artifact);
            }
        }

    }
    
    private void addResource(ChildElement resources, String placeholder) {
        ChildElement resource = new ChildElement("resource-root");
        resource.addAttribute("path", placeholder);
        resources.addChild(resource);
    }
}

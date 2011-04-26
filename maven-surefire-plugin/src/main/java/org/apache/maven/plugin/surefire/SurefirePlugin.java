package org.apache.maven.plugin.surefire;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.parser.ModulesProcessor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.PatternIncludesArtifactFilter;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.surefire.booter.ForkConfiguration;
import org.apache.maven.surefire.booter.SurefireBooter;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.booter.SurefireExecutionException;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.StringUtils;

/**
 * Run tests using Surefire.
 * This is a fork of org.apache.maven.plugins:maven-surefire-plugin:2.6:test.
 * 
 * It has the configuration options of the 'real' surefire plugin (http://maven.apache.org/plugins/maven-surefire-plugin/test-mojo.html), 
 * although your mileage may vary if you start playing with the fork modes. The plugin takes a module definition file and creates a directory structure 
 * containing modules in the jboss-modules format.
 *
 * @author Jason van Zyl
 * @version $Id: SurefirePlugin.java 981261 2010-08-01 16:26:10Z bentmann $
 * @requiresDependencyResolution test
 * @goal test
 * @phase test
 * @threadSafe
 */
public class SurefirePlugin
    extends AbstractSurefireMojo
    implements SurefireExecutionParameters, SurefireReportParameters
{

    /**
     * Set this to 'true' to skip running tests, but still compile them. Its use is NOT RECOMMENDED, but quite
     * convenient on occasion.
     *
     * @parameter default-value="false" expression="${skipTests}"
     * @since 2.4
     */
    private boolean skipTests;

    /**
     * This old parameter is just like skipTests, but bound to the old property maven.test.skip.exec.
     *
     * @parameter expression="${maven.test.skip.exec}"
     * @since 2.3
     * @deprecated Use -DskipTests instead.
     */
    private boolean skipExec;

    /**
     * Set this to 'true' to bypass unit tests entirely. Its use is NOT RECOMMENDED, especially if you
     * enable it using the "maven.test.skip" property, because maven.test.skip disables both running the
     * tests and compiling the tests.  Consider using the skipTests parameter instead.
     *
     * @parameter default-value="false" expression="${maven.test.skip}"
     */
    private boolean skip;

    /**
     * Set this to true to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
     * occasion.
     *
     * @parameter default-value="false" expression="${maven.test.failure.ignore}"
     */
    private boolean testFailureIgnore;

    /**
     * The base directory of the project being tested. This can be obtained in your unit test by
     * System.getProperty("basedir").
     *
     * @parameter default-value="${basedir}"
     */
    private File basedir;

    /**
     * The directory containing generated test classes of the project being tested.
     * This will be included at the beginning the test classpath.
     *
     * @parameter default-value="${project.build.testOutputDirectory}"
     */
    private File testClassesDirectory;

    /**
     * The directory containing generated classes of the project being tested.
     * This will be included after the test classes in the test classpath.
     *
     * @parameter default-value="${project.build.outputDirectory}"
     */
    private File classesDirectory;

    /**
     * The Maven Project Object
     *
     * @parameter default-value="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * List of dependencies to exclude from the test classpath.
     * Each dependency string must follow the format <i>groupId:artifactId</i>.
     * For example: <i>org.acme:project-a</i>
     * 
     *  @parameter
     *  @since 2.6
     */
    private List classpathDependencyExcludes;
    
    /**
     * A dependency scope to exclude from the test classpath
     * The scope can be one of the following scopes:
     * 
     * <ul>
     * <li><i>compile</i> - system, provided, compile
     * <li><i>runtime</i> - compile, runtime
     * <li><i>test</i> - system, provided, compile, runtime, test
     * </ul>
     * 
     * @parameter default-value=""
     * @since 2.6
     */
    private String classpathDependencyScopeExclude;
    
    /**
     * Additional elements to be appended to the classpath.
     *
     * @parameter
     * @since 2.4
     */
    private List additionalClasspathElements;

    /**
     * Base directory where all reports are written to.
     *
     * @parameter default-value="${project.build.directory}/surefire-reports"
     */
    private File reportsDirectory;

    /**
     * The test source directory containing test class sources.
     *
     * @parameter default-value="${project.build.testSourceDirectory}"
     * @required
     * @since 2.2
     */
    private File testSourceDirectory;

    /**
     * Specify this parameter to run individual tests by file name, overriding the <code>includes/excludes</code>
     * parameters.  Each pattern you specify here will be used to create an
     * include pattern formatted like <code>**&#47;${test}.java</code>, so you can just type "-Dtest=MyTest"
     * to run a single test called "foo/MyTest.java".  This parameter will override the TestNG suiteXmlFiles
     * parameter.
     *
     * @parameter expression="${test}"
     */
    private String test;

    /**
     * List of patterns (separated by commas) used to specify the tests that should be included in testing. When not
     * specified and when the <code>test</code> parameter is not specified, the default includes will be
     * <code>**&#47;Test*.java   **&#47;*Test.java   **&#47;*TestCase.java</code>.  This parameter is ignored if
     * TestNG suiteXmlFiles are specified.
     *
     * @parameter
     */
    private List includes;

    /**
     * List of patterns (separated by commas) used to specify the tests that should be excluded in testing. When not
     * specified and when the <code>test</code> parameter is not specified, the default excludes will be
     * <code>**&#47;*$*</code> (which excludes all inner classes).  This parameter is ignored if
     * TestNG suiteXmlFiles are specified.
     *
     * @parameter
     */
    private List excludes;

    /**
     * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
     * System.setProperty( "localRepository").
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * List of System properties to pass to the JUnit tests.
     *
     * @parameter
     * @deprecated Use systemPropertyVariables instead.
     */
    private Properties systemProperties;

    /**
     * List of System properties to pass to the JUnit tests.
     *
     * @parameter
     * @since 2.5
     */
    private Map systemPropertyVariables;

    /**
     * List of properties for configuring all TestNG related configurations. This is the new
     * preferred method of configuring TestNG.
     *
     * @parameter
     * @since 2.4
     */
    private Properties properties;

    /**
     * Map of of plugin artifacts.
     *
     * @parameter expression="${plugin.artifactMap}"
     * @required
     * @readonly
     */
    private Map pluginArtifactMap;

    /**
     * Map of of project artifacts.
     *
     * @parameter expression="${project.artifactMap}"
     * @required
     * @readonly
     */
    private Map projectArtifactMap;

    /**
     * Option to print summary of test suites or just print the test cases that has errors.
     *
     * @parameter expression="${surefire.printSummary}" default-value="true"
     */
    private boolean printSummary;

    /**
     * Selects the formatting for the test report to be generated. Can be set as brief or plain.
     *
     * @parameter expression="${surefire.reportFormat}" default-value="brief"
     */
    private String reportFormat;

    /**
     * Option to generate a file test report or just output the test report to the console.
     *
     * @parameter expression="${surefire.useFile}" default-value="true"
     */
    private boolean useFile;

    /**
     * When forking, set this to true to redirect the unit test standard output to a file (found in
     * reportsDirectory/testName-output.txt).
     *
     * @parameter expression="${maven.test.redirectTestOutputToFile}" default-value="false"
     * @since 2.3
     */
    private boolean redirectTestOutputToFile;

    /**
     * Set this to "true" to cause a failure if there are no tests to run.
     *
     * @parameter expression="${failIfNoTests}"
     * @since 2.4
     */
    private Boolean failIfNoTests;

    /**
     * Option to specify the forking mode. Can be "never", "once" or "always". "none" and "pertest" are also accepted
     * for backwards compatibility.
     *
     * @parameter expression="${forkMode}" default-value="once"
     * @since 2.1
     */
    private String forkMode;

    /**
     * Option to specify the jvm (or path to the java executable) to use with the forking options. For the default, the
     * jvm will be the same as the one used to run Maven.
     *
     * @parameter expression="${jvm}"
     * @since 2.1
     */
    private String jvm;

    /**
     * Arbitrary JVM options to set on the command line.
     *
     * @parameter expression="${argLine}"
     * @since 2.1
     */
    private String argLine;

    /**
     * Attach a debugger to the forked JVM.  If set to "true", the process will suspend and
     * wait for a debugger to attach on port 5005.  If set to some other string, that
     * string will be appended to the argLine, allowing you to configure arbitrary
     * debuggability options (without overwriting the other options specified in the argLine).
     *
     * @parameter expression="${maven.surefire.debug}"
     * @since 2.4
     */
    private String debugForkedProcess;

    /**
     * Kill the forked test process after a certain number of seconds.  If set to 0,
     * wait forever for the process, never timing out.
     *
     * @parameter expression="${surefire.timeout}"
     * @since 2.4
     */
    private int forkedProcessTimeoutInSeconds;

    /**
     * Additional environments to set on the command line.
     *
     * @parameter
     * @since 2.1.3
     */
    private Map environmentVariables = new HashMap();

    /**
     * Command line working directory.
     *
     * @parameter expression="${basedir}"
     * @since 2.1.3
     */
    private File workingDirectory;

    /**
     * When false it makes tests run using the standard classloader delegation instead of the default Maven isolated
     * classloader. Only used when forking (forkMode is not "none").<br/> Setting it to false helps with some problems
     * caused by conflicts between xml parsers in the classpath and the Java 5 provider parser.
     *
     * @parameter expression="${childDelegation}" default-value="false"
     * @since 2.1
     */
    private boolean childDelegation;

    /**
     * (TestNG only) Groups for this test. Only classes/methods/etc decorated with one of the groups specified here will be included
     * in test run, if specified.  This parameter is overridden if suiteXmlFiles are specified.
     *
     * @parameter expression="${groups}"
     * @since 2.2
     */
    private String groups;

    /**
     * (TestNG only) Excluded groups. Any methods/classes/etc with one of the groups specified in this list will specifically not be
     * run.  This parameter is overridden if suiteXmlFiles are specified.
     *
     * @parameter expression="${excludedGroups}"
     * @since 2.2
     */
    private String excludedGroups;

    /**
     * (TestNG only) List of TestNG suite xml file locations, seperated by commas. Note that suiteXmlFiles is incompatible
     * with several other parameters on this plugin, like includes/excludes.  This parameter is ignored if
     * the "test" parameter is specified (allowing you to run a single test instead of an entire suite).
     *
     * @parameter
     * @since 2.2
     */
    private File[] suiteXmlFiles;

    /**
     * Allows you to specify the name of the JUnit artifact. If not set, <code>junit:junit</code> will be used.
     *
     * @parameter expression="${junitArtifactName}" default-value="junit:junit"
     * @since 2.3.1
     */
    private String junitArtifactName;

    /**
     * Allows you to specify the name of the TestNG artifact. If not set, <code>org.testng:testng</code> will be used.
     *
     * @parameter expression="${testNGArtifactName}" default-value="org.testng:testng"
     * @since 2.3.1
     */
    private String testNGArtifactName;

    /**
     * (TestNG/JUnit 4.7 provider only) The attribute thread-count allows you to specify how many threads should be allocated for this execution. Only
     * makes sense to use in conjunction with parallel.
     *
     * @parameter expression="${threadCount}"
     * @since 2.2
     */
    private int threadCount;

    /**
     * (JUnit 4.7 provider) Indicates that threadCount is per cpu core. Defaults to true
     *
     * @parameter expression="${perCoreThreadCount}"
     * @since 2.5
     */
    private String perCoreThreadCount;

    /**
     * (JUnit 4.7 provider) Indicates that the thread pool will be unlimited. The parallel parameter and the actual number of classes/methods
     * will decide. Setting this to true effectively disables perCoreThreadCount and threadCount.
     *
     * @parameter expression="${useUnlimitedThreads}"
     * @since 2.5
     */
    private String useUnlimitedThreads;

    /**
     * (TestNG only) When you use the parallel attribute, TestNG will try to run all your test methods in separate threads, except for
     * methods that depend on each other, which will be run in the same thread in order to respect their order of
     * execution.
     * <p/>
     * (JUnit 4.7 provider) Supports values classes/methods/both to run in separate threads, as controlled by threadCount.
     *
     * @parameter expression="${parallel}"
     * @todo test how this works with forking, and console/file output parallelism
     * @since 2.2
     */
    private String parallel;

    /**
     * Whether to trim the stack trace in the reports to just the lines within the test, or show the full trace.
     *
     * @parameter expression="${trimStackTrace}" default-value="true"
     * @since 2.2
     */
    private boolean trimStackTrace;

    /**
     * Resolves the artifacts needed.
     *
     * @component
     */
    private ArtifactResolver artifactResolver;

    /**
     * Creates the artifact
     *
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * The plugin remote repositories declared in the POM.
     *
     * @parameter expression="${project.pluginArtifactRepositories}"
     * @since 2.2
     */
    private List remoteRepositories;

    /**
     * For retrieval of artifact's metadata.
     *
     * @component
     */
    private ArtifactMetadataSource metadataSource;

    private Properties originalSystemProperties;

    /**
     * systemPropertyVariables + systemProperties
     */
    private Properties internalSystemProperties = new Properties();

    /**
     * Flag to disable the generation of report files in xml format.
     *
     * @parameter expression="${disableXmlReport}" default-value="false"
     * @since 2.2
     */
    private boolean disableXmlReport;

    /**
     * Option to pass dependencies to the system's classloader instead of using an isolated class loader when forking.
     * Prevents problems with JDKs which implement the service provider lookup mechanism by using the system's
     * classloader.  Default value is "true".
     *
     * @parameter expression="${surefire.useSystemClassLoader}"
     * @since 2.3
     */
    private Boolean useSystemClassLoader;

    /**
     * By default, Surefire forks your tests using a manifest-only JAR; set this parameter
     * to "false" to force it to launch your tests with a plain old Java classpath.
     * (See http://maven.apache.org/plugins/maven-surefire-plugin/examples/class-loading.html
     * for a more detailed explanation of manifest-only JARs and their benefits.)
     * <p/>
     * Beware, setting this to "false" may cause your tests to
     * fail on Windows if your classpath is too long.
     *
     * @parameter expression="${surefire.useManifestOnlyJar}" default-value="true"
     * @since 2.4.3
     */
    private boolean useManifestOnlyJar;

    /**
     * By default, Surefire enables JVM assertions for the execution of your test cases. To disable the assertions, set
     * this flag to <code>false</code>.
     *
     * @parameter expression="${enableAssertions}" default-value="true"
     * @since 2.3.1
     */
    private boolean enableAssertions;

    /**
     * The current build session instance.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * (TestNG only) Define the factory class used to create all test instances
     *
     * @parameter expression="${objectFactory}"
     * @since 2.5
     */
    private String objectFactory;


    /** @parameter default-value="${session.parallel}" */
    private Boolean parallelMavenExecution;
    
    /**
     * @component
     */
    private ToolchainManager toolchainManager;

    ////////////////////////////////////////////////////
    //JBoss Modules configuration parameters
    
    
    /**
     * The existing module root directories
     * 
     * @parameter
     */
    private List<String> roots;
    
    /**
     * If true (default) clean out the modules directory each time we run the tests.
     * 
     * @parameter expression="${jboss.modules.clean}" default-value="true"
     */
    private boolean cleanModulesDirectory;
    
    /**
     * The absolute path of the modules output directory created from {@link #moduleDefinitionFile}.
     *
     * @parameter expression="${jboss.modules.directory}" default-value="${project.build.directory}/modules"
     */
    private File modulesDirectory;
    
    /**
     * The name of the -logmodule parameter passes in to JBoss Modules (i.e. the name of the module containg the jboss logmanager). This is needed if the target project uses 
     * java.util.Logging or jboss logging, and the jboss log manager is not on the system classpath.
     * 
     * @parameter expression="${jboss.modules.logmodule}"
     */
    private String logModule;

    /**
     * The name of the -jaxpmodule parameter passed into jboss modules.
     */
    private String jaxpModule;

    /**
     * The JBoss logging configuration if any. This must be set if the target project uses jboss logging and you want any output to be displayed
     * 
     * @parameter expression="${logging.configuration}"
     */
    private File logConfiguration;
    
    /**
     * The path of the module definition file.
     *
     * @parameter expression="${jboss.modules.definition}" default-value="${project.build.testOutputDirectory}/modules/module-def.xml"
     * @required
     */
    private File moduleDefinitionFile;

    /**
     * The artifact collector to use.
     *
     * @component
     * @required
     * @readonly
     */
    private ArtifactCollector artifactCollector;

    /**
     * The dependency tree builder to use.
     *
     * @component
     * @required
     * @readonly
     */
    private DependencyTreeBuilder dependencyTreeBuilder;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( verifyParameters() )
        {
            createJBossModulesDirectory();
            
            SurefireBooter surefireBooter = constructSurefireBooter();

            getLog().info(
                StringUtils.capitalizeFirstLetter( getPluginName() ) + " report directory: " + getReportsDirectory() );

            int result;
            try
            {
                result = surefireBooter.run();
            }
            catch ( SurefireBooterForkException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            catch ( SurefireExecutionException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }

            if ( getOriginalSystemProperties() != null && !surefireBooter.isForking() )
            {
                // restore system properties, only makes sense when not forking..
                System.setProperties( getOriginalSystemProperties() );
            }

            SurefireHelper.reportExecution( this, result, getLog() );
        }
    }

    protected boolean verifyParameters()
        throws MojoFailureException
    {
        if ( isSkip() || isSkipTests() || isSkipExec() )
        {
            getLog().info( "Tests are skipped." );
            return false;
        }

        if ( !getTestClassesDirectory().exists() )
        {
            if ( getFailIfNoTests() != null && getFailIfNoTests().booleanValue() )
            {
                throw new MojoFailureException( "No tests to run!" );
            }
            getLog().info( "No tests to run." );
            return true;
        }

        ensureWorkingDirectoryExists();

        ensureParallelRunningCompatibility();

        warnIfUselessUseSystemClassLoaderParameter();

        return true;
    }

    protected String getPluginName()
    {
        return "surefire";
    }

    protected String[] getDefaultIncludes()
    {
        return new String[]{"**/Test*.java", "**/*Test.java", "**/*TestCase.java"};
    }

    // now for the implementation of the field accessors

    public boolean isSkipTests()
    {
        return skipTests;
    }



    public void setSkipTests( boolean skipTests )
    {
        this.skipTests = skipTests;
    }

    public boolean isSkipExec()
    {
        return skipExec;
    }

    public void setSkipExec( boolean skipExec )
    {
        this.skipExec = skipExec;
    }

    public boolean isSkip()
    {
        return skip;
    }

    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }

    public boolean isTestFailureIgnore()
    {
        return testFailureIgnore;
    }

    public void setTestFailureIgnore( boolean testFailureIgnore )
    {
        this.testFailureIgnore = testFailureIgnore;
    }

    public File getBasedir()
    {
        return basedir;
    }

    public void setBasedir( File basedir )
    {
        this.basedir = basedir;
    }

    public File getTestClassesDirectory()
    {
        return testClassesDirectory;
    }

    public void setTestClassesDirectory( File testClassesDirectory )
    {
        this.testClassesDirectory = testClassesDirectory;
    }

    public File getClassesDirectory()
    {
        return classesDirectory;
    }

    public void setClassesDirectory( File classesDirectory )
    {
        this.classesDirectory = classesDirectory;
    }

    public MavenProject getProject()
    {
        return project;
    }

    public void setProject( MavenProject project )
    {
        this.project = project;
    }

    public List getClasspathDependencyExcludes()
    {
        return classpathDependencyExcludes;
    }

    public void setClasspathDependencyExcludes( List classpathDependencyExcludes )
    {
        this.classpathDependencyExcludes = classpathDependencyExcludes;
    }

    public String getClasspathDependencyScopeExclude()
    {
        return classpathDependencyScopeExclude;
    }

    public void setClasspathDependencyScopeExclude( String classpathDependencyScopeExclude )
    {
        this.classpathDependencyScopeExclude = classpathDependencyScopeExclude;
    }

    public List getAdditionalClasspathElements()
    {
        return additionalClasspathElements;
    }

    public void setAdditionalClasspathElements( List additionalClasspathElements )
    {
        this.additionalClasspathElements = additionalClasspathElements;
    }

    public File getReportsDirectory()
    {
        return reportsDirectory;
    }

    public void setReportsDirectory( File reportsDirectory )
    {
        this.reportsDirectory = reportsDirectory;
    }

    public File getTestSourceDirectory()
    {
        return testSourceDirectory;
    }

    public void setTestSourceDirectory( File testSourceDirectory )
    {
        this.testSourceDirectory = testSourceDirectory;
    }

    public String getTest()
    {
        return test;
    }

    public void setTest( String test )
    {
        this.test = test;
    }

    public List getIncludes()
    {
        return includes;
    }

    public void setIncludes( List includes )
    {
        this.includes = includes;
    }

    public List getExcludes()
    {
        return excludes;
    }

    public void setExcludes( List excludes )
    {
        this.excludes = excludes;
    }

    public ArtifactRepository getLocalRepository()
    {
        return localRepository;
    }

    public void setLocalRepository( ArtifactRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    public Properties getSystemProperties()
    {
        return systemProperties;
    }

    public void setSystemProperties( Properties systemProperties )
    {
        this.systemProperties = systemProperties;
    }

    public Map getSystemPropertyVariables()
    {
        return systemPropertyVariables;
    }

    public void setSystemPropertyVariables( Map systemPropertyVariables )
    {
        this.systemPropertyVariables = systemPropertyVariables;
    }

    public Properties getProperties()
    {
        return properties;
    }

    public void setProperties( Properties properties )
    {
        this.properties = properties;
    }

    public Map getPluginArtifactMap()
    {
        return pluginArtifactMap;
    }

    public void setPluginArtifactMap( Map pluginArtifactMap )
    {
        this.pluginArtifactMap = pluginArtifactMap;
    }

    public Map getProjectArtifactMap()
    {
        return projectArtifactMap;
    }

    public void setProjectArtifactMap( Map projectArtifactMap )
    {
        this.projectArtifactMap = projectArtifactMap;
    }

    public boolean isPrintSummary()
    {
        return printSummary;
    }

    public void setPrintSummary( boolean printSummary )
    {
        this.printSummary = printSummary;
    }

    public String getReportFormat()
    {
        return reportFormat;
    }

    public void setReportFormat( String reportFormat )
    {
        this.reportFormat = reportFormat;
    }

    public boolean isUseFile()
    {
        return useFile;
    }

    public void setUseFile( boolean useFile )
    {
        this.useFile = useFile;
    }

    public boolean isRedirectTestOutputToFile()
    {
        return redirectTestOutputToFile;
    }

    public void setRedirectTestOutputToFile( boolean redirectTestOutputToFile )
    {
        this.redirectTestOutputToFile = redirectTestOutputToFile;
    }

    public Boolean getFailIfNoTests()
    {
        return failIfNoTests;
    }

    public void setFailIfNoTests( Boolean failIfNoTests )
    {
        this.failIfNoTests = failIfNoTests;
    }

    public String getForkMode()
    {
        return forkMode;
    }

    public void setForkMode( String forkMode )
    {
        this.forkMode = forkMode;
    }

    public String getJvm()
    {
        return jvm;
    }

    public void setJvm( String jvm )
    {
        this.jvm = jvm;
    }

    public String getArgLine()
    {
        return argLine;
    }

    public void setArgLine( String argLine )
    {
        this.argLine = argLine;
    }

    public String getDebugForkedProcess()
    {
        return debugForkedProcess;
    }

    public void setDebugForkedProcess( String debugForkedProcess )
    {
        this.debugForkedProcess = debugForkedProcess;
    }

    public int getForkedProcessTimeoutInSeconds()
    {
        return forkedProcessTimeoutInSeconds;
    }

    public void setForkedProcessTimeoutInSeconds( int forkedProcessTimeoutInSeconds )
    {
        this.forkedProcessTimeoutInSeconds = forkedProcessTimeoutInSeconds;
    }

    public Map getEnvironmentVariables()
    {
        return environmentVariables;
    }

    public void setEnvironmentVariables( Map environmentVariables )
    {
        this.environmentVariables = environmentVariables;
    }

    public File getWorkingDirectory()
    {
        return workingDirectory;
    }

    public void setWorkingDirectory( File workingDirectory )
    {
        this.workingDirectory = workingDirectory;
    }

    public boolean isChildDelegation()
    {
        return childDelegation;
    }

    public void setChildDelegation( boolean childDelegation )
    {
        this.childDelegation = childDelegation;
    }

    public String getGroups()
    {
        return groups;
    }

    public void setGroups( String groups )
    {
        this.groups = groups;
    }

    public String getExcludedGroups()
    {
        return excludedGroups;
    }

    public void setExcludedGroups( String excludedGroups )
    {
        this.excludedGroups = excludedGroups;
    }

    public File[] getSuiteXmlFiles()
    {
        return suiteXmlFiles;
    }

    public void setSuiteXmlFiles( File[] suiteXmlFiles )
    {
        this.suiteXmlFiles = suiteXmlFiles;
    }

    public String getJunitArtifactName()
    {
        return junitArtifactName;
    }

    public void setJunitArtifactName( String junitArtifactName )
    {
        this.junitArtifactName = junitArtifactName;
    }

    public String getTestNGArtifactName()
    {
        return testNGArtifactName;
    }

    public void setTestNGArtifactName( String testNGArtifactName )
    {
        this.testNGArtifactName = testNGArtifactName;
    }

    public int getThreadCount()
    {
        return threadCount;
    }

    public void setThreadCount( int threadCount )
    {
        this.threadCount = threadCount;
    }

    public String getPerCoreThreadCount()
    {
        return perCoreThreadCount;
    }

    public void setPerCoreThreadCount( String perCoreThreadCount )
    {
        this.perCoreThreadCount = perCoreThreadCount;
    }

    public String getUseUnlimitedThreads()
    {
        return useUnlimitedThreads;
    }

    public void setUseUnlimitedThreads( String useUnlimitedThreads )
    {
        this.useUnlimitedThreads = useUnlimitedThreads;
    }

    public String getParallel()
    {
        return parallel;
    }

    public void setParallel( String parallel )
    {
        this.parallel = parallel;
    }

    public boolean isTrimStackTrace()
    {
        return trimStackTrace;
    }

    public void setTrimStackTrace( boolean trimStackTrace )
    {
        this.trimStackTrace = trimStackTrace;
    }

    public ArtifactResolver getArtifactResolver()
    {
        return artifactResolver;
    }

    public void setArtifactResolver( ArtifactResolver artifactResolver )
    {
        this.artifactResolver = artifactResolver;
    }

    public ArtifactFactory getArtifactFactory()
    {
        return artifactFactory;
    }

    public void setArtifactFactory( ArtifactFactory artifactFactory )
    {
        this.artifactFactory = artifactFactory;
    }

    public List getRemoteRepositories()
    {
        return remoteRepositories;
    }

    public void setRemoteRepositories( List remoteRepositories )
    {
        this.remoteRepositories = remoteRepositories;
    }

    public ArtifactMetadataSource getMetadataSource()
    {
        return metadataSource;
    }

    public void setMetadataSource( ArtifactMetadataSource metadataSource )
    {
        this.metadataSource = metadataSource;
    }

    public Properties getOriginalSystemProperties()
    {
        return originalSystemProperties;
    }

    public void setOriginalSystemProperties( Properties originalSystemProperties )
    {
        this.originalSystemProperties = originalSystemProperties;
    }

    public Properties getInternalSystemProperties()
    {
        return internalSystemProperties;
    }

    public void setInternalSystemProperties( Properties internalSystemProperties )
    {
        this.internalSystemProperties = internalSystemProperties;
    }

    public boolean isDisableXmlReport()
    {
        return disableXmlReport;
    }

    public void setDisableXmlReport( boolean disableXmlReport )
    {
        this.disableXmlReport = disableXmlReport;
    }

    public Boolean getUseSystemClassLoader()
    {
        return useSystemClassLoader;
    }

    public void setUseSystemClassLoader( Boolean useSystemClassLoader )
    {
        this.useSystemClassLoader = useSystemClassLoader;
    }

    public boolean isUseManifestOnlyJar()
    {
        return useManifestOnlyJar;
    }

    public void setUseManifestOnlyJar( boolean useManifestOnlyJar )
    {
        this.useManifestOnlyJar = useManifestOnlyJar;
    }

    public boolean isEnableAssertions()
    {
        return enableAssertions;
    }

    public void setEnableAssertions( boolean enableAssertions )
    {
        this.enableAssertions = enableAssertions;
    }

    public MavenSession getSession()
    {
        return session;
    }

    public void setSession( MavenSession session )
    {
        this.session = session;
    }

    public String getObjectFactory()
    {
        return objectFactory;
    }

    public void setObjectFactory( String objectFactory )
    {
        this.objectFactory = objectFactory;
    }

    public ToolchainManager getToolchainManager()
    {
        return toolchainManager;
    }

    public void setToolchainManager( ToolchainManager toolchainManager )
    {
        this.toolchainManager = toolchainManager;
    }

    public boolean isMavenParallel()
    {
        return parallelMavenExecution  != null && parallelMavenExecution.booleanValue();
    }
    
    
    
    //Forked methods
    public List generateTestClasspath() throws DependencyResolutionRequiredException, MojoExecutionException {
        return new ArrayList();
    }
    
    private Set filterArtifacts( Set artifacts, ArtifactFilter filter )
    {
        Set filteredArtifacts = new LinkedHashSet();

        for ( Iterator iter = artifacts.iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();
            if ( ! filter.include( artifact ) )
            {
                filteredArtifacts.add( artifact );
            }
        }

        return filteredArtifacts;
    }
    
    private File findJBossModulesJar() throws MojoExecutionException
    {
        Set classpathArtifacts = getProject().getArtifacts();

        if ( getClasspathDependencyScopeExclude() != null && !getClasspathDependencyScopeExclude().equals( "" ) )
        {
            ArtifactFilter dependencyFilter = new ScopeArtifactFilter( getClasspathDependencyScopeExclude() );
            classpathArtifacts = this.filterArtifacts( classpathArtifacts, dependencyFilter );
        }

        if ( getClasspathDependencyExcludes() != null )
        {
            ArtifactFilter dependencyFilter = new PatternIncludesArtifactFilter( getClasspathDependencyExcludes() );
            classpathArtifacts = this.filterArtifacts( classpathArtifacts, dependencyFilter );
        }

        for ( Iterator iter = classpathArtifacts.iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();
            
            if (artifact.getGroupId().equals("org.jboss.modules") && artifact.getArtifactId().equals("jboss-modules")) 
            {
                if ( artifact.getArtifactHandler().isAddedToClasspath() )
                {
                    File file = artifact.getFile();
                    if ( file != null )
                    {
                        return file;
                    }
                }
            }
        }
        
        throw new MojoExecutionException("Could not find org.jboss.modules:modules, make sure it is included in your pom's dependencies");
    }

    /////////////////////////////////////////////////////////////
    //JBoss modules specific methods
    
    protected ForkConfiguration processForkConfiguration(ForkConfiguration forkConfiguration) throws MojoExecutionException {
        forkConfiguration.setJBossModulesJar(findJBossModulesJar().getAbsolutePath());
        forkConfiguration.setLogModule(logModule);
        forkConfiguration.setJaxpModule(jaxpModule);
        forkConfiguration.setLogConfiguration(logConfiguration);
        
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = roots.iterator() ; it.hasNext() ; ) {
            File file = new File( it.next() );            
            if (!file.exists()) {
                throw new MojoExecutionException("Roots value does not exist: " + file.getAbsolutePath());
            }
            if (sb.length() > 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append(file.getAbsolutePath());
        }
        
        if (sb.length() > 0) {
            sb.insert(0, File.pathSeparatorChar);
        }
        sb.insert(0, modulesDirectory);
        
        forkConfiguration.setJBossModuleRoots(sb.toString());
        
        return super.processForkConfiguration(forkConfiguration);
    }

    void createJBossModulesDirectory() throws MojoExecutionException {
        ModulesProcessor processor = new ModulesProcessor(
                getLog(), 
                project, 
                localRepository, 
                artifactFactory, 
                metadataSource, 
                artifactCollector, 
                testClassesDirectory, 
                classesDirectory, 
                moduleDefinitionFile,
                cleanModulesDirectory,
                modulesDirectory, 
                dependencyTreeBuilder);
        
        processor.createModulesDirectory();
    }    
}

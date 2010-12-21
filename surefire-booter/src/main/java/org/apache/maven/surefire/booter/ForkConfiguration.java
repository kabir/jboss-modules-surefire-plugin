package org.apache.maven.surefire.booter;

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

import org.apache.maven.surefire.util.UrlUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Configuration for forking tests.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:kenney@apache.org">Kenney Westerhof</a>
 */
public class ForkConfiguration
{
    public static final String FORK_ONCE = "once";

    public static final String FORK_ALWAYS = "always";

    public static final String FORK_NEVER = "never";

    private String forkMode;

    private boolean useSystemClassLoader;
    private boolean useManifestOnlyJar;

    private Properties systemProperties;

    private String jvmExecutable;

    private String argLine;

    private Map environmentVariables;

    private File workingDirectory;

    private boolean debug;
    
    private String debugLine;
    
    private String jbossModulesJar;
    
    private String logModule; 
    
    private File logConfiguration;
    
    private String jbossModuleRoots;

    public void setForkMode( String forkMode )
    {
        if ( "pertest".equalsIgnoreCase( forkMode ) )
        {
            this.forkMode = FORK_ALWAYS;
        }
        else if ( "none".equalsIgnoreCase( forkMode ) )
        {
            this.forkMode = FORK_NEVER;
        }
        else if ( forkMode.equals( FORK_NEVER ) || forkMode.equals( FORK_ONCE ) || forkMode.equals( FORK_ALWAYS ) )
        {
            this.forkMode = forkMode;
        }
        else
        {
            throw new IllegalArgumentException( "Fork mode " + forkMode + " is not a legal value" );
        }
    }

    public boolean isForking()
    {
        return !FORK_NEVER.equals( forkMode );
    }

    public void setUseSystemClassLoader( boolean useSystemClassLoader )
    {
        this.useSystemClassLoader = useSystemClassLoader;
    }

    public boolean isUseSystemClassLoader()
    {
        return useSystemClassLoader;
    }

    public void setSystemProperties( Properties systemProperties )
    {
        this.systemProperties = (Properties) systemProperties.clone();
    }

    public void setJvmExecutable( String jvmExecutable )
    {
        this.jvmExecutable = jvmExecutable;
    }

    public void setArgLine( String argLine )
    {
        this.argLine = argLine;
    }
    
    public void setDebugLine( String debugLine )
    {
        this.debugLine = debugLine;
    }

    public void setJBossModulesJar(String jbossModulesJar) {
        this.jbossModulesJar = jbossModulesJar;
    }
    
    public void setLogModule(String logModule) {
        this.logModule = logModule;
    }
    
    public void setLogConfiguration(File logConfiguration) {
        this.logConfiguration = logConfiguration;
    }

    public void setJBossModuleRoots(String jbossModuleRoots) {
        this.jbossModuleRoots = jbossModuleRoots;
    }
    
    public void setEnvironmentVariables( Map environmentVariables )
    {
        this.environmentVariables = new HashMap( environmentVariables );
    }

    public void setWorkingDirectory( File workingDirectory )
    {
        this.workingDirectory = workingDirectory;
    }

    public String getForkMode()
    {
        return forkMode;
    }

    public Properties getSystemProperties()
    {
        return systemProperties;
    }

    /**
     * @throws SurefireBooterForkException
     * @deprecated use the 2-arg alternative.
     */
    public Commandline createCommandLine( List classPath )
        throws SurefireBooterForkException
    {
        return createCommandLine( classPath, false );
    }

    public Commandline createCommandLine( List classPath, boolean useJar )
        throws SurefireBooterForkException
    {
        Commandline cli = new Commandline();

        cli.setExecutable( jvmExecutable );
        
        if ( argLine != null )
        {
            cli.createArg().setLine( argLine );
        }

        if ( environmentVariables != null )
        {
            Iterator iter = environmentVariables.keySet().iterator();

            while ( iter.hasNext() )
            {
                String key = (String) iter.next();

                String value = (String) environmentVariables.get( key );

                cli.addEnvironment( key, value );
            }
        }

        if ( debugLine != null && !"".equals( debugLine ) )
        {
            cli.createArg().setLine( debugLine );
        }

//        if ( useJar )
//        {
//            File jarFile;
//            try
//            {
//                jarFile = createJar( classPath );
//            }
//            catch ( IOException e )
//            {
//                throw new SurefireBooterForkException( "Error creating archive file", e );
//            }
//
//            cli.createArg().setValue( "-jar" );
//
//            cli.createArg().setValue( jarFile.getAbsolutePath() );
//        }
//        else
//        {
//            cli.createArg().setValue( "-classpath" );
//
//            cli.createArg().setValue( StringUtils.join( classPath.iterator(), File.pathSeparator ) );
//
//            cli.createArg().setValue( SurefireBooter.class.getName() );
//        }
        
        //Handle special system properties that need to be available in jboss-modules Main to set up logging, before
        //surefire reads the properties file        
        if (logConfiguration != null) {
            if (!logConfiguration.exists()) {
                throw new SurefireBooterForkException("Invalid value for -Dlogging.configuration. File not found: " + logConfiguration.getAbsolutePath());
            }
            
            try {
                cli.createArg().setValue("-Dlogging.configuration=" + logConfiguration.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new SurefireBooterForkException("Error creating URL from file");
            }
        }
        if (systemProperties != null && systemProperties.containsKey("org.jboss.boot.log.file")) {
            cli.createArg().setValue("-Dorg.jboss.boot.log.file=" + systemProperties.getProperty("org.jboss.boot.log.file"));
        }
        
        cli.createArg().setValue("-jar");
        cli.createArg().setValue(jbossModulesJar);
        cli.createArg().setValue("-mp");
        cli.createArg().setValue(jbossModuleRoots);
        if (logModule != null) {
            cli.createArg().setValue("-logmodule");
            cli.createArg().setValue(logModule);
        }
        cli.createArg().setValue("jboss.surefire.module");
        cli.setWorkingDirectory( workingDirectory.getAbsolutePath() );

        return cli;
    }

    /**
     * Create a jar with just a manifest containing a Main-Class entry for SurefireBooter and a Class-Path entry
     * for all classpath elements.
     *
     * @param classPath List&lt;String> of all classpath elements.
     * @return
     * @throws IOException
     */
    private File createJar( List classPath )
        throws IOException
    {
        File file = File.createTempFile( "surefirebooter", ".jar" );
        if ( !debug )
        {
            file.deleteOnExit();
        }
        FileOutputStream fos = new FileOutputStream( file );
        JarOutputStream jos = new JarOutputStream( fos );
        jos.setLevel( JarOutputStream.STORED );
        JarEntry je = new JarEntry( "META-INF/MANIFEST.MF" );
        jos.putNextEntry( je );

        Manifest man = new Manifest();

        // we can't use StringUtils.join here since we need to add a '/' to
        // the end of directory entries - otherwise the jvm will ignore them.
        String cp = "";
        for ( Iterator it = classPath.iterator(); it.hasNext(); )
        {
            String el = (String) it.next();
            // NOTE: if File points to a directory, this entry MUST end in '/'.
            cp += UrlUtils.getURL( new File( el ) ).toExternalForm() + " ";
        }

        man.getMainAttributes().putValue( "Manifest-Version", "1.0" );
        man.getMainAttributes().putValue( "Class-Path", cp.trim() );
        man.getMainAttributes().putValue( "Main-Class", SurefireBooter.class.getName() );

        man.write( jos );
        jos.close();

        return file;
    }

    public void setDebug( boolean debug )
    {
        this.debug = debug;
    }

    public boolean isDebug()
    {
        return debug;
    }

    public void setUseManifestOnlyJar( boolean useManifestOnlyJar )
    {
        this.useManifestOnlyJar = useManifestOnlyJar;
    }
    
    public boolean isUseManifestOnlyJar()
    {
        return useManifestOnlyJar;
    }
}

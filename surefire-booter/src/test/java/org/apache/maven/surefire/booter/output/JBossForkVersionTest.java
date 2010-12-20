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
package org.apache.maven.surefire.booter.output;

import org.apache.maven.surefire.jboss.config.Versions;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class JBossForkVersionTest extends AbstractOutputConsumerTestCase {

    public void testVersion() {
        String pomVersion = System.getProperty("version.in.pom");
        assertNotNull("No version in system property 'version.in.pom'", pomVersion);
        
        assertEquals("'version.in.pom' system property and Versions.PLUGIN_FORK_VERSION differ", pomVersion, Versions.PLUGIN_FORK_VERSION);
    }

    //Just override these so we're sure we get run
    @Override
    public void testConsumeFooterLine() {
    }

    @Override
    public void testConsumeHeaderLine() {
    }

    @Override
    public void testConsumeMessageLine() {
    }

    @Override
    public void testConsumeOutputLine() throws Exception {
    }

    @Override
    public void testTestSetCompleted() {
    }

    @Override
    public void testTestSetStarting() {
    }
}

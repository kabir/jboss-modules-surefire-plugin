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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class Module extends ChildElement {
    final String namespaceUri;
    
    Module(String namespaceUri) {
        super("module");
        this.namespaceUri = namespaceUri;
    }

    String getName() {
        String name = getAttribute("name");
        if (name == null) {
            throw new IllegalStateException("Module does not have a 'name' attribute");
        }
        return name;
    }

    void output(File dir) {
        if (!dir.exists() && !dir.isDirectory()) {
            throw new IllegalArgumentException("No directory called " + dir.getAbsolutePath());
        }
        File file = new File(dir, "module.xml");
        if (file.exists()) {
            file.delete();
        }

        PrintWriter writer;
        try {
            writer = new PrintWriter(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            output(writer, 0);
        } catch (IOException e) {

        } finally {
            try {
                writer.close();
            } catch (Exception ignore) {
            }
        }
    }

    void printNamespaceUri(PrintWriter writer) {
        writer.print(" xmlns=\"" + namespaceUri + "\"");
    }
}

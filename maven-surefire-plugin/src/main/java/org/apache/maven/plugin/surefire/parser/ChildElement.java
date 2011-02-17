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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
class ChildElement {
    private final String name;

    private final Map<String, String> attributes = new LinkedHashMap<String, String>();
    private final List<ChildElement> children = new ArrayList<ChildElement>();
    private final Set<String> replacementAttributes = new HashSet<String>();


    ChildElement(String name) {
        this.name = name;
    }

    boolean addAttribute(String name, String value) {

        if (value.startsWith("$") && value.endsWith("$") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
            replacementAttributes.add(name);
        }

        return attributes.put(name, value) != null;
    }

    void addChild(ChildElement child) {
        children.add(child);
    }

    String getAttribute(String name) {
        return attributes.get(name);
    }

    List<ChildElement> getChildren(){
        return children;
    }

    void findReplacementAttributes(List<MavenReplacement> replacements) {
        for (String replacementAttribute : replacementAttributes) {
            replacements.add(new MavenReplacement(this, replacementAttribute));
        }

        for (ChildElement child : children) {
            child.findReplacementAttributes(replacements);
        }
    }

    void output(XMLStreamWriter writer) throws XMLStreamException {
        String ns = getNamespaceUri();
        boolean hasChildren = children.size() > 0;
        
        if (hasChildren) {
            if (ns != null) {
                writer.writeStartElement("", name, ns);
                writer.writeNamespace("", ns);
            } else {
                writer.writeStartElement(name);
            }
        } else {
            if (ns != null) {
                writer.writeEmptyElement("", name, ns);
                writer.writeNamespace("", ns);
            } else {
                writer.writeEmptyElement(name);
            }
        }

        if (attributes.size() > 0) {
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                writer.writeAttribute(attribute.getKey(), attribute.getValue());
            }
        }
        
        if (hasChildren) {
            for (ChildElement child : children) {
                child.output(writer);
            }
            
            writer.writeEndElement();
        }
    }

    void indent(PrintWriter writer, int indent) throws IOException {
        for (int i = 0 ; i < indent ; i++) {
            writer.print("  ");
        }
    }
    
    String getNamespaceUri() {
        return null;
    }
}

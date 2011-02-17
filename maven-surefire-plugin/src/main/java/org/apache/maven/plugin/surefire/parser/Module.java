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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

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

        final OutputStream out;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        XMLStreamWriter writer;
        try {
            writer = new SimpleFormattingWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(out));
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        
        try {
            writer.writeStartDocument();
            output(writer);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                writer.close();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    String getNamespaceUri() {
        return namespaceUri;
    }
 
    private static class SimpleFormattingWriter implements XMLStreamWriter {
        final XMLStreamWriter delegate;
        int indent;
        
        public SimpleFormattingWriter(XMLStreamWriter delegate) {
            this.delegate = delegate;
        }

        public void writeStartElement(String localName) throws XMLStreamException {
            writeNewLine();
            delegate.writeStartElement(localName);
            indent++;
        }

        public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
            writeNewLine();
            delegate.writeStartElement(namespaceURI, localName);
            indent++;
        }

        public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
            writeNewLine();
            delegate.writeStartElement(prefix, localName, namespaceURI);
            indent++;
        }

        public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
            writeNewLine();
            delegate.writeEmptyElement(namespaceURI, localName);
        }

        public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
            writeNewLine();
            delegate.writeEmptyElement(prefix, localName, namespaceURI);
        }

        public void writeEmptyElement(String localName) throws XMLStreamException {
            writeNewLine();
            delegate.writeEmptyElement(localName);
        }

        public void writeEndElement() throws XMLStreamException {
            indent--;
            writeNewLine();
            delegate.writeEndElement();
        }

        public void writeEndDocument() throws XMLStreamException {
            writeNewLine();
            delegate.writeEndDocument();
        }

        public void close() throws XMLStreamException {
            delegate.close();
        }

        public void flush() throws XMLStreamException {
            delegate.flush();
        }

        public void writeAttribute(String localName, String value) throws XMLStreamException {
            delegate.writeAttribute(localName, value);
        }

        public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
            delegate.writeAttribute(prefix, namespaceURI, localName, value);
        }

        public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
            delegate.writeAttribute(namespaceURI, localName, value);
        }

        public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
            delegate.writeNamespace(prefix, namespaceURI);
        }

        public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
            delegate.writeDefaultNamespace(namespaceURI);
        }

        public void writeComment(String data) throws XMLStreamException {
            delegate.writeComment(data);
        }

        public void writeProcessingInstruction(String target) throws XMLStreamException {
            delegate.writeProcessingInstruction(target);
        }

        public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
            delegate.writeProcessingInstruction(target, data);
        }

        public void writeCData(String data) throws XMLStreamException {
            delegate.writeCData(data);
        }

        public void writeDTD(String dtd) throws XMLStreamException {
            delegate.writeDTD(dtd);
        }

        public void writeEntityRef(String name) throws XMLStreamException {
            delegate.writeEntityRef(name);
        }

        public void writeStartDocument() throws XMLStreamException {
            delegate.writeStartDocument();
        }

        public void writeStartDocument(String version) throws XMLStreamException {
            writeNewLine();
            delegate.writeStartDocument(version);
        }

        public void writeStartDocument(String encoding, String version) throws XMLStreamException {
            writeNewLine();
            delegate.writeStartDocument(encoding, version);
        }

        public void writeCharacters(String text) throws XMLStreamException {
            delegate.writeCharacters(text);
        }

        public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
            delegate.writeCharacters(text, start, len);
        }

        public String getPrefix(String uri) throws XMLStreamException {
            return delegate.getPrefix(uri);
        }

        public void setPrefix(String prefix, String uri) throws XMLStreamException {
            delegate.setPrefix(prefix, uri);
        }

        public void setDefaultNamespace(String uri) throws XMLStreamException {
            delegate.setDefaultNamespace(uri);
        }

        public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
            delegate.setNamespaceContext(context);
        }

        public NamespaceContext getNamespaceContext() {
            return delegate.getNamespaceContext();
        }

        public Object getProperty(String name) throws IllegalArgumentException {
            return delegate.getProperty(name);
        }
        
        private void writeNewLine() throws XMLStreamException {
            delegate.writeCharacters("\n");
            for (int i = 0 ; i < indent ; i++) {
                delegate.writeCharacters("    ");
            }
        }
    }
}

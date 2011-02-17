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
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A parser that parses the module-def.xml file. It only knows about the modules and module elements, and passes all attributes and elemets through.
 * The proper validation will be done by whatever version of jboss-modules is used.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class ModulesParser {
    private final File input;
    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final String NAMESPACE = "urn:jboss:surefire-module:1.0";
    private String targetNamespaceUri;
    private TestModuleDependencies testModuleDependencies;
    private TestModuleResources testModuleResources;

    enum Element {
        MODULES,
        TEST_MODULE_RESOURCES,
        TEST_MODULE_DEPENDENCIES,
        MODULE,
        
        // default unknown element
        UNKNOWN;


        private static final Map<QName, Element> elements;
        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName(NAMESPACE, "modules"), Element.MODULES);
            elementsMap.put(new QName(NAMESPACE, "test-module-resources"), Element.TEST_MODULE_RESOURCES);
            elementsMap.put(new QName(NAMESPACE, "test-module-dependencies"), Element.TEST_MODULE_DEPENDENCIES);
            elementsMap.put(new QName(NAMESPACE, "module"), Element.MODULE);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            final Element element = elements.get(qName);
            return element == null ? UNKNOWN : element;
        }
    }

    enum Attribute {
        NAME,
        TARGET_NAMESPACE,
        
        // default unknown attribute
        UNKNOWN;

        private static final Map<QName, Attribute> attributes;

        static {
            Map<QName, Attribute> attributesMap = new HashMap<QName, Attribute>();
            attributesMap.put(new QName("name"), NAME);
            attributesMap.put(new QName("targetNs"), TARGET_NAMESPACE);
            attributes = attributesMap;
        }

        static Attribute of(QName qName) {
            final Attribute attribute = attributes.get(qName);
            return attribute == null ? UNKNOWN : attribute;
        }
    }

    ModulesParser(File input) {
        this.input = input;
    }

    List<Module> parse(){
        final InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(input));
        } catch (FileNotFoundException f) {
            throw new RuntimeException("Could not find " + input, f);
        }

        try {
            return parse(in);
        } finally {
            safeClose(in);
        }
    }
    
    String getJBossModulesNamespaceUri() {
        return targetNamespaceUri;
    }
    
    TestModuleDependencies getTestModuleDependencies() {
        return testModuleDependencies;
    }
    
    TestModuleResources getTestModuleResources() {
        return testModuleResources;
    }

    private static void setIfSupported(XMLInputFactory inputFactory, String property, Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    List<Module> parse(InputStream in){
        try {
            final XMLInputFactory inputFactory = INPUT_FACTORY;
            setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
            setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            final XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);
            try {
                return parseDocument( streamReader);
            } finally {
                safeClose(streamReader);
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException("Error creating modules from " + input.getPath(), e);
        }
    }

    private List<Module> parseDocument(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_DOCUMENT:
                    return parseRootElement(reader);
                case XMLStreamConstants.START_ELEMENT:
                    if (Element.of(reader.getName()) != Element.MODULES) {
                        throw unexpectedContent(reader);
                    }
                    List<Module> modules = parseModules(reader);
                    parseEndDocument(reader);
                    return modules;
                default:
                    throw unexpectedContent(reader);
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private List<Module> parseRootElement(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT:
                    Element element = Element.of(reader.getName());
                    if (Element.of(reader.getName()) != Element.MODULES) {
                        throw unexpectedContent(reader);
                    }
                    List<Module> modules = parseModules(reader);
                    parseEndDocument(reader);
                    return modules;
                default:
                    throw unexpectedContent(reader);
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    
    
    private List<Module> parseModules(XMLStreamReader reader) throws XMLStreamException {
        List<Module> modules = new ArrayList<Module>();
                
        //targetNamespaceUri = reader.getNamespaceURI();
        int attributes = reader.getAttributeCount();
        
        for (int i = 0 ; i < attributes ; i++) {
            Attribute attribute = Attribute.of(reader.getAttributeName(i));
            if (attribute != Attribute.TARGET_NAMESPACE) {
                throw unexpectedContent(reader);
            }
            targetNamespaceUri = reader.getAttributeValue(i);
        }
        
        if (targetNamespaceUri == null) {
            missingAttributes(reader.getLocation(), Collections.singleton(Attribute.TARGET_NAMESPACE));
        }
        
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
            case XMLStreamConstants.END_ELEMENT:
                if (Element.of(reader.getName()) != Element.MODULES) {
                    throw unexpectedContent(reader);
                }
                return modules;
            case XMLStreamConstants.START_ELEMENT:
                Element element = Element.of(reader.getName());
                switch (element) {
                case MODULE:
                    Module module = new Module(targetNamespaceUri);
                    addAttributes(module, reader);
                    parseChildElement(reader.getName(), module, reader);
                    modules.add(module);
                    break;
                case TEST_MODULE_DEPENDENCIES:
                    if (testModuleDependencies != null) {
                        throw new XMLStreamException("There already was a " + reader.getName() + " entry", reader.getLocation());
                    }
                    testModuleDependencies = new TestModuleDependencies();
                    parseChildElement(reader.getName(), testModuleDependencies, reader);
                    break;
                case TEST_MODULE_RESOURCES:
                    if (testModuleResources != null) {
                        throw new XMLStreamException("There already was a " + reader.getName() + " entry", reader.getLocation());
                    }
                    testModuleResources = new TestModuleResources();
                    parseChildElement(reader.getName(), testModuleResources, reader);
                    break;
                default:
                    throw unexpectedContent(reader);
                }
                break;
            default:
                throw unexpectedContent(reader);
            }
        }
        return modules;
    }

    private void parseChildElement(QName name, ChildElement parent, XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
            case XMLStreamConstants.END_ELEMENT:
                if (!reader.getName().equals(name)) {
                    throw unexpectedContent(reader);
                }
                return;
            case XMLStreamConstants.START_ELEMENT:
                ChildElement child = new ChildElement(reader.getName().getLocalPart());
                parent.addChild(child);
                addAttributes(child, reader);
                parseChildElement(reader.getName(), child, reader);
            break;
            default:
                throw unexpectedContent(reader);
            }
        }
    }

    private void addAttributes(ChildElement element, XMLStreamReader reader) throws XMLStreamException {
        int count = reader.getAttributeCount();
        for (int i = 0 ; i < count ; i++) {
            String name = reader.getAttributeName(i).getLocalPart();
            String value = reader.getAttributeValue(i);
            element.addAttribute(name, value);
        }
    }

    private void parseEndDocument(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.END_DOCUMENT: {
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    if (! reader.isWhiteSpace()) {
                        throw unexpectedContent(reader);
                    }
                    // ignore
                    break;
                }
                case XMLStreamConstants.COMMENT:
                case XMLStreamConstants.SPACE: {
                    // ignore
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        return;
    }

    private XMLStreamException endOfDocument(final Location location) {
        return new XMLStreamException("Unexpected end of document", location);
    }

    private XMLStreamException unexpectedContent(final XMLStreamReader reader) {
        final String kind;
        switch (reader.getEventType()) {
            case XMLStreamConstants.ATTRIBUTE: kind = "attribute"; break;
            case XMLStreamConstants.CDATA: kind = "cdata"; break;
            case XMLStreamConstants.CHARACTERS: kind = "characters"; break;
            case XMLStreamConstants.COMMENT: kind = "comment"; break;
            case XMLStreamConstants.DTD: kind = "dtd"; break;
            case XMLStreamConstants.END_DOCUMENT: kind = "document end"; break;
            case XMLStreamConstants.END_ELEMENT: kind = "element end"; break;
            case XMLStreamConstants.ENTITY_DECLARATION: kind = "entity declaration"; break;
            case XMLStreamConstants.ENTITY_REFERENCE: kind = "entity ref"; break;
            case XMLStreamConstants.NAMESPACE: kind = "namespace"; break;
            case XMLStreamConstants.NOTATION_DECLARATION: kind = "notation declaration"; break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION: kind = "processing instruction"; break;
            case XMLStreamConstants.SPACE: kind = "whitespace"; break;
            case XMLStreamConstants.START_DOCUMENT: kind = "document start"; break;
            case XMLStreamConstants.START_ELEMENT: kind = "element start"; break;
            default: kind = "unknown"; break;
        }
        final StringBuilder b = new StringBuilder("Unexpected content of type '").append(kind).append('\'');
        if (reader.hasName()) {
            b.append(" named '").append(reader.getName()).append('\'');
        }
        if (reader.hasText()) {
            b.append(", text is: '").append(reader.getText()).append('\'');
        }
        return new XMLStreamException(b.toString(), reader.getLocation());
    }

    private static XMLStreamException missingAttributes(final Location location, final Set<Attribute> required) {
        final StringBuilder b = new StringBuilder("Missing one or more required attributes:");
        for (Attribute attribute : required) {
            b.append(' ').append(attribute);
        }
        return new XMLStreamException(b.toString(), location);
    }

    private void safeClose(Closeable c) {
        try {
            c.close();
        } catch (Exception ignore) {
        }
    }

    private void safeClose(XMLStreamReader c) {
        try {
            c.close();
        } catch (Exception ignore) {
        }
    }

    public static void main(String[] args) {
        File file = new File("/Users/kabir/sourcecontrol/surefire-2.6-git/git/test/src/test/resources/modules/module-def.xml");
        ModulesParser parser = new ModulesParser(file);
        parser.parse();
    }
}

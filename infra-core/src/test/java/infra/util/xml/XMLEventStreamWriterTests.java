/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Modifications Copyright 2017 - 2026 the TODAY authors.

package infra.util.xml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Node;
import org.xmlunit.util.Predicate;

import java.io.StringWriter;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.XMLEvent;

import infra.core.testfixture.xml.XmlContent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class XMLEventStreamWriterTests {

  private static final String XML =
          "<?pi content?><root xmlns='namespace'><prefix:child xmlns:prefix='namespace2'><!--comment-->content</prefix:child></root>";

  private XMLEventStreamWriter streamWriter;

  private StringWriter stringWriter;

  @BeforeEach
  void createStreamReader() throws Exception {
    stringWriter = new StringWriter();
    XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    XMLEventWriter eventWriter = outputFactory.createXMLEventWriter(stringWriter);
    streamWriter = new XMLEventStreamWriter(eventWriter, XMLEventFactory.newInstance());
  }

  @Test
  void write() throws Exception {
    streamWriter.writeStartDocument();
    streamWriter.writeProcessingInstruction("pi", "content");
    streamWriter.writeStartElement("namespace", "root");
    streamWriter.writeDefaultNamespace("namespace");
    streamWriter.writeStartElement("prefix", "child", "namespace2");
    streamWriter.writeNamespace("prefix", "namespace2");
    streamWriter.writeComment("comment");
    streamWriter.writeCharacters("content");
    streamWriter.writeEndElement();
    streamWriter.writeEndElement();
    streamWriter.writeEndDocument();

    Predicate<Node> nodeFilter = n -> n.getNodeType() != Node.DOCUMENT_TYPE_NODE && n.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE;
    assertThat(XmlContent.from(stringWriter)).isSimilarTo(XML, nodeFilter);
  }

  @Test
  void writesEmptyElementsAttributesAndTextInOrder() throws Exception {
    streamWriter.writeStartDocument("1.0");
    streamWriter.writeStartElement("root");
    streamWriter.writeNamespace("p", "urn:sample");
    streamWriter.writeNamespace("q", "urn:other");
    streamWriter.writeEmptyElement("first");
    streamWriter.writeAttribute("id", "1");
    streamWriter.writeEmptyElement("urn:sample", "second");
    streamWriter.writeDefaultNamespace("urn:sample");
    streamWriter.writeEmptyElement("p", "third", "urn:sample");
    streamWriter.writeAttribute("q", "urn:other", "label", "sample");
    streamWriter.writeCharacters("xtexty".toCharArray(), 1, 4);
    streamWriter.writeCData("<literal>");
    streamWriter.writeProcessingInstruction("sample");
    streamWriter.writeEndElement();
    streamWriter.writeEndDocument();
    streamWriter.flush();
    streamWriter.close();

    assertThat(XmlContent.from(stringWriter)).isSimilarTo(
            "<root xmlns:p='urn:sample' xmlns:q='urn:other'><first id='1'/><second xmlns='urn:sample'/>"
                    + "<p:third q:label='sample'/>text<![CDATA[<literal>]]><?sample ?></root>",
            node -> node.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE);
  }

  @Test
  void explicitEncodingAndNamespacedAttributeArePreserved() throws Exception {
    XMLEventWriter delegate = mock(XMLEventWriter.class);
    var writer = new XMLEventStreamWriter(delegate, XMLEventFactory.newInstance());
    writer.writeStartDocument("UTF-16", "1.1");
    writer.writeEmptyElement("element");
    writer.writeAttribute("urn:sample", "name", "value");
    writer.close();

    var captor = ArgumentCaptor.forClass(XMLEvent.class);
    verify(delegate, times(4)).add(captor.capture());
    List<XMLEvent> events = captor.getAllValues();
    var document = (StartDocument) events.get(0);
    assertThat(document.getCharacterEncodingScheme()).isEqualTo("UTF-16");
    assertThat(document.getVersion()).isEqualTo("1.1");
    assertThat(events.get(1).asStartElement().getName()).isEqualTo(new QName("element"));
    var attribute = (Attribute) events.get(2);
    assertThat(attribute.getName()).isEqualTo(new QName("urn:sample", "name"));
    assertThat(attribute.getValue()).isEqualTo("value");
    assertThat(events.get(3).asEndElement().getName()).isEqualTo(new QName("element"));
    verify(delegate).close();
  }

  @Test
  void delegatesNamespaceConfigurationAndFlush() throws Exception {
    XMLEventWriter delegate = mock(XMLEventWriter.class);
    NamespaceContext context = mock(NamespaceContext.class);
    given(delegate.getNamespaceContext()).willReturn(context);
    given(delegate.getPrefix("urn:sample")).willReturn("p");
    var writer = new XMLEventStreamWriter(delegate, XMLEventFactory.newInstance());

    writer.setNamespaceContext(context);
    writer.setPrefix("p", "urn:sample");
    writer.setDefaultNamespace("urn:default");
    writer.flush();

    assertThat(writer.getNamespaceContext()).isSameAs(context);
    assertThat(writer.getPrefix("urn:sample")).isEqualTo("p");
    assertThatIllegalArgumentException().isThrownBy(() -> writer.getProperty("unknown"));
    verify(delegate).setNamespaceContext(context);
    verify(delegate).setPrefix("p", "urn:sample");
    verify(delegate).setDefaultNamespace("urn:default");
    verify(delegate).flush();
  }

  @Test
  void writesDocumentTypeAndEntityReferenceAsEvents() throws Exception {
    XMLEventWriter delegate = mock(XMLEventWriter.class);
    var factory = mock(XMLEventFactory.class);
    var dtd = mock(DTD.class);
    var reference = mock(EntityReference.class);
    given(factory.createDTD("<!DOCTYPE root>")).willReturn(dtd);
    given(factory.createEntityReference("amp", null)).willReturn(reference);
    var writer = new XMLEventStreamWriter(delegate, factory);

    writer.writeDTD("<!DOCTYPE root>");
    writer.writeEntityRef("amp");

    verify(delegate).add(dtd);
    verify(delegate).add(reference);
  }

}

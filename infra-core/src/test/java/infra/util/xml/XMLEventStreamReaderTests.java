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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Node;
import org.xmlunit.util.Predicate;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;

import infra.core.testfixture.xml.XmlContent;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

class XMLEventStreamReaderTests {

  private static final String XML =
          "<?pi content?><root xmlns='namespace'><prefix:child xmlns:prefix='namespace2'>content</prefix:child></root>";

  private XMLEventStreamReader streamReader;

  @BeforeEach
  void createStreamReader() throws Exception {
    XMLInputFactory inputFactory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = inputFactory.createXMLEventReader(new StringReader(XML));
    streamReader = new XMLEventStreamReader(eventReader);
  }

  @Test
  void readAll() throws Exception {
    while (streamReader.hasNext()) {
      streamReader.next();
    }
  }

  @Test
  void readCorrect() throws Exception {
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    StAXSource source = new StAXSource(streamReader);
    StringWriter writer = new StringWriter();
    transformer.transform(source, new StreamResult(writer));
    Predicate<Node> nodeFilter = n ->
            n.getNodeType() != Node.DOCUMENT_TYPE_NODE && n.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE;
    assertThat(XmlContent.from(writer)).isSimilarTo(XML, nodeFilter);
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
          0, content
          1, ontent
          4, ent
          6, t
          7, ''
          """)
  void getTextCharactersHonorsSourceStart(int sourceStart, String expected) throws Exception {
    advanceToCharacters();

    // text node is "content" (7 chars); request an oversized buffer to ensure
    // getTextCharacters(sourceStart, ...) does not read past the source
    char[] target = new char[10];
    int count = streamReader.getTextCharacters(sourceStart, target, 0, 10);

    assertThat(count).isEqualTo(expected.length());
    assertThat(new String(target, 0, count)).isEqualTo(expected);
  }

  private void advanceToCharacters() throws Exception {
    while (streamReader.getEventType() != XMLStreamConstants.CHARACTERS) {
      streamReader.next();
    }
  }

  @Test
  void requireValidatesNamespaceAndLocalName() throws Exception {
    advanceToStartElement("root");

    streamReader.require(XMLStreamConstants.START_ELEMENT, null, "root");
    streamReader.require(XMLStreamConstants.START_ELEMENT, "namespace", "root");
    streamReader.require(XMLStreamConstants.START_ELEMENT, "namespace", null);

    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() ->
            streamReader.require(XMLStreamConstants.START_ELEMENT, null, "wrong"));
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() ->
            streamReader.require(XMLStreamConstants.START_ELEMENT, "wrong-namespace", "root"));
  }

  private void advanceToStartElement(String localName) throws Exception {
    while (streamReader.getEventType() != XMLStreamConstants.START_ELEMENT ||
            !streamReader.getLocalName().equals(localName)) {
      streamReader.next();
    }
  }

  @Test
  void exposesDocumentAndProcessingInstructionMetadata() throws Exception {
    assertThat(streamReader.getVersion()).isNotNull();
    assertThat(streamReader.getEncoding()).isNull();
    assertThat(streamReader.getCharacterEncodingScheme()).isNull();
    assertThat(streamReader.standaloneSet()).isFalse();
    assertThat(streamReader.isStandalone()).isFalse();
    assertThat(streamReader.getLocation()).isNotNull();
    assertThat(streamReader.hasName()).isFalse();
    assertThat(streamReader.hasText()).isFalse();
    assertThatIllegalStateException().isThrownBy(streamReader::getName);
    assertThatIllegalStateException().isThrownBy(streamReader::getNamespaceURI);
    assertThatIllegalStateException().isThrownBy(streamReader::getPrefix);
    assertThatIllegalStateException().isThrownBy(streamReader::getPITarget);
    assertThatIllegalStateException().isThrownBy(streamReader::getPIData);
    assertThatIllegalStateException().isThrownBy(streamReader::getText);
    assertThatIllegalStateException().isThrownBy(streamReader::getAttributeCount);
    assertThatIllegalStateException().isThrownBy(() -> streamReader.getAttributeName(0));
    assertThatIllegalStateException().isThrownBy(streamReader::getNamespaceContext);
    assertThatIllegalStateException().isThrownBy(streamReader::getNamespaceCount);
    assertThatIllegalStateException().isThrownBy(() -> streamReader.getNamespacePrefix(0));
    assertThat(streamReader.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE)).isEqualTo(true);
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(streamReader::getElementText);
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> streamReader.require(XMLStreamConstants.END_ELEMENT, null, null));
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> streamReader.require(XMLStreamConstants.START_DOCUMENT, null, "root"));

    assertThat(streamReader.next()).isEqualTo(XMLStreamConstants.PROCESSING_INSTRUCTION);
    assertThat(streamReader.getPITarget()).isEqualTo("pi");
    assertThat(streamReader.getPIData()).isEqualTo("content");
    assertThat(streamReader.getVersion()).isNull();
    assertThatIllegalStateException().isThrownBy(streamReader::standaloneSet);
    assertThatIllegalStateException().isThrownBy(streamReader::isStandalone);
  }

  @Test
  void readsAttributesAndNamespaceDeclarations() throws Exception {
    var factory = XMLInputFactory.newInstance();
    var reader = new XMLEventStreamReader(factory.createXMLEventReader(new StringReader(
            "<p:root xmlns:p='urn:root' xmlns:q='urn:attr' plain='one' q:value='two'/>")));
    assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
    assertThat(reader.isStartElement()).isTrue();
    assertThat(reader.hasName()).isTrue();
    assertThat(reader.getName()).isEqualTo(new QName("urn:root", "root", "p"));
    assertThat(reader.getPrefix()).isEqualTo("p");
    assertThat(reader.getNamespaceURI()).isEqualTo("urn:root");
    assertThat(reader.getNamespaceURI("q")).isEqualTo("urn:attr");
    assertThat(reader.getAttributeCount()).isEqualTo(2);
    assertThat(reader.getAttributeValue(null, "plain")).isEqualTo("one");
    assertThat(reader.getAttributeValue("urn:attr", "value")).isEqualTo("two");
    assertThat(reader.getAttributeValue("wrong", "value")).isNull();
    int index = reader.getAttributeName(0).getLocalPart().equals("value") ? 0 : 1;
    assertThat(reader.getAttributeLocalName(index)).isEqualTo("value");
    assertThat(reader.getAttributePrefix(index)).isEqualTo("q");
    assertThat(reader.getAttributeNamespace(index)).isEqualTo("urn:attr");
    assertThat(reader.getAttributeType(index)).isEqualTo("CDATA");
    assertThat(reader.isAttributeSpecified(index)).isTrue();
    assertThatIllegalArgumentException().isThrownBy(() -> reader.getAttributeName(2));
    assertThat(reader.getNamespaceCount()).isEqualTo(2);
    assertThat(List.of(reader.getNamespacePrefix(0), reader.getNamespacePrefix(1))).containsExactlyInAnyOrder("p", "q");
    assertThat(List.of(reader.getNamespaceURI(0), reader.getNamespaceURI(1))).containsExactlyInAnyOrder("urn:root", "urn:attr");
    assertThatIllegalArgumentException().isThrownBy(() -> reader.getNamespaceURI(2));
    assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
    assertThat(reader.isEndElement()).isTrue();
    assertThat(reader.getNamespaceCount()).isEqualTo(2);
    assertThat(List.of(reader.getNamespaceURI(0), reader.getNamespaceURI(1))).containsExactlyInAnyOrder("urn:root", "urn:attr");
    reader.next();
    assertThat(reader.hasNext()).isFalse();
    reader.close();
  }

  @Test
  void readsTextAndSkipsCommentsAndInstructions() throws Exception {
    var f = XMLEventFactory.newInstance();
    var reader = events(f.createStartElement("", "", "sample"), f.createCharacters("first"),
            f.createCData("<second>"), f.createSpace(" "), f.createComment("ignored"),
            f.createProcessingInstruction("sample", "ignored"), f.createEndElement("", "", "sample"));
    assertThat(reader.getElementText()).isEqualTo("first<second> ");

    reader = events(f.createComment("comment"), f.createCharacters("text"));
    assertThat(reader.hasText()).isTrue();
    assertThat(reader.getText()).isEqualTo("comment");
    reader.next();
    assertThat(reader.isCharacters()).isTrue();
    assertThat(reader.getTextStart()).isZero();
    assertThat(reader.getTextLength()).isEqualTo(4);
    assertThat(reader.getTextCharacters()).containsExactly('t', 'e', 'x', 't');
  }

  @Test
  void rejectsInvalidElementTextAndTagSequences() throws Exception {
    var f = XMLEventFactory.newInstance();
    f.setLocation(mock(Location.class));
    var start = f.createStartElement("", "", "sample");
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> events(start, start).getElementText());
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> events(start, f.createEndDocument()).getElementText());
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> events(start, f.createStartDocument()).getElementText());
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> events(start, f.createCharacters("non whitespace")).nextTag());
    var reader = events(f.createStartDocument(), f.createComment("ignored"),
            f.createProcessingInstruction("sample", "ignored"), start);
    assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
  }

  private static XMLEventStreamReader events(XMLEvent... events) throws XMLStreamException {
    return new XMLEventStreamReader(new ListBasedXMLEventReader(List.of(events)));
  }

}

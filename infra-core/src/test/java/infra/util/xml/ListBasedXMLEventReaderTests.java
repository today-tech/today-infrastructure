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

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import infra.core.testfixture.xml.XmlContent;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Arjen Poutsma
 * @author Andrzej Hołowko
 */
class ListBasedXMLEventReaderTests {

  @Test
  void iteratorTranslatesCheckedReadFailure() throws Exception {
    var reader = org.mockito.Mockito.mock(AbstractXMLEventReader.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    org.mockito.BDDMockito.given(reader.nextEvent()).willThrow(new XMLStreamException("read failed"));
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(reader::next).withMessage("read failed");
  }

  @Test
  void iterationAndCloseContracts() throws Exception {
    var events = readEvents("<sample/>");
    var reader = new ListBasedXMLEventReader(events);
    assertThat(reader.peek()).isSameAs(events.get(0));
    assertThat(reader.next()).isSameAs(events.get(0));
    while (reader.hasNext()) {
      reader.nextEvent();
    }
    assertThat(reader.peek()).isNull();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(reader::nextEvent);
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(reader::remove);
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> reader.getProperty("unknown"));
    reader.close();
    assertThat(reader.hasNext()).isFalse();
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(reader::nextTag);
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(reader::getElementText);
  }

  @Test
  void nextTagSkipsNonElementEventsAndRejectsText() throws Exception {
    var factory = XMLEventFactory.newInstance();
    var start = factory.createStartElement("", "", "sample");
    var end = factory.createEndElement("", "", "sample");
    var reader = new ListBasedXMLEventReader(List.of(factory.createComment("comment"),
            factory.createProcessingInstruction("sample", ""), factory.createSpace(" "),
            factory.createCData(" "), start, end, factory.createEndDocument()));
    assertThat(reader.nextTag()).isSameAs(start);
    assertThat(reader.nextTag()).isSameAs(end);
    assertThat(reader.nextTag()).isNull();
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() ->
            new ListBasedXMLEventReader(List.of(factory.createCharacters("text"))).nextTag());
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() ->
            new ListBasedXMLEventReader(List.of(factory.createStartDocument())).nextTag());
  }

  @Test
  void elementTextRejectsNestedElements() throws Exception {
    var reader = new ListBasedXMLEventReader(readEvents("<sample><nested/></sample>"));
    reader.nextEvent();
    reader.nextEvent();
    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(reader::getElementText);
  }

  private final XMLInputFactory inputFactory = XMLInputFactory.newInstance();

  private final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();

  @Test
  void standard() throws Exception {
    String xml = "<foo><bar>baz</bar></foo>";
    List<XMLEvent> events = readEvents(xml);

    ListBasedXMLEventReader reader = new ListBasedXMLEventReader(events);

    StringWriter resultWriter = new StringWriter();
    XMLEventWriter writer = this.outputFactory.createXMLEventWriter(resultWriter);
    writer.add(reader);

    assertThat(XmlContent.from(resultWriter)).isSimilarTo(xml);
  }

  @Test
  void getElementText() throws Exception {
    String xml = "<foo><bar>baz</bar></foo>";
    List<XMLEvent> events = readEvents(xml);

    ListBasedXMLEventReader reader = new ListBasedXMLEventReader(events);

    assertThat(reader.nextEvent().getEventType()).isEqualTo(START_DOCUMENT);
    assertThat(reader.nextEvent().getEventType()).isEqualTo(START_ELEMENT);
    assertThat(reader.nextEvent().getEventType()).isEqualTo(START_ELEMENT);
    assertThat(reader.getElementText()).isEqualTo("baz");
    assertThat(reader.nextEvent().getEventType()).isEqualTo(END_ELEMENT);
    assertThat(reader.nextEvent().getEventType()).isEqualTo(END_DOCUMENT);
  }

  @Test
  void getElementTextThrowsExceptionAtWrongPosition() throws Exception {
    String xml = "<foo><bar>baz</bar></foo>";
    List<XMLEvent> events = readEvents(xml);

    ListBasedXMLEventReader reader = new ListBasedXMLEventReader(events);

    assertThat(reader.nextEvent().getEventType()).isEqualTo(START_DOCUMENT);

    assertThatExceptionOfType(XMLStreamException.class).isThrownBy(
                    reader::getElementText)
            .withMessageStartingWith("Not at START_ELEMENT");
  }

  private List<XMLEvent> readEvents(String xml) throws XMLStreamException {
    XMLEventReader reader = this.inputFactory.createXMLEventReader(new StringReader(xml));
    List<XMLEvent> events = new ArrayList<>();
    while (reader.hasNext()) {
      events.add(reader.nextEvent());
    }
    return events;
  }

}

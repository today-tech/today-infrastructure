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
import java.util.List;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author Arjen Poutsma
 */
class StaxUtilsTests {

  @Test
  void staxSourcesRetainReaderForBothAdapterTypes() throws Exception {
    var factory = XMLInputFactory.newInstance();
    var streamReader = factory.createXMLStreamReader(new StringReader("<sample/>"));
    var eventReader = factory.createXMLEventReader(new StringReader("<sample/>"));
    for (var source : List.of(StaxUtils.createStaxSource(streamReader), StaxUtils.createCustomStaxSource(streamReader))) {
      assertThat(StaxUtils.getXMLStreamReader(source)).isSameAs(streamReader);
      assertThat(StaxUtils.getXMLEventReader(source)).isNull();
    }
    for (var source : List.of(StaxUtils.createStaxSource(eventReader), StaxUtils.createCustomStaxSource(eventReader))) {
      assertThat(StaxUtils.getXMLEventReader(source)).isSameAs(eventReader);
      assertThat(StaxUtils.getXMLStreamReader(source)).isNull();
    }
    assertThatIllegalArgumentException().isThrownBy(() -> StaxUtils.getXMLStreamReader(new StreamSource()));
    assertThatIllegalArgumentException().isThrownBy(() -> StaxUtils.getXMLEventReader(new StreamSource()));
    assertThat(StaxUtils.createXMLReader(streamReader)).isInstanceOf(StaxStreamXMLReader.class);
    assertThat(StaxUtils.createXMLReader(eventReader)).isInstanceOf(StaxEventXMLReader.class);
    assertThat(StaxUtils.createEventStreamReader(eventReader).getEventType()).isEqualTo(javax.xml.stream.XMLStreamConstants.START_DOCUMENT);
  }

  @Test
  void staxResultsRetainWriterForBothAdapterTypes() throws Exception {
    var factory = XMLOutputFactory.newInstance();
    var streamWriter = factory.createXMLStreamWriter(new StringWriter());
    var eventWriter = factory.createXMLEventWriter(new StringWriter());
    for (var result : List.of(StaxUtils.createStaxResult(streamWriter), StaxUtils.createCustomStaxResult(streamWriter))) {
      assertThat(StaxUtils.getXMLStreamWriter(result)).isSameAs(streamWriter);
      assertThat(StaxUtils.getXMLEventWriter(result)).isNull();
    }
    for (var result : List.of(StaxUtils.createStaxResult(eventWriter), StaxUtils.createCustomStaxResult(eventWriter))) {
      assertThat(StaxUtils.getXMLEventWriter(result)).isSameAs(eventWriter);
      assertThat(StaxUtils.getXMLStreamWriter(result)).isNull();
    }
    assertThatIllegalArgumentException().isThrownBy(() -> StaxUtils.getXMLStreamWriter(new StreamResult()));
    assertThatIllegalArgumentException().isThrownBy(() -> StaxUtils.getXMLEventWriter(new StreamResult()));
    assertThat(StaxUtils.createContentHandler(streamWriter)).isInstanceOf(StaxStreamHandler.class);
    assertThat(StaxUtils.createContentHandler(eventWriter)).isInstanceOf(StaxEventHandler.class);
    assertThat(StaxUtils.createEventStreamWriter(eventWriter)).isInstanceOf(XMLEventStreamWriter.class);
    assertThat(StaxUtils.createEventStreamWriter(eventWriter, XMLEventFactory.newInstance())).isInstanceOf(XMLEventStreamWriter.class);
    var event = XMLEventFactory.newInstance().createStartDocument();
    assertThat(StaxUtils.createXMLEventReader(List.of(event)).nextEvent()).isSameAs(event);
  }

  @Test
  void defensiveFactoryDisablesExternalResolution() throws Exception {
    var factory = StaxUtils.createDefensiveInputFactory();
    assertThat(factory.getProperty(XMLInputFactory.SUPPORT_DTD)).isEqualTo(false);
    assertThat(factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)).isEqualTo(false);
    try (var stream = (java.io.InputStream) factory.getXMLResolver().resolveEntity(null, null, null, null)) {
      assertThat(stream.read()).isEqualTo(-1);
    }
  }

  @Test
  void isStaxSourceInvalid() throws Exception {
    assertThat(StaxUtils.isStaxSource(new DOMSource())).as("A StAX Source").isFalse();
    assertThat(StaxUtils.isStaxSource(new SAXSource())).as("A StAX Source").isFalse();
    assertThat(StaxUtils.isStaxSource(new StreamSource())).as("A StAX Source").isFalse();
  }

  @Test
  void isStaxSource() throws Exception {
    XMLInputFactory inputFactory = XMLInputFactory.newInstance();
    String expected = "<element/>";
    XMLStreamReader streamReader = inputFactory.createXMLStreamReader(new StringReader(expected));
    Source source = StaxUtils.createCustomStaxSource(streamReader);

    assertThat(StaxUtils.isStaxSource(source)).as("Not a StAX Source").isTrue();
  }

  @Test
  void isStaxSourceJaxp14() throws Exception {
    XMLInputFactory inputFactory = XMLInputFactory.newInstance();
    String expected = "<element/>";
    XMLStreamReader streamReader = inputFactory.createXMLStreamReader(new StringReader(expected));
    StAXSource source = new StAXSource(streamReader);

    assertThat(StaxUtils.isStaxSource(source)).as("Not a StAX Source").isTrue();
  }

  @Test
  void isStaxResultInvalid() throws Exception {
    assertThat(StaxUtils.isStaxResult(new DOMResult())).as("A StAX Result").isFalse();
    assertThat(StaxUtils.isStaxResult(new SAXResult())).as("A StAX Result").isFalse();
    assertThat(StaxUtils.isStaxResult(new StreamResult())).as("A StAX Result").isFalse();
  }

  @Test
  void isStaxResult() throws Exception {
    XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(new StringWriter());
    Result result = StaxUtils.createCustomStaxResult(streamWriter);

    assertThat(StaxUtils.isStaxResult(result)).as("Not a StAX Result").isTrue();
  }

  @Test
  void isStaxResultJaxp14() throws Exception {
    XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(new StringWriter());
    StAXResult result = new StAXResult(streamWriter);

    assertThat(StaxUtils.isStaxResult(result)).as("Not a StAX Result").isTrue();
  }

}

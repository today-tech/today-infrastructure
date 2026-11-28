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

package infra.context.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;

import infra.beans.FatalBeanException;
import infra.context.ConfigurableApplicationContext;
import infra.context.support.ClassPathXmlApplicationContext;
import infra.context.support.GenericXmlApplicationContext;
import infra.context.support.FileSystemXmlApplicationContext;
import infra.context.support.MockEnvironment;
import infra.core.io.ClassPathResource;
import infra.core.io.ByteArrayResource;
import infra.core.io.FileSystemResource;
import infra.util.PlaceholderResolutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/3/9 17:51
 */
public class ContextNamespaceHandlerTests {

  @Test
  void fileSystemContextLoadsXmlAndSupportsDelayedRefresh(@TempDir Path directory) throws Exception {
    var xml = Files.write(directory.resolve("sample.xml"), contextXml("""
            <bean id="sample" class="java.lang.String"><constructor-arg value="value"/></bean>
            """).getContentAsByteArray());
    try (var parent = new GenericXmlApplicationContext();
            var empty = new FileSystemXmlApplicationContext();
            var child = new FileSystemXmlApplicationContext(parent);
            var single = new FileSystemXmlApplicationContext(xml.toUri().toString());
            var multiple = new FileSystemXmlApplicationContext(new String[] { xml.toUri().toString() });
            var withParent = new FileSystemXmlApplicationContext(new String[] { xml.toUri().toString() }, parent);
            var delayed = new FileSystemXmlApplicationContext(new String[] { xml.toUri().toString() }, false)) {
      assertThat(empty.getResource("/sample.xml")).isEqualTo(new FileSystemResource("sample.xml"));
      assertThat(empty.getResource("sample.xml")).isEqualTo(new FileSystemResource("sample.xml"));
      assertThat(child.getParent()).isSameAs(parent);
      assertThat(withParent.getParent()).isSameAs(parent);
      assertThat(single.getBean("sample")).isEqualTo("value");
      assertThat(multiple.getBean("sample")).isEqualTo("value");
      assertThat(delayed.isActive()).isFalse();
      delayed.refresh();
      assertThat(delayed.getBean("sample")).isEqualTo("value");
    }
  }

  @Test
  void loadTimeWeaverDefinitionsRespectExplicitAndAutomaticWeavingSettings() {
    try (var context = new GenericXmlApplicationContext()) {
      context.setClassLoader(new ClassLoader(getClass().getClassLoader()) {
        @Override
        public URL getResource(String name) {
          return "META-INF/aop.xml".equals(name) ? null : super.getResource(name);
        }
      });
      context.load(contextXml("<context:load-time-weaver/>"));
      var registry = context.getBeanFactory();
      assertThat(registry.getBeanDefinition(ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME).getBeanClassName())
              .isEqualTo("infra.context.weaving.DefaultContextLoadTimeWeaver");
      assertThat(registry.containsBeanDefinition(LoadTimeWeaverBeanDefinitionParser.ASPECTJ_WEAVING_ENABLER_BEAN_NAME)).isFalse();
    }
    try (var context = new GenericXmlApplicationContext()) {
      context.load(contextXml("""
              <context:load-time-weaver aspectj-weaving="off" weaver-class="example.CustomWeaver"/>
              """));
      assertThat(context.getBeanFactory().getBeanDefinition(ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME).getBeanClassName())
              .isEqualTo("example.CustomWeaver");
      context.load(contextXml("<context:load-time-weaver aspectj-weaving='on'/>"));
      assertThat(context.getBeanFactory().containsBeanDefinition(LoadTimeWeaverBeanDefinitionParser.ASPECTJ_WEAVING_ENABLER_BEAN_NAME)).isTrue();
    }
  }

  @Test
  void mbeanServerDefinitionsUseAgentIdOrLocateExistingServer() {
    try (var context = new GenericXmlApplicationContext()) {
      context.load(contextXml("""
              <context:mbean-server/>
              <context:mbean-server id="namedServer" agent-id="sample"/>
              """));
      var factory = context.getBeanFactory();
      assertThat(factory.getBeanDefinition("mbeanServer").getPropertyValues().getPropertyValue("locateExistingServerIfPossible"))
              .isEqualTo(true);
      assertThat(factory.getBeanDefinition("namedServer").getPropertyValues().getPropertyValue("agentId")).isEqualTo("sample");
    }
  }

  private static ByteArrayResource contextXml(String element) {
    return new ByteArrayResource("""
            <beans xmlns="http://www.springframework.org/schema/beans"
                   xmlns:context="http://www.springframework.org/schema/context"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
                                       http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
              %s
            </beans>
            """.formatted(element).getBytes(StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    System.getProperties().remove("foo");
  }

  @Test
  void propertyPlaceholder() {
    ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
            "contextNamespaceHandlerTests-replace.xml", getClass());
    assertThat(applicationContext.getBean("string")).isEqualTo("bar");
    assertThat(applicationContext.getBean("nullString")).isEqualTo("null");
    applicationContext.close();
  }

  @Test
  void propertyPlaceholderSystemProperties() {
    String value = System.setProperty("foo", "spam");
    try {
      ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
              "contextNamespaceHandlerTests-system.xml", getClass());
      assertThat(applicationContext.getBean("string")).isEqualTo("spam");
      assertThat(applicationContext.getBean("fallback")).isEqualTo("none");
      applicationContext.close();
    }
    finally {
      if (value != null) {
        System.setProperty("foo", value);
      }
    }
  }

  @Test
  void propertyPlaceholderEnvironmentProperties() {
    MockEnvironment env = new MockEnvironment().withProperty("foo", "spam");
    GenericXmlApplicationContext applicationContext = new GenericXmlApplicationContext();
    applicationContext.setEnvironment(env);
    applicationContext.load(new ClassPathResource("contextNamespaceHandlerTests-simple.xml", getClass()));
    applicationContext.refresh();
    assertThat(applicationContext.getBean("string")).isEqualTo("spam");
    assertThat(applicationContext.getBean("fallback")).isEqualTo("none");
    applicationContext.close();
  }

  @Test
  void propertyPlaceholderLocation() {
    ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
            "contextNamespaceHandlerTests-location.xml", getClass());
    assertThat(applicationContext.getBean("foo")).isEqualTo("bar");
    assertThat(applicationContext.getBean("bar")).isEqualTo("foo");
    assertThat(applicationContext.getBean("spam")).isEqualTo("maps");
    applicationContext.close();
  }

  @Test
  void propertyPlaceholderLocationWithSystemPropertyForOneLocation() {
    System.setProperty("properties",
            "classpath*:/infra/context/config/test-*.properties");
    try {
      ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
              "contextNamespaceHandlerTests-location-placeholder.xml", getClass());
      assertThat(applicationContext.getBean("foo")).isEqualTo("bar");
      assertThat(applicationContext.getBean("bar")).isEqualTo("foo");
      assertThat(applicationContext.getBean("spam")).isEqualTo("maps");
      applicationContext.close();
    }
    finally {
      System.clearProperty("properties");
    }
  }

  @Test
  void propertyPlaceholderLocationWithSystemPropertyForMultipleLocations() {
    System.setProperty("properties",
            "classpath*:/infra/context/config/test-*.properties," +
                    "classpath*:/infra/context/config/empty-*.properties," +
                    "classpath*:/infra/context/config/missing-*.properties");
    try {
      ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
              "contextNamespaceHandlerTests-location-placeholder.xml", getClass());
      assertThat(applicationContext.getBean("foo")).isEqualTo("bar");
      assertThat(applicationContext.getBean("bar")).isEqualTo("foo");
      assertThat(applicationContext.getBean("spam")).isEqualTo("maps");
      applicationContext.close();
    }
    finally {
      System.clearProperty("properties");
    }
  }

  @Test
  void propertyPlaceholderLocationWithSystemPropertyMissing() {
    assertThatExceptionOfType(FatalBeanException.class)
            .isThrownBy(() -> new ClassPathXmlApplicationContext("contextNamespaceHandlerTests-location-placeholder.xml", getClass()))
            .havingRootCause()
            .isInstanceOf(PlaceholderResolutionException.class)
            .withMessage("Could not resolve placeholder 'foo' in value \"${foo}\"");
  }

  @Test
  void propertyPlaceholderIgnored() {
    ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
            "contextNamespaceHandlerTests-replace-ignore.xml", getClass());
    assertThat(applicationContext.getBean("string")).isEqualTo("${bar}");
    assertThat(applicationContext.getBean("nullString")).isEqualTo("null");
    applicationContext.close();
  }

  @Test
  void propertyOverride() {
    ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext(
            "contextNamespaceHandlerTests-override.xml", getClass());
    Date date = (Date) applicationContext.getBean("date");
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(42);
    applicationContext.close();
  }

}

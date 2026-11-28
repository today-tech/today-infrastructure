/*
 * Copyright 2017 - 2026 the TODAY authors.
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
package infra.beans.factory.support;

import org.junit.jupiter.api.Test;

import infra.beans.PropertyValues;
import infra.beans.factory.BeanDefinitionValidationException;
import infra.beans.factory.config.AutowiredPropertyMarker;
import infra.beans.factory.config.ConstructorArgumentValues;
import infra.beans.factory.config.PropertyValueRetriever;
import infra.util.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChildBeanDefinitionTests {

  @Test
  void constructorsPreserveParentClassAndOverrides() {
    var properties = new PropertyValues().add("name", "child");
    var arguments = new ConstructorArgumentValues();
    arguments.addIndexedArgumentValue(0, "argument");
    var minimal = new ChildBeanDefinition("parent");
    var withProperties = new ChildBeanDefinition("parent", properties);
    var withArguments = new ChildBeanDefinition("parent", arguments, properties);
    var resolved = new ChildBeanDefinition("parent", Sample.class, arguments, properties);
    var unresolved = new ChildBeanDefinition("parent", Sample.class.getName(), arguments, properties);

    assertThat(minimal.getParentName()).isEqualTo("parent");
    assertThat(withProperties.getPropertyValues()).isEqualTo(properties);
    assertThat(withArguments.getConstructorArgumentValues()).isEqualTo(arguments);
    assertThat(resolved.getBeanClass()).isEqualTo(Sample.class);
    assertThat(unresolved.hasBeanClass()).isFalse();
    assertThat(unresolved.getBeanClassName()).isEqualTo(Sample.class.getName());
    assertThat(resolved.toString()).startsWith("Child bean with parent 'parent':");
  }

  @Test
  void clonePreservesParentAndDoesNotShareMutablePropertyValues() {
    var original = new ChildBeanDefinition("parent", new PropertyValues().add("name", "child"));
    var clone = original.cloneBeanDefinition();

    assertThat(clone).isNotSameAs(original).isEqualTo(original).hasSameHashCodeAs(original);
    assertThat(original.equals(original)).isTrue();
    assertThat(original).isNotEqualTo(new ChildBeanDefinition("other")).isNotEqualTo(new RootBeanDefinition());
    clone.getPropertyValues().add("name", "changed");
    assertThat(original.getPropertyValues().getPropertyValue("name")).isEqualTo("child");
    assertThat(clone).isNotEqualTo(original);
  }

  @Test
  void validationRejectsRemovedParentName() {
    var definition = new ChildBeanDefinition("parent");
    definition.validate();

    definition.setParentName(null);

    assertThatExceptionOfType(BeanDefinitionValidationException.class)
            .isThrownBy(definition::validate).withMessageContaining("'parentName' must be set");
  }

  @Test
  void propertyRetrieverCanPreserveTheInheritedDefaultOrProvideAValue() {
    var factory = new StandardBeanFactory();
    factory.registerBeanDefinition("parent", new RootBeanDefinition(Sample.class));
    var retained = new ChildBeanDefinition("parent");
    retained.getPropertyValues().add("name", (PropertyValueRetriever) (path, binder, owner) -> PropertyValueRetriever.DO_NOT_SET);
    var replaced = new ChildBeanDefinition("parent");
    replaced.getPropertyValues().add("name", (PropertyValueRetriever) (path, binder, owner) -> "resolved " + path);
    factory.registerBeanDefinition("retained", retained);
    factory.registerBeanDefinition("replaced", replaced);

    assertThat(factory.getBean("retained", Sample.class).name).isEqualTo("default");
    assertThat(factory.getBean("replaced", Sample.class).name).isEqualTo("resolved name");
  }

  @Test
  void autowiredMarkerPreservesIdentityOnSerialization() {
    var marker = (AutowiredPropertyMarker) AutowiredPropertyMarker.INSTANCE;

    assertThat(SerializationUtils.clone(marker)).isSameAs(marker).hasSameHashCodeAs(marker);
    assertThat(marker.equals(marker)).isTrue();
    assertThat(marker).isNotEqualTo("(autowired)").hasToString("(autowired)");
  }

  static class Sample {

    String name = "default";

    public void setName(String name) {
      this.name = name;
    }
  }

}

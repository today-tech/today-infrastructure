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

import infra.beans.factory.BeanDefinitionValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MethodDescriptorTests {

  @Test
  void simpleAndQualifiedNamesResolveToTheDeclaringClass() {
    assertDescriptor("start", Child.class);
    assertDescriptor(Child.class.getName() + ".start", Child.class);
    assertDescriptor(Parent.class.getName() + ".start", Parent.class);
  }

  @Test
  void unknownDeclaringClassReportsBeanAndMethod() {
    assertThatExceptionOfType(BeanDefinitionValidationException.class)
            .isThrownBy(() -> MethodDescriptor.create("service", Child.class, "missing.Bean.start"))
            .withMessageContainingAll("missing.Bean.start", "service");
  }

  private static void assertDescriptor(String methodName, Class<?> declaringClass) {
    var descriptor = MethodDescriptor.create("service", Child.class, methodName);

    assertThat(descriptor.declaringClass()).isEqualTo(declaringClass);
    assertThat(descriptor.methodName()).isEqualTo("start");
    assertThat(descriptor.parameterTypes()).isEmpty();
  }

  private static class Parent {
  }

  private static class Child extends Parent {
  }

}

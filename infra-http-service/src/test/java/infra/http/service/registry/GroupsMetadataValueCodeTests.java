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

package infra.http.service.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import infra.aot.generate.ValueCodeGenerator;
import infra.aot.test.generate.TestGenerationContext;
import infra.beans.factory.aot.BeanDefinitionPropertyValueCodeGeneratorDelegates;
import infra.http.service.registry.HttpServiceGroup.ClientType;

import static org.assertj.core.api.Assertions.assertThat;

class GroupsMetadataValueCodeTests {

  private final GroupsMetadataValueDelegate delegate = new GroupsMetadataValueDelegate();

  private final ValueCodeGenerator generator = ValueCodeGenerator.withDefaults().add(List.of(this.delegate));

  @Test
  void unsupportedValuesAreLeftToOtherDelegates() {
    assertThat(this.delegate.generateCode(this.generator, "sample")).isNull();
  }

  @Test
  void registrationCodePreservesNamesAndUsesMutableEmptySet() {
    var registration = new GroupsMetadata.Registration("sample", ClientType.REST_CLIENT);

    assertThat(this.generator.generateCode(registration).toString())
            .contains("\"sample\"", "REST_CLIENT", "new java.util.LinkedHashSet()");
    registration.httpServiceTypeNames().add("example.SampleClient");
    assertThat(this.generator.generateCode(registration).toString())
            .contains("\"sample\"", "REST_CLIENT", "\"example.SampleClient\"");
  }

  @Test
  void metadataCanBeGeneratedInlineOrInDedicatedMethod() {
    var metadata = new GroupsMetadata();
    metadata.getOrCreateGroup("first", ClientType.REST_CLIENT).httpServiceTypeNames().add("example.First");
    metadata.getOrCreateGroup("second", ClientType.WEB_CLIENT).httpServiceTypeNames().add("example.Second");

    assertThat(this.generator.generateCode(metadata).toString())
            .contains("new infra.http.service.registry.GroupsMetadata(", "\"first\"", "\"second\"",
                    "example.First", "example.Second");
    var context = new TestGenerationContext();
    var generatedClass = context.getGeneratedClasses().addForFeatureComponent("TestCode", GroupsMetadata.class, builder -> { });
    var methodGenerator = BeanDefinitionPropertyValueCodeGeneratorDelegates.createValueCodeGenerator(
            generatedClass.getMethods(), List.of(this.delegate));
    assertThat(methodGenerator.generateCode(metadata).toString()).contains("getGroupsMetadata()");
    assertThat(generatedClass.getMethods()).isNotNull();
  }

}

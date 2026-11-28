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

package infra.http.reactive.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdkHttpClientResourceFactoryTests {

  @Test
  void connectorBuilderSelectionUsesTypeAndClasspath() {
    assertThat(ClientHttpConnectorBuilder.of(JdkClientHttpConnector.class)).isInstanceOf(JdkClientHttpConnectorBuilder.class);
    assertThat(ClientHttpConnectorBuilder.of(ReactorClientHttpConnector.class)).isInstanceOf(ReactorClientHttpConnectorBuilder.class);
    assertThat(ClientHttpConnectorBuilder.of(HttpComponentsClientHttpConnector.class)).isInstanceOf(HttpComponentsClientHttpConnectorBuilder.class);
    assertThat(ClientHttpConnectorBuilder.detect()).isInstanceOf(ReactorClientHttpConnectorBuilder.class);
    assertThat(ClientHttpConnectorBuilder.detect(without("reactor.netty."))).isInstanceOf(HttpComponentsClientHttpConnectorBuilder.class);
    assertThat(ClientHttpConnectorBuilder.detect(without("reactor.netty.", "org.apache.hc."))).isInstanceOf(JdkClientHttpConnectorBuilder.class);
    assertThatIllegalArgumentException().isThrownBy(() -> ClientHttpConnectorBuilder.of(null));
    assertThatIllegalArgumentException().isThrownBy(() -> ClientHttpConnectorBuilder.of(ClientHttpConnector.class));
  }

  @Test
  void connectorCustomizersRunInOrderAfterBuild() {
    var connector = mock(ClientHttpConnector.class);
    var calls = new ArrayList<String>();
    ClientHttpConnectorBuilder<ClientHttpConnector> builder = settings -> {
      assertThat(settings).isNull();
      calls.add("build");
      return connector;
    };
    var customized = builder.withCustomizer(value -> {
      assertThat(value).isSameAs(connector);
      calls.add("first");
    }).withCustomizers(List.of(value -> calls.add("second"), value -> calls.add("third")));
    assertThat(customized.build()).isSameAs(connector);
    assertThat(calls).containsExactly("build", "first", "second", "third");
    assertThatIllegalArgumentException().isThrownBy(() -> builder.withCustomizer(null));
    assertThatIllegalArgumentException().isThrownBy(() -> builder.withCustomizers(null));
    assertThatIllegalArgumentException().isThrownBy(() -> builder.withCustomizers(Arrays.asList((value) -> { }, null)));
  }

  private ClassLoader without(String... prefixes) {
    return new ClassLoader(getClass().getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (Arrays.stream(prefixes).anyMatch(name::startsWith)) {
          throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, resolve);
      }
    };
  }

  @Test
  void createsNamedExecutorAndShutsItDown() throws Exception {
    var factory = new JdkHttpClientResourceFactory();
    factory.setThreadPrefix("sample-client");
    factory.afterPropertiesSet();
    var executor = (ExecutorService) factory.getExecutor();
    try {
      assertThat(executor.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS))
              .startsWith("sample-client@");
      factory.afterPropertiesSet();
      assertThat(factory.getExecutor()).isSameAs(executor);
    }
    finally {
      factory.destroy();
    }
    assertThat(executor.isShutdown()).isTrue();
  }

  @Test
  void retainsAndShutsDownProvidedExecutorService() throws Exception {
    var factory = new JdkHttpClientResourceFactory();
    var executor = mock(ExecutorService.class);
    factory.setExecutor(executor);
    factory.afterPropertiesSet();
    assertThat(factory.getExecutor()).isSameAs(executor);
    factory.destroy();
    verify(executor).shutdown();
  }

  @Test
  void acceptsNonServiceExecutorAndRejectsNullThreadPrefix() throws Exception {
    var factory = new JdkHttpClientResourceFactory();
    Executor executor = Runnable::run;
    factory.setExecutor(executor);
    factory.afterPropertiesSet();
    factory.destroy();
    assertThat(factory.getExecutor()).isSameAs(executor);
    assertThatIllegalArgumentException().isThrownBy(() -> factory.setThreadPrefix(null));
  }

}

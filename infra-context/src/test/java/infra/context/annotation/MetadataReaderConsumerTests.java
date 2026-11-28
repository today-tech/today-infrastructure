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

package infra.context.annotation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import infra.core.type.classreading.MetadataReader;
import infra.core.type.classreading.MetadataReaderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MetadataReaderConsumerTests {

  @Test
  void compositionPreservesOrderAndArguments() throws IOException {
    var reader = mock(MetadataReader.class);
    var factory = mock(MetadataReaderFactory.class);
    var calls = new ArrayList<String>();
    MetadataReaderConsumer first = (r, f) -> {
      assertThat(r).isSameAs(reader);
      assertThat(f).isSameAs(factory);
      calls.add("first");
    };
    first.andThen((r, f) -> {
      assertThat(r).isSameAs(reader);
      assertThat(f).isSameAs(factory);
      calls.add("second");
    }).accept(reader, factory);
    assertThat(calls).containsExactly("first", "second");
  }

  @Test
  void failurePreventsSecondConsumer() {
    var failure = new IOException("read failed");
    MetadataReaderConsumer first = (reader, factory) -> { throw failure; };
    var composed = first.andThen((reader, factory) -> { throw new AssertionError("Unexpected invocation"); });
    assertThatThrownBy(() -> composed.accept(mock(MetadataReader.class), mock(MetadataReaderFactory.class)))
            .isSameAs(failure);
    assertThatThrownBy(() -> first.andThen(null)).isInstanceOf(NullPointerException.class);
  }

}

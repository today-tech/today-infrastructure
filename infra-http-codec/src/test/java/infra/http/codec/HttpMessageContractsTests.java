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

package infra.http.codec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import infra.core.ResolvableType;
import infra.http.MediaType;
import infra.http.reactive.server.ServerHttpRequest;
import infra.http.reactive.server.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class HttpMessageContractsTests {

  private final ResolvableType type = ResolvableType.forClass(String.class);

  private final ServerHttpRequest request = mock(ServerHttpRequest.class);

  private final ServerHttpResponse response = mock(ServerHttpResponse.class);

  @Test
  void encoderDefaultsToNoHints() {
    var encoder = mock(HttpMessageEncoder.class, CALLS_REAL_METHODS);
    assertThat(encoder.getEncodeHints(type, type, MediaType.TEXT_PLAIN, request, response)).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void readerDefaultsFilterMediaTypesAndDelegateServerReads() {
    HttpMessageReader<String> reader = mock(HttpMessageReader.class, CALLS_REAL_METHODS);
    given(reader.canRead(type, null)).willReturn(true);
    given(reader.getReadableMediaTypes()).willReturn(List.of(MediaType.TEXT_PLAIN));
    assertThat(reader.getReadableMediaTypes(type)).containsExactly(MediaType.TEXT_PLAIN);
    assertThat(reader.getReadableMediaTypes(ResolvableType.forClass(Integer.class))).isEmpty();
    Map<String, Object> hints = Map.of("sample", "value");
    var flux = Flux.just("first", "second");
    var mono = Mono.just("first");
    given(reader.read(type, request, hints)).willReturn(flux);
    given(reader.readMono(type, request, hints)).willReturn(mono);
    assertThat(reader.read(type, type, request, response, hints)).isSameAs(flux);
    assertThat(reader.readMono(type, type, request, response, hints)).isSameAs(mono);
  }

  @Test
  @SuppressWarnings("unchecked")
  void writerDefaultsFilterMediaTypesAndDelegateServerWrites() {
    HttpMessageWriter<String> writer = mock(HttpMessageWriter.class, CALLS_REAL_METHODS);
    given(writer.canWrite(type, null)).willReturn(true);
    given(writer.getWritableMediaTypes()).willReturn(List.of(MediaType.TEXT_PLAIN));
    assertThat(writer.getWritableMediaTypes(type)).containsExactly(MediaType.TEXT_PLAIN);
    assertThat(writer.getWritableMediaTypes(ResolvableType.forClass(Integer.class))).isEmpty();
    var input = Flux.just("sample");
    Map<String, Object> hints = Map.of("sample", "value");
    Mono<Void> completion = Mono.empty();
    given(writer.write(input, type, MediaType.TEXT_PLAIN, response, hints)).willReturn(completion);
    assertThat(writer.write(input, type, type, MediaType.TEXT_PLAIN, request, response, hints)).isSameAs(completion);
  }
}

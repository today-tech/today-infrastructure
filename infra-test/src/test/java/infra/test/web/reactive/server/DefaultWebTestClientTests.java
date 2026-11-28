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

package infra.test.web.reactive.server;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import infra.http.HttpHeaders;
import infra.http.ResponseCookie;
import infra.http.reactive.client.ClientHttpConnector;
import infra.util.LinkedMultiValueMap;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link DefaultWebTestClient}.
 *
 * @author Sam Brannen
 * @since 5.0
 */
class DefaultWebTestClientTests {

  @Test
  void cookieMatchersCheckResponseAttributesAndUseDiagnostics() {
    var result = mock(ExchangeResult.class);
    var response = mock(WebTestClient.ResponseSpec.class);
    var cookies = new LinkedMultiValueMap<String, ResponseCookie>();
    cookies.add("session", ResponseCookie.builder("session", "sample").path("/app").domain("example.org")
            .maxAge(Duration.ofSeconds(30)).build());
    given(result.getResponseCookies()).willReturn(cookies);
    willAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).given(result).assertWithDiagnostics(any(Runnable.class));
    var assertions = new CookieAssertions(result, response);

    Matcher<String> textMatcher = mock();
    Matcher<Long> ageMatcher = mock();
    given(textMatcher.matches(any())).willReturn(true);
    given(ageMatcher.matches(any())).willReturn(true);
    assertThat(assertions.value("session", textMatcher)).isSameAs(response);
    assertThat(assertions.path("session", textMatcher)).isSameAs(response);
    assertThat(assertions.domain("session", textMatcher)).isSameAs(response);
    assertThat(assertions.maxAge("session", ageMatcher)).isSameAs(response);
    then(textMatcher).should().matches("sample");
    then(textMatcher).should().matches("/app");
    then(textMatcher).should().matches("example.org");
    then(ageMatcher).should().matches(30L);
  }

  @Test
  void mutateDoesNotShareDefaultHeaderAndCookieValues() {
    WebTestClient client = WebTestClient.bindToServer(
            new HttpHandlerConnector((request, response) -> response.setComplete()))
            .defaultHeaders(headers -> headers.add("foo", "bar"))
            .defaultCookie("foo", "bar").build();
    client.mutate().defaultHeaders(headers -> headers.add("foo", "baz"))
            .defaultCookie("foo", "baz").build();
    assertDefaultHeaderAndCookie(client, "bar");
    assertDefaultHeaderAndCookie(client.mutate().build(), "bar");
  }

  @Test
  void buildDoesNotShareDefaultHeaderAndCookieValues() {
    WebTestClient.Builder builder = WebTestClient.bindToServer(
            new HttpHandlerConnector((request, response) -> response.setComplete()))
            .defaultHeader("foo", "bar").defaultCookie("foo", "bar");
    WebTestClient client = builder.build();
    builder.defaultHeaders(headers -> headers.add("foo", "baz"));
    builder.defaultCookie("foo", "baz");
    assertDefaultHeaderAndCookie(client, "bar");
  }

  private void assertDefaultHeaderAndCookie(WebTestClient client, String value) {
    ExchangeResult result = client.get().uri("/").exchange().expectBody().isEmpty();
    assertThat(result.getRequestHeaders().get("foo")).containsExactly(value);
    assertThat(result.getRequestHeaders().get(HttpHeaders.COOKIE)).containsExactly("foo=" + value);
  }

  private final ClientHttpConnector connector = new HttpHandlerConnector((request, response) -> {
    response.getHeaders().setContentType(infra.http.MediaType.TEXT_PLAIN);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(
            "done".getBytes(java.nio.charset.StandardCharsets.UTF_8))).delayElement(Duration.ofMillis(100)));
  });

  @Test
  @Timeout(5)
  void responseTimeoutZeroSpecifiesInfiniteTimeout() {
    WebTestClient client = WebTestClient.bindToServer(this.connector)
            .responseTimeout(Duration.ZERO).build();
    client.get().uri("/slow").exchange().expectBody(String.class).isEqualTo("done");
  }

  @Test
  void responseTimeoutExceeded() {
    WebTestClient client = WebTestClient.bindToServer(this.connector)
            .responseTimeout(Duration.ofMillis(10)).build();
    assertThatIllegalStateException().isThrownBy(() -> client.get().uri("/slow").exchange())
            .withMessageStartingWith("Timeout on blocking read");
  }

  @Test
  void negativeResponseTimeoutIsRejected() {
    WebTestClient.Builder builder = WebTestClient.bindToServer(this.connector);
    assertThatIllegalArgumentException().isThrownBy(() -> builder.responseTimeout(Duration.ofMillis(-1)))
            .withMessage("Timeout must be a non-negative value");
  }

  @Test
  void nullResponseTimeoutIsRejected() {
    WebTestClient.Builder builder = WebTestClient.bindToServer(this.connector);
    assertThatIllegalArgumentException().isThrownBy(() -> builder.responseTimeout(null))
            .withMessage("Timeout must not be null");
  }
}

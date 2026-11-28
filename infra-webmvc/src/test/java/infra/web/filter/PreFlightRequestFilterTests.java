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

package infra.web.filter;

import org.junit.jupiter.api.Test;

import infra.web.FilterChain;
import infra.web.ServerHttpExchange;
import infra.web.cors.PreFlightRequestHandler;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PreFlightRequestFilterTests {

  @Test
  void preFlightRequestIsHandledWithoutCallingChain() throws Exception {
    var handler = mock(PreFlightRequestHandler.class);
    var exchange = mock(ServerHttpExchange.class);
    var chain = mock(FilterChain.class);
    given(exchange.isPreFlightRequest()).willReturn(true);
    new PreFlightRequestFilter(handler).doFilter(exchange, chain);
    verify(handler).handlePreFlight(exchange);
    verifyNoInteractions(chain);
  }

  @Test
  void ordinaryRequestContinuesThroughChain() throws Exception {
    var handler = mock(PreFlightRequestHandler.class);
    var exchange = mock(ServerHttpExchange.class);
    var chain = mock(FilterChain.class);
    new PreFlightRequestFilter(handler).doFilter(exchange, chain);
    verify(chain).doFilter(exchange);
    verifyNoInteractions(handler);
    assertThatIllegalArgumentException().isThrownBy(() -> new PreFlightRequestFilter(null));
  }
}

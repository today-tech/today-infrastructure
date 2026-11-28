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

package infra.test.web.mock.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import infra.http.client.JdkClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class TrustAllTlsRequestFactoryTests {

  @Test
  void builderUsesDedicatedTlsContextForTestClients() throws Exception {
    var defaultContext = SSLContext.getDefault();
    var context = spy(SSLContext.getInstance("TLS"));
    try (var ssl = mockStatic(SSLContext.class, CALLS_REAL_METHODS)) {
      ssl.when(() -> SSLContext.getInstance("TLS")).thenReturn(context);
      var client = TrustAllTlsRequestFactory.httpClientBuilder().build();
      assertThat(client.sslContext()).isSameAs(context);
    }
    assertThat(SSLContext.getDefault()).isSameAs(defaultContext);
    var trustManagers = ArgumentCaptor.forClass(TrustManager[].class);
    verify(context).init(isNull(), trustManagers.capture(), any(SecureRandom.class));
    assertThat(trustManagers.getValue()).singleElement().isInstanceOfSatisfying(X509TrustManager.class, manager -> {
      var chain = new X509Certificate[] { mock(X509Certificate.class) };
      assertThat(manager.getAcceptedIssuers()).isEmpty();
      assertThatCode(() -> manager.checkClientTrusted(chain, "RSA")).doesNotThrowAnyException();
      assertThatCode(() -> manager.checkServerTrusted(chain, "RSA")).doesNotThrowAnyException();
    });
  }

  @Test
  void createsJdkRequestFactoryAndKeepsTlsInitializationFailureAsCause() {
    assertThat(TrustAllTlsRequestFactory.create()).isInstanceOf(JdkClientHttpRequestFactory.class);
    var failure = new NoSuchAlgorithmException("TLS unavailable");
    try (var ssl = mockStatic(SSLContext.class, CALLS_REAL_METHODS)) {
      ssl.when(() -> SSLContext.getInstance("TLS")).thenThrow(failure);
      assertThatIllegalStateException().isThrownBy(TrustAllTlsRequestFactory::httpClientBuilder)
              .withMessage("Failed to create trust-all SSL request factory").withCause(failure);
    }
  }

}

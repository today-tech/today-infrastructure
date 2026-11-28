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

package infra.cache.jcache;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Properties;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class JCacheManagerFactoryBeanTests {

  @Test
  void initializesWithConfiguredProviderArgumentsAndClosesManager() {
    var factory = new JCacheManagerFactoryBean();
    var uri = URI.create("urn:sample");
    var properties = new Properties();
    var loader = getClass().getClassLoader();
    var provider = mock(CachingProvider.class);
    var manager = mock(CacheManager.class);
    given(provider.getCacheManager(uri, loader, properties)).willReturn(manager);
    factory.setCacheManagerUri(uri);
    factory.setCacheManagerProperties(properties);
    factory.setBeanClassLoader(loader);

    try (var caching = mockStatic(Caching.class)) {
      caching.when(Caching::getCachingProvider).thenReturn(provider);
      assertThat(factory.getObject()).isNull();
      assertThat(factory.getObjectType()).isEqualTo(CacheManager.class);
      factory.destroy();
      factory.afterPropertiesSet();
      assertThat(factory.getObject()).isSameAs(manager);
      assertThat(factory.getObjectType()).isEqualTo(manager.getClass());
      assertThat(factory.isSingleton()).isTrue();
      factory.destroy();
      verify(manager).close();
    }
  }
}

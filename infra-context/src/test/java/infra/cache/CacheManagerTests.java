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

package infra.cache;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import infra.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CacheManagerTests {

  @Test
  void defaultCacheOperationsDelegateToBasicMethods() {
    var cache = mock(Cache.class, CALLS_REAL_METHODS);
    assertThat(cache.putIfAbsent("missing", "value")).isNull();
    verify(cache).put("missing", "value");
    Cache.ValueWrapper existing = () -> "old";
    given(cache.get("present")).willReturn(existing);
    assertThat(cache.putIfAbsent("present", "new")).isSameAs(existing);
    assertThat(cache.evictIfPresent("present")).isFalse();
    verify(cache).evict("present");
    assertThat(cache.invalidate()).isFalse();
    verify(cache).clear();
    assertThatThrownBy(() -> cache.retrieve("key")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> cache.retrieve("key", () -> java.util.concurrent.CompletableFuture.completedFuture("value")))
            .isInstanceOf(UnsupportedOperationException.class);
    var cause = new IllegalStateException("load failed");
    var exception = new Cache.ValueRetrievalException("key", "loader", cause);
    assertThat(exception.getKey()).isEqualTo("key");
    assertThat(exception).hasCause(cause).hasMessageContaining("key");
  }

  @Test
  void defaultResetClearsExistingCachesAndToleratesUnavailableCache() {
    var first = new ConcurrentMapCache("first");
    var second = new ConcurrentMapCache("second");
    first.put("key", "first value");
    second.put("key", "second value");
    CacheManager manager = new CacheManager() {
      @Override
      public Cache getCache(String name) {
        return switch (name) {
          case "first" -> first;
          case "second" -> second;
          default -> null;
        };
      }

      @Override
      public Collection<String> getCacheNames() {
        return List.of("first", "unavailable", "second");
      }
    };

    manager.resetCaches();

    assertThat(first.get("key")).isNull();
    assertThat(second.get("key")).isNull();
    assertThat(manager.getCache("first")).isSameAs(first);
    assertThat(manager.getCacheNames()).containsExactly("first", "unavailable", "second");
  }

}

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

package infra.context.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import infra.context.ApplicationEvent;
import infra.context.ApplicationListener;
import infra.core.Ordered;
import infra.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SourceFilteringListenerTests {

  @Test
  void filtersByIdentityAndAdaptsPlainListener() {
    String source = new String(new char[] { 'a' });
    var events = new ArrayList<SampleEvent>();
    var delegate = new ApplicationListener<SampleEvent>() {
      @Override
      public void onApplicationEvent(SampleEvent event) {
        events.add(event);
      }
    };
    var listener = new SourceFilteringListener(source, delegate);
    var accepted = new SampleEvent(source);

    listener.onApplicationEvent(new SampleEvent("a"));
    listener.onApplicationEvent(accepted);

    assertThat(events).containsExactly(accepted);
    assertThat(listener.supportsEventType(ResolvableType.forClass(SampleEvent.class))).isTrue();
    assertThat(listener.supportsEventType(ResolvableType.forClass(ContextClosedEvent.class))).isFalse();
    assertThat(listener.supportsSourceType(String.class)).isTrue();
    assertThat(listener.supportsSourceType(Integer.class)).isFalse();
    assertThat(listener.supportsSourceType(null)).isFalse();
  }

  @Test
  void delegatesGenericListenerMetadata() {
    var source = new Object();
    var type = ResolvableType.forClass(SampleEvent.class);
    var delegate = mock(GenericApplicationListener.class);
    given(delegate.supportsEventType(type)).willReturn(true);
    given(delegate.getOrder()).willReturn(42);
    given(delegate.getListenerId()).willReturn("sample");
    var listener = new SourceFilteringListener(source, delegate);
    var event = new SampleEvent(source);

    assertThat(listener.supportsEventType(type)).isTrue();
    assertThat(listener.getOrder()).isEqualTo(42);
    assertThat(listener.getListenerId()).isEqualTo("sample");
    listener.onApplicationEvent(event);
    verify(delegate).supportsEventType(type);
    verify(delegate).getOrder();
    verify(delegate).getListenerId();
    verify(delegate).onApplicationEvent(event);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void listenerWithoutDelegateRequiresAnInternalHandler() {
    var source = new Object();
    var listener = new SourceFilteringListener(source);

    assertThat(listener.supportsEventType(ResolvableType.forClass(SampleEvent.class))).isTrue();
    assertThat(listener.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    assertThat(listener.getListenerId()).isEmpty();
    listener.onApplicationEvent(new SampleEvent(new Object()));
    assertThatIllegalStateException().isThrownBy(() -> listener.onApplicationEvent(new SampleEvent(source)))
            .withMessageContaining("Must specify a delegate object");
  }

  private static class SampleEvent extends ApplicationEvent {

    SampleEvent(Object source) {
      super(source);
    }
  }

}

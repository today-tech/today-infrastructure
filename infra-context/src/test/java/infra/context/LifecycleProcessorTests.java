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

package infra.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleProcessorTests {

  @Test
  void defaultCallbacksDriveStartAndStopInOrder() {
    var calls = new ArrayList<String>();
    LifecycleProcessor processor = new LifecycleProcessor() {
      @Override
      public void start() {
        calls.add("start");
      }

      @Override
      public void stop() {
        calls.add("stop");
      }

      @Override
      public boolean isRunning() {
        return !calls.isEmpty() && calls.get(calls.size() - 1).equals("start");
      }
    };
    processor.onRefresh();
    assertThat(processor.isRunning()).isTrue();
    processor.onRestart();
    assertThat(processor.isRunning()).isTrue();
    processor.onPause();
    assertThat(processor.isRunning()).isFalse();
    processor.onClose();
    assertThat(calls).containsExactly("start", "stop", "start", "stop", "stop");
  }

}

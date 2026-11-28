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

package infra.scheduling.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MethodInvokingRunnableTests {

  @Test
  void invokesPreparedInstanceMethodWithConvertedArgument() throws Exception {
    var target = new Target();
    var runnable = new MethodInvokingRunnable();
    runnable.setTargetObject(target);
    runnable.setTargetMethod("increment");
    runnable.setArguments("3");
    runnable.afterPropertiesSet();

    runnable.run();
    runnable.run();

    assertThat(target.count).isEqualTo(6);
  }

  @Test
  void resolvesStaticMethodWithConfiguredLoader() throws Exception {
    var runnable = new MethodInvokingRunnable();
    runnable.setBeanClassLoader(Target.class.getClassLoader());
    runnable.setStaticMethod(Target.class.getName() + ".noOperation");
    runnable.afterPropertiesSet();

    assertThat(runnable.getTargetClass()).isEqualTo(Target.class);
    assertThat(runnable.getPreparedMethod().getName()).isEqualTo("noOperation");
    assertThatCode(runnable::run).doesNotThrowAnyException();
  }

  @Test
  void targetFailureDoesNotTerminateSchedulerLoop() throws Exception {
    var target = new Target();
    var runnable = new MethodInvokingRunnable();
    runnable.setTargetObject(target);
    runnable.setTargetMethod("fail");
    runnable.afterPropertiesSet();

    assertThatCode(runnable::run).doesNotThrowAnyException();
    assertThatCode(runnable::run).doesNotThrowAnyException();
    assertThat(target.count).isEqualTo(2);
    assertThat(runnable.getInvocationFailureMessage()).contains("fail", Target.class.getName());
  }

  @Test
  void invocationInfrastructureFailureDoesNotEscape() {
    var runnable = new MethodInvokingRunnable();
    runnable.setTargetObject(new Target());
    runnable.setTargetMethod("increment");

    assertThatCode(runnable::run).doesNotThrowAnyException();
  }

  static class Target {

    int count;

    public void increment(int amount) {
      count += amount;
    }

    public void fail() {
      count++;
      throw new IllegalStateException("failed");
    }

    public static void noOperation() {
    }
  }

}

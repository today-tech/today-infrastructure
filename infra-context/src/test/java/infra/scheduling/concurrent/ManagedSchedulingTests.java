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

package infra.scheduling.concurrent;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.naming.NameNotFoundException;

import infra.jndi.JndiTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ManagedSchedulingTests {

  @Test
  void taskExecutorUsesConfiguredJndiResource() throws Exception {
    var executor = new DefaultManagedTaskExecutor();
    var delegate = mock(Executor.class);
    var template = mock(JndiTemplate.class);
    executor.setJndiEnvironment(new Properties());
    executor.setJndiTemplate(template);
    executor.setJndiName("executor");
    executor.setResourceRef(true);
    given(template.lookup("java:comp/env/executor", Executor.class)).willReturn(delegate);
    executor.afterPropertiesSet();
    Runnable task = () -> { };
    executor.execute(task);
    verify(delegate).execute(task);
  }

  @Test
  void threadFactoryUsesManagedResourceOrFallsBackToLocalThreads() throws Exception {
    var factory = new DefaultManagedAwareThreadFactory();
    var template = mock(JndiTemplate.class);
    var delegate = mock(ThreadFactory.class);
    Runnable task = () -> { };
    var managedThread = new Thread(task);
    factory.setJndiEnvironment(new Properties());
    factory.setJndiTemplate(template);
    factory.setJndiName("factory");
    factory.setResourceRef(true);
    given(template.lookup("java:comp/env/factory", ThreadFactory.class)).willReturn(delegate);
    given(delegate.newThread(task)).willReturn(managedThread);
    factory.afterPropertiesSet();
    assertThat(factory.newThread(task)).isSameAs(managedThread);

    var fallback = new DefaultManagedAwareThreadFactory();
    fallback.setJndiTemplate(template);
    fallback.setJndiName("java:comp/missing");
    given(template.lookup("java:comp/missing", ThreadFactory.class)).willThrow(new NameNotFoundException());
    fallback.setThreadNamePrefix("local-");
    fallback.afterPropertiesSet();
    assertThat(fallback.newThread(task).getName()).startsWith("local-");
  }

  @Test
  void schedulerInitializesBothExecutorRolesFromJndi() throws Exception {
    var scheduler = new DefaultManagedTaskScheduler();
    var template = mock(JndiTemplate.class);
    var executor = mock(ScheduledExecutorService.class);
    scheduler.setJndiEnvironment(new Properties());
    scheduler.setJndiTemplate(template);
    scheduler.setJndiName("scheduler");
    scheduler.setResourceRef(true);
    given(template.lookup("java:comp/env/scheduler", ScheduledExecutorService.class)).willReturn(executor);
    scheduler.afterPropertiesSet();
    assertThat(scheduler.getConcurrentExecutor()).isSameAs(executor);
  }

}

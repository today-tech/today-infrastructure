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

package infra.instrument.classloading;

import org.junit.jupiter.api.Test;

import java.lang.instrument.ClassFileTransformer;

import infra.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2023/8/14 14:42
 */
class TomcatLoadTimeWeaverTests {

  @Test
  void compatibleLoaderReceivesTransformerAndCreatesThrowawayLoader() {
    var loader = new CompatibleLoader();
    var weaver = new TomcatLoadTimeWeaver(loader);
    var transformer = mock(ClassFileTransformer.class);
    weaver.addTransformer(transformer);
    assertThat(loader.transformer).isSameAs(transformer);
    assertThat(weaver.getInstrumentableClassLoader()).isSameAs(loader);
    assertThat(weaver.getThrowawayClassLoader()).isNotSameAs(loader);
  }

  @Test
  void defaultConstructorUsesCompatibleContextClassLoader() {
    var loader = new CompatibleLoader() {
      @Override
      public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (TomcatLoadTimeWeaver.INSTRUMENTABLE_LOADER_CLASS_NAME.equals(name)) {
          return Runnable.class;
        }
        return super.loadClass(name);
      }
    };
    var thread = Thread.currentThread();
    var previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(loader);
      assertThat(new TomcatLoadTimeWeaver().getInstrumentableClassLoader()).isSameAs(loader);
    }
    finally {
      thread.setContextClassLoader(previous);
    }
  }

  @Test
  void invocationFailuresPreserveLoaderCause() {
    var loader = new CompatibleLoader();
    var weaver = new TomcatLoadTimeWeaver(loader);
    loader.failure = new IllegalStateException("loader failure");
    assertThatThrownBy(() -> weaver.addTransformer(mock(ClassFileTransformer.class)))
            .hasMessage("Tomcat addTransformer method threw exception").hasCause(loader.failure);
    assertThatThrownBy(weaver::getThrowawayClassLoader)
            .hasMessage("Tomcat copy method threw exception").hasCause(loader.failure);
  }

  @Test
  void incompatibleCopyResultReportsReflectiveInvocationFailure() {
    var weaver = new TomcatLoadTimeWeaver(new IncompatibleLoader());
    assertThatThrownBy(weaver::getThrowawayClassLoader)
            .hasMessage("Could not invoke Tomcat copy method").hasCauseInstanceOf(ClassCastException.class);
  }

  public static class CompatibleLoader extends ClassLoader {

    ClassFileTransformer transformer;

    IllegalStateException failure;

    public void addTransformer(ClassFileTransformer transformer) {
      if (failure != null) {
        throw failure;
      }
      this.transformer = transformer;
    }

    public ClassLoader getThrowawayClassLoader() {
      if (failure != null) {
        throw failure;
      }
      return new ClassLoader(this) { };
    }
  }

  static class IncompatibleLoader extends ClassLoader {

    public void addTransformer(ClassFileTransformer transformer) {
    }

    public String copyWithoutTransformers() {
      return "not a class loader";
    }
  }

  @Test
  void tomcatLoadTimeWeaver() {
    SimpleInstrumentableClassLoader classLoader = new SimpleInstrumentableClassLoader(ClassUtils.getDefaultClassLoader());
    assertThatThrownBy(() -> new TomcatLoadTimeWeaver(classLoader))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Could not initialize TomcatLoadTimeWeaver because Tomcat API classes are not available");

    assertThatThrownBy(TomcatLoadTimeWeaver::new)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Could not initialize TomcatLoadTimeWeaver because Tomcat API classes are not available");
  }

  @Test
  void nullClassLoaderNotAllowed() {
    assertThatThrownBy(() -> new TomcatLoadTimeWeaver(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ClassLoader is required");
  }

  @Test
  void customInstrumentableClassLoaderVariant() {
    ClassLoader mockLoader = mock(ClassLoader.class);
    SimpleInstrumentableClassLoader instrumentableLoader = new SimpleInstrumentableClassLoader(mockLoader);

    assertThatThrownBy(() -> new TomcatLoadTimeWeaver(instrumentableLoader))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not initialize TomcatLoadTimeWeaver");
  }

  @Test
  void defaultConstructorUsesDefaultClassLoader() {
    assertThatThrownBy(TomcatLoadTimeWeaver::new)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not initialize TomcatLoadTimeWeaver because Tomcat API classes are not available");
  }

  @Test
  void instrumentableClassLoaderMethodsNotAvailable() {
    ClassLoader mockLoader = mock(ClassLoader.class);
    SimpleInstrumentableClassLoader instrumentableLoader = new SimpleInstrumentableClassLoader(mockLoader);

    assertThatThrownBy(() -> {
      TomcatLoadTimeWeaver weaver = new TomcatLoadTimeWeaver(instrumentableLoader);
      weaver.addTransformer(mock(ClassFileTransformer.class));
    }).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not initialize TomcatLoadTimeWeaver");
  }

}

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

package infra.core.type.classreading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassFormatExceptionTests {

  @Test
  void preservesMessageAndOptionalCause() {
    var cause = new IllegalArgumentException("unsupported class version");
    assertThat(new ClassFormatException("Cannot read class")).hasMessage("Cannot read class").hasNoCause();
    assertThat(new ClassFormatException("Cannot read class", cause)).hasMessage("Cannot read class").hasCause(cause);
  }

}

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
package infra.beans;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BeanPropertyExceptionTests {

  @Test
  void unreadablePropertyReportsBeanTypeAndProperty() {
    var exception = new NotReadablePropertyException(Sample.class, "quantity");

    assertThat(exception.getBeanClass()).isEqualTo(Sample.class);
    assertThat(exception.getPropertyName()).isEqualTo("quantity");
    assertThat(exception).hasMessageContaining("Bean property 'quantity' is not readable");
  }

  @Test
  void unreadablePropertyPreservesProvidedDetails() {
    var cause = new IllegalStateException("getter unavailable");

    assertThat(new NotReadablePropertyException(Sample.class, "quantity", "No getter"))
            .hasMessageContaining("No getter").hasNoCause();
    assertThat(new NotReadablePropertyException(Sample.class, "quantity", "No getter", cause))
            .hasMessageContaining("No getter").hasCause(cause);
  }

  @Test
  void batchRetainsFailuresAndSupportsLookupByPropertyAndExceptionType() {
    var first = mismatch("quantity", new NumberFormatException("NaN"));
    var second = mismatch("limit", new IllegalStateException("invalid limit"));
    var batch = new PropertyBatchUpdateException(new PropertyAccessException[] { first, second });

    assertThat(batch.getExceptionCount()).isEqualTo(2);
    assertThat(batch.getPropertyAccessExceptions()).containsExactly(first, second);
    assertThat(batch.getPropertyAccessException("limit")).isSameAs(second);
    assertThat(batch.getPropertyAccessException("size")).isNull();
    assertThat(batch.contains(PropertyBatchUpdateException.class)).isTrue();
    assertThat(batch.contains(IllegalStateException.class)).isTrue();
    assertThat(batch.contains(UnsupportedOperationException.class)).isFalse();
    assertThat(batch.contains(null)).isFalse();
    assertThat(batch).hasMessage("Failed properties: " + first.getMessage() + "; " + second.getMessage());
    assertThat(batch.toString()).containsSubsequence("nested PropertyAccessExceptions (2)",
            "PropertyAccessException 1: " + first, "PropertyAccessException 2: " + second);
  }

  @Test
  void bothStackTraceOverloadsPrintEachFailureInOrder() {
    var batch = new PropertyBatchUpdateException(new PropertyAccessException[] {
            mismatch("quantity", new NumberFormatException("NaN")),
            mismatch("limit", new IllegalStateException("invalid limit")) });
    var bytes = new ByteArrayOutputStream();
    var text = new StringWriter();

    try (var stream = new PrintStream(bytes, true, UTF_8); var writer = new PrintWriter(text)) {
      batch.printStackTrace(stream);
      batch.printStackTrace(writer);
    }

    assertThat(bytes.toString(UTF_8)).isEqualTo(text.toString())
            .containsSubsequence("details (2)", "PropertyAccessException 1:", "NaN",
                    "PropertyAccessException 2:", "invalid limit");
  }

  @Test
  void emptyBatchIsRejected() {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> new PropertyBatchUpdateException(new PropertyAccessException[0]))
            .withMessage("At least 1 PropertyAccessException required");
  }

  private static TypeMismatchException mismatch(String property, RuntimeException cause) {
    var mismatch = new TypeMismatchException("bad input", int.class, cause);
    mismatch.initPropertyName(property);
    return mismatch;
  }

  private static class Sample {
  }

}

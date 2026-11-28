/*
 * Copyright 2017 - 2026 the TODAY authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package infra.core.metrics.jfr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;

import infra.core.metrics.StartupStep;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

class FlightRecorderStartupStepTests {

  @Test
  void recordsStepAndTagsAndInvokesCallback(@TempDir Path directory) throws Exception {
    var completed = new ArrayList<FlightRecorderStartupStep>();
    Path file = directory.resolve("startup.jfr");
    try (var recording = new Recording()) {
      recording.enable(FlightRecorderStartupEvent.class).withoutThreshold();
      recording.start();
      var step = new FlightRecorderStartupStep(42, "initialize", 12, completed::add);
      assertThat(step.getId()).isEqualTo(42);
      assertThat(step.getParentId()).isEqualTo(12L);
      assertThat(step.getName()).isEqualTo("initialize");
      assertThat(step.tag("component", "sample").tag("phase", () -> "start")).isSameAs(step);
      assertThat(step.getTags()).extracting(StartupStep.Tag::getKey, StartupStep.Tag::getValue)
              .containsExactly(tuple("component", "sample"), tuple("phase", "start"));
      assertThatExceptionOfType(UnsupportedOperationException.class)
              .isThrownBy(() -> step.getTags().iterator().remove());
      step.end();
      assertThat(completed).containsExactly(step);
      assertThat(step.getEvent().tags).isEqualTo("component=sample,phase=start,");
      recording.stop();
      recording.dump(file);
    }
    assertThat(RecordingFile.readAllEvents(file))
            .filteredOn(event -> event.getEventType().getName().equals(FlightRecorderStartupEvent.class.getName()))
            .anySatisfy(event -> {
              assertThat(event.getLong("eventId")).isEqualTo(42);
              assertThat(event.getLong("parentId")).isEqualTo(12);
              assertThat(event.getString("name")).isEqualTo("initialize");
              assertThat(event.getString("tags")).isEqualTo("component=sample,phase=start,");
            });
  }

  @Test
  void tagsCanBeAppendedFromSuppliers() {
    var tags = new FlightRecorderStartupStep.FlightRecorderTags();
    assertThat(tags.iterator().hasNext()).isFalse();
    tags.add("key", () -> "value");
    assertThat(tags).extracting(StartupStep.Tag::getKey, StartupStep.Tag::getValue)
            .containsExactly(tuple("key", "value"));
  }

}

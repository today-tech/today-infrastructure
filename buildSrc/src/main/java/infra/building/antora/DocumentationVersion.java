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

package infra.building.antora;

import org.jspecify.annotations.Nullable;

/**
 * Classifies artifact versions for documentation without loading the framework.
 */
public final class DocumentationVersion {

  private DocumentationVersion() {
  }

  /**
   * Return the artifact release type used to select documentation examples.
   *
   * @param version the artifact version
   * @return {@code snapshot}, {@code release}, or the prerelease phase
   */
  public static String artifactReleaseType(String version) {
    if (version.endsWith("-SNAPSHOT")) {
      return "snapshot";
    }
    String label = prereleaseLabel(version);
    return label == null ? "release" : label;
  }

  static @Nullable String prereleaseLabel(String version) {
    String[] components = version.split("-");
    if (components.length < 2) {
      return null;
    }
    String[] qualifier = components[1].split("\\.");
    String phase = qualifier.length == 2 ? qualifier[0] : components[1];
    if ("RELEASE".equals(phase)) {
      return version.endsWith("-SNAPSHOT") ? "SNAPSHOT" : null;
    }
    return phase;
  }

}

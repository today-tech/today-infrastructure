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

package infra.jdbc.support.incrementer;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DialectIncrementerTests {

  private final DataSource dataSource = mock(DataSource.class);

  @Test
  void sequenceDialectsGenerateDatabaseSpecificQueries() {
    assertSequence(new Db2MainframeMaxValueIncrementer(), new Db2MainframeMaxValueIncrementer(dataSource, "seq"),
            "select next value for seq from sysibm.sysdummy1");
    assertSequence(new Db2LuwMaxValueIncrementer(), new Db2LuwMaxValueIncrementer(dataSource, "seq"),
            "values nextval for seq");
    assertSequence(new HsqlSequenceMaxValueIncrementer(), new HsqlSequenceMaxValueIncrementer(dataSource, "seq"),
            "call next value for seq");
  }

  @Test
  void identityDialectsGenerateDatabaseSpecificStatements() {
    assertIdentity(new SybaseMaxValueIncrementer(), new SybaseMaxValueIncrementer(dataSource, "seq", "id"),
            "insert into seq values()");
    assertIdentity(new SybaseAnywhereMaxValueIncrementer(), new SybaseAnywhereMaxValueIncrementer(dataSource, "seq", "id"),
            "insert into seq values(DEFAULT)");
    assertIdentity(new SqlServerMaxValueIncrementer(), new SqlServerMaxValueIncrementer(dataSource, "seq", "id"),
            "insert into seq default values");
  }

  private void assertSequence(AbstractSequenceMaxValueIncrementer configurable,
          AbstractSequenceMaxValueIncrementer initialized, String query) {
    configurable.setDataSource(dataSource);
    configurable.setIncrementerName("seq");
    assertThat(configurable.getSequenceQuery()).isEqualTo(query);
    assertThat(initialized.getSequenceQuery()).isEqualTo(query);
  }

  private void assertIdentity(AbstractIdentityColumnMaxValueIncrementer configurable,
          AbstractIdentityColumnMaxValueIncrementer initialized, String query) {
    configurable.setDataSource(dataSource);
    configurable.setIncrementerName("seq");
    configurable.setColumnName("id");
    assertThat(configurable.getIncrementStatement()).isEqualTo(query);
    assertThat(initialized.getIncrementStatement()).isEqualTo(query);
    assertThat(initialized.getIdentityStatement()).isEqualTo("select @@identity");
  }
}

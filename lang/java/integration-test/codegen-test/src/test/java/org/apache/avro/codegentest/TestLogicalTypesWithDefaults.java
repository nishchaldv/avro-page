/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro.codegentest;

import java.time.LocalDate;

import org.apache.avro.codegentest.testdata.LogicalTypesWithDefaults;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestLogicalTypesWithDefaults extends AbstractSpecificRecordTest {

  private static final LocalDate DEFAULT_VALUE = LocalDate.parse("1973-05-19");

  @Test
  void defaultValueOfNullableField() throws IOException {
    LogicalTypesWithDefaults instanceOfGeneratedClass = LogicalTypesWithDefaults.newBuilder()
        .setNonNullDate(LocalDate.now()).build();
    verifySerDeAndStandardMethods(instanceOfGeneratedClass);
  }

  @Test
  void defaultValueOfNonNullField() throws IOException {
    LogicalTypesWithDefaults instanceOfGeneratedClass = LogicalTypesWithDefaults.newBuilder()
        .setNullableDate(LocalDate.now()).build();
    Assert.assertEquals(DEFAULT_VALUE, instanceOfGeneratedClass.getNonNullDate());
    verifySerDeAndStandardMethods(instanceOfGeneratedClass);
  }

  @Test
  void withValues() throws IOException {
    LogicalTypesWithDefaults instanceOfGeneratedClass = LogicalTypesWithDefaults.newBuilder()
        .setNullableDate(LocalDate.now()).setNonNullDate(LocalDate.now()).build();
    verifySerDeAndStandardMethods(instanceOfGeneratedClass);
  }

}

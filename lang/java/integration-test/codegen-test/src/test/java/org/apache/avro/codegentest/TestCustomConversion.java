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

import org.apache.avro.codegentest.testdata.CustomConversionWithLogicalTypes;
import org.apache.avro.codegentest.testdata.LogicalTypesWithCustomConversion;
import org.apache.avro.codegentest.testdata.LogicalTypesWithCustomConversionIdl;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestCustomConversion extends AbstractSpecificRecordTest {

  @Test
  void nullValues() {
    LogicalTypesWithCustomConversion instanceOfGeneratedClass = LogicalTypesWithCustomConversion.newBuilder()
        .setNonNullCustomField(new CustomDecimal(BigInteger.valueOf(100), 2))
        .setNonNullFixedSizeString(new FixedSizeString("test")).build();
    verifySerDeAndStandardMethods(instanceOfGeneratedClass);
  }

  @Test
  void nullValuesIdl() {
    LogicalTypesWithCustomConversionIdl instanceOfGeneratedClass = LogicalTypesWithCustomConversionIdl.newBuilder()
        .setNonNullCustomField(new CustomDecimal(BigInteger.valueOf(100), 2))
        .setNonNullFixedSizeString(new FixedSizeString("test")).build();
    verifySerDeAndStandardMethods(instanceOfGeneratedClass);
  }

  @Test
  void nonNullValues() {
    LogicalTypesWithCustomConversion instanceOfGeneratedClass = LogicalTypesWithCustomConversion.newBuilder()
        .setNonNullCustomField(new CustomDecimal(BigInteger.valueOf(100), 2))
        .setNullableCustomField(new CustomDecimal(BigInteger.valueOf(3000), 2))
        .setNonNullFixedSizeString(new FixedSizeString("test")).setNullableFixedSizeString(new FixedSizeString("test2"))
        .build();
    verifySerDeAndStandardMethods(instanceOfGeneratedClass);
  }

  @Test
  void stringViolatesLimit() {
    assertThrows(IllegalArgumentException.class, () -> {
      LogicalTypesWithCustomConversion instanceOfGeneratedClass = LogicalTypesWithCustomConversion.newBuilder()
          .setNonNullCustomField(new CustomDecimal(BigInteger.valueOf(100), 2))
          .setNonNullFixedSizeString(new FixedSizeString("")).build();

      verifySerDeAndStandardMethods(instanceOfGeneratedClass);
    });
  }

  @Test
  void customConversionWithCustomLogicalType() {
    final CustomConversionWithLogicalTypes customConversionWithLogicalTypes = CustomConversionWithLogicalTypes
        .newBuilder().setCustomEnum(new CustomEnumType("TWO")).build();
    verifySerDeAndStandardMethods(customConversionWithLogicalTypes);
  }
}

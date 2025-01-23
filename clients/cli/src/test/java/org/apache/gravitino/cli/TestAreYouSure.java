/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestAreYouSure {

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  @BeforeEach
  void setUp() {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreExitFlg() {
    Main.useExit = true;
  }

  @AfterEach
  public void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void testCommandWithForce() {
    Assertions.assertTrue(AreYouSure.really(true));
  }

  @Test
  void testCommandWithInputY() {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream("Y".getBytes(StandardCharsets.UTF_8));
    System.setIn(inputStream);

    Assertions.assertTrue(AreYouSure.really(false));
  }

  @Test
  void testCommandWithInputN() {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream("N".getBytes(StandardCharsets.UTF_8));
    System.setIn(inputStream);

    Assertions.assertFalse(AreYouSure.really(false));
  }

  @Test
  void testCommandWithInputInvalid() {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream("Invalid".getBytes(StandardCharsets.UTF_8));
    System.setIn(inputStream);

    Assertions.assertFalse(AreYouSure.really(false));
  }

  @Test
  void testCommandWithInputMessage() {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream("Y".getBytes(StandardCharsets.UTF_8));
    System.setIn(inputStream);

    Assertions.assertTrue(AreYouSure.really(false, "output information"));
    String output = new String(outContent.toByteArray(), StandardCharsets.UTF_8).trim();
    Assertions.assertEquals("output information", output);
  }
}

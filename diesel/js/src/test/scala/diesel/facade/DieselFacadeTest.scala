/*
 * Copyright 2018 The Diesel Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package diesel.facade

import diesel.samples.calc.MyDsl
import munit.FunSuite

class DieselFacadeTest extends FunSuite {

  test("facade should create parse request") {
    val parseRequest  = DieselParsers.createParseRequest("hello")
    assertEquals(parseRequest.text, "hello")
    assert(parseRequest.axiom.isEmpty);
    val parseRequest2 = parseRequest.setAxiom("yalla")
    assertEquals(parseRequest2.text, "hello")
    assertEquals(parseRequest2.axiom.toOption, Some("yalla"))
  }

  test("facade should create predict request") {
    val predictRequest = DieselParsers.createPredictRequest("hello", 2)
    assertEquals(predictRequest.parseRequest.text, "hello")
    assert(predictRequest.parseRequest.axiom.isEmpty);
    assertEquals(predictRequest.offset, 2)
  }

  test("facade should parse calc dsl") {
    val facade = new DieselParserFacade(MyDsl)
    val res    = facade.parse(DieselParsers.createParseRequest("1 + pi"))
    assert(res.success)
    assert(res.error.isEmpty)
    assert(res.markers.isEmpty)
    assertEquals(res.styles.size, 3)
    val s0     = res.styles(0)
    assertEquals(s0.offset, 2)
    assertEquals(s0.length, 1)
    assertEquals(s0.name, "keyword")
    val s1     = res.styles(1)
    assertEquals(s1.offset, 0)
    assertEquals(s1.length, 1)
    assertEquals(s1.name, "string")
    val s2     = res.styles(2)
    assertEquals(s2.offset, 4)
    assertEquals(s2.length, 2)
    assertEquals(s2.name, "constant")
  }

  test("facade should predict calc dsl") {
    val facade = new DieselParserFacade(MyDsl)
    val res    = facade.predict(DieselParsers.createPredictRequest("1 + ", 3))
    assert(res.success)
    assert(res.error.isEmpty)
    println("****", res)
    assertEquals(res.proposals.length, 5)
    val p0     = res.proposals(0)
    assertEquals(p0.text, "0")
    assert(p0.replace.isEmpty)
    val p1     = res.proposals(1)
    assertEquals(p1.text, "pi")
    assert(p1.replace.isEmpty)
  }

}
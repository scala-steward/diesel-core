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

package diesel.samples

import diesel.facade.DieselParserFacade
import diesel.samples.glsl.Glsl
import diesel.samples.jsmodeldsl.BmdDsl
import diesel.samples.sfeel.SFeel

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("DieselSamples")
object DieselSamples {

  @JSExport
  def createBmdParser(): DieselParserFacade =
    new DieselParserFacade(BmdDsl, Some(BmdDsl.completionConfiguration))

  @JSExport
  def createGlslParser(): DieselParserFacade = new DieselParserFacade(Glsl)

  @JSExport
  def createSFeelParser(): DieselParserFacade = new DieselParserFacade(SFeel)

}

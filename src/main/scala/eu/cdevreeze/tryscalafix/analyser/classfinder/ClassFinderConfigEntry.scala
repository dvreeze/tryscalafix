/*
 * Copyright 2024-2024 Chris de Vreeze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cdevreeze.tryscalafix.analyser.classfinder

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import metaconfig.ConfDecoder
import metaconfig.generic.Surface

/**
 * ClassFinder config entry.
 *
 * @author
 *   Chris de Vreeze
 */
final case class ClassFinderConfigEntry(
    typeOfEntry: String,
    searchInputSymbols: List[String],
    classCategoryDisplayName: String
)

object ClassFinderConfigEntry {

  val knownEntryTypes: Set[String] =
    Set(
      "HasSuperType",
      "UsesType",
      "UsesMethod",
      "UsesTypeOrSubType",
      "UsesAnnotation"
    ) // Poor man's enumeration (limitation of metaconfig)

  // Mind the two dots below, that must be replaced to get the symbol
  val default: ClassFinderConfigEntry =
    ClassFinderConfigEntry("HasSuperType", List("javax/servlet/http/HttpServlet.."), "Servlet")

  implicit val surface: Surface[ClassFinderConfigEntry] =
    metaconfig.generic.deriveSurface[ClassFinderConfigEntry]

  implicit val decoder: ConfDecoder[ClassFinderConfigEntry] = metaconfig.generic.deriveDecoder(default)

  implicit val jsonDecoder: Decoder[ClassFinderConfigEntry] = deriveDecoder
}

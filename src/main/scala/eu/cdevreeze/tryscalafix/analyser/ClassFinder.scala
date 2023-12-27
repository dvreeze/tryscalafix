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

package eu.cdevreeze.tryscalafix.analyser

import eu.cdevreeze.tryscalafix.SemanticDocumentAnalyser
import eu.cdevreeze.tryscalafix.SemanticDocumentAnalyserFactory
import io.circe.Json
import scalafix.v1.SemanticDocument

/**
 * SemanticDocumentAnalyser that finds given types (or sub-types). Search criteria can be based on type (or sub-type),
 * but also on absence of certain methods, use of a certain type within methods, etc.
 *
 * This analyser can help a lot in showing how an "enterprise" code base hangs together in terms of HTTP endpoints,
 * Kafka consumers and producers, database repository classes, Spring components etc.
 *
 * This ClassFinder only searches for classes in the passed SemanticDocument. It does not do so for class files found on
 * the class path, for example coming from compilation of Java source files. In principle they could still be analysed,
 * starting from the Symbols (somewhat comparable to Java reflection).
 *
 * @author
 *   Chris de Vreeze
 */
final class ClassFinder() extends SemanticDocumentAnalyser[Json] {

  override def apply(doc: SemanticDocument): Json = {
    ???
  }

}

object ClassFinder extends SemanticDocumentAnalyserFactory[Json, ClassFinder] {

  override def create(config: SemanticDocumentAnalyserFactory.JsonConfig): ClassFinder = {
    ???
  }

}

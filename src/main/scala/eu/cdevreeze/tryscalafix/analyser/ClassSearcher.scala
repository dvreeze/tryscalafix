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
import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinder.SearchResult
import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinder
import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinderConfig
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Elem
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Scope
import scalafix.v1.SemanticDocument
import scalafix.v1.XtensionTreeScalafix

import java.nio.file.Path
import java.nio.file.Paths
import scala.meta.inputs.Input
import scala.util.chaining.scalaUtilChainingOps

/**
 * SemanticDocumentAnalyser that finds given types (or sub-types). Search criteria can be based on type (or sub-type),
 * but also on presence of certain methods, use of a certain type within methods, etc.
 *
 * This analyser can help a lot in showing how an "enterprise" code base hangs together in terms of HTTP endpoints,
 * Kafka consumers and producers, database repository classes, Spring components etc.
 *
 * This ClassFinder only searches for classes in the passed SemanticDocument. It does not do so for other class files
 * found on the class path, for example coming from compilation of Java source files. In principle they could still be
 * analysed, starting from the Symbols (somewhat comparable to Java reflection).
 *
 * @author
 *   Chris de Vreeze
 */
final class ClassSearcher(val config: ClassFinderConfig) extends SemanticDocumentAnalyser[Elem] {

  private implicit val scope: Scope = Scope.empty

  override def apply(doc: SemanticDocument): Elem = {
    require(!config.isEmpty, "Empty ClassFinderConfig not allowed")

    implicit val implicitDoc: SemanticDocument = doc

    val classFinders: Seq[ClassFinder] =
      config.entries.flatMap(entry => ClassFinder.tryToCreateFromConfigEntry(entry).toOption)

    val fileName: Path = doc.input.asInstanceOf[Input.VirtualFile].path.pipe(Paths.get(_)).getFileName

    val searchResults: Seq[SearchResult] = classFinders.map(_.findClasses)

    import eu.cdevreeze.tryscalafix.internal.xmlsupport.Node._

    // TODO Enhance/improve
    elem(
      name = elemName("sourceFile"),
      attrs = Map(attrName("fileName") -> fileName.toAbsolutePath.toString),
      children = searchResults.map { searchResult =>
        elem(
          name = elemName("searchResult"),
          children = Seq(
            textElem(elemName("classCategory"), text(searchResult.classCategoryDisplayName)),
            elem(
              name = elemName("definitions"),
              children = searchResult.definitions.map { defn =>
                textElem(elemName("definition"), text(defn.symbol.toString))
              }
            )
          )
        )
      }
    )
  }

}

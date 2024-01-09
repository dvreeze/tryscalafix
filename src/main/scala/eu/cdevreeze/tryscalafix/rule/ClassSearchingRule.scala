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

package eu.cdevreeze.tryscalafix.rule

import eu.cdevreeze.tryscalafix.analyser.ClassSearcher
import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinderConfig
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Elem
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Node
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Scope
import eu.cdevreeze.tryscalafix.rule.ClassSearchingRule.ClassDefinition
import eu.cdevreeze.tryscalafix.rule.ClassSearchingRule.PrimaryConstructor
import eu.cdevreeze.tryscalafix.rule.ClassSearchingRule.SearchResult
import io.circe.Encoder
import io.circe.Json
import io.circe.Printer
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import metaconfig.Configured
import scalafix.patch.Patch
import scalafix.v1._

import java.util.concurrent.atomic.AtomicReference
import javax.xml.namespace.QName
import scala.util.chaining.scalaUtilChainingOps

/**
 * SemanticRule that finds classes by given criteria, by invoking ClassSearcher.
 *
 * @author
 *   Chris de Vreeze
 */
final class ClassSearchingRule(val config: ClassFinderConfig) extends SemanticRule("ClassSearchingRule") {

  def this() = this(ClassFinderConfig.default)

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    config.conf
      .getOrElse("ClassSearchingRule")(this.config)
      .map(newConfig => new ClassSearchingRule(newConfig))
  }

  private implicit val parentScope: Scope = Scope.empty

  private final val accumulatedElem = new AtomicReference(
    Node.elem(
      name = Node.elemName("DummyRoot"),
      children = Seq.empty
    )
  )

  override def beforeStart(): Unit = {
    super.beforeStart()

    this.accumulatedElem.set {
      Node.elem(
        name = Node.elemName("ClassSearcher"),
        children = Seq.empty
      )
    }
  }

  override def afterComplete(): Unit = {
    val resultElem: Elem = accumulatedElem.get()

    val searchResultElems: Seq[Elem] = resultElem.filterDescendantElems(_.name.getLocalPart == "searchResult")

    val searchResults: Seq[SearchResult] = searchResultElems.map { elm =>
      SearchResult(
        classCategory = elm.findFirstChildElem(_.name.getLocalPart == "classCategory").map(_.text).getOrElse(""),
        foundClasses = elm.filterDescendantElems(_.name.getLocalPart == "definition").map { defnElm =>
          ClassDefinition(
            classSymbol = Symbol(defnElm.findFirstChildElem(_.name.getLocalPart == "symbol").map(_.text).getOrElse("")),
            primaryConstructor =
              defnElm.findFirstChildElem(_.name.getLocalPart == "primaryConstructor").map { ctorElm =>
                ctorElm
                  .filterChildElems(_.name.getLocalPart == "par")
                  .map(_.text)
                  .pipe(pars => PrimaryConstructor(pars))
              }
          )
        }
      )
    }

    val groupedSearchResults: Map[String, Seq[SearchResult]] = searchResults.groupBy(_.classCategory)

    val searchResultSummaries: Seq[SearchResult] = groupedSearchResults.toSeq
      .map { case (classCategory, resultGroups) =>
        SearchResult(classCategory, resultGroups.flatMap(_.foundClasses).distinct)
      }

    val searchResultSummariesJson: Json = searchResultSummaries.asJson

    val jsonString: String = searchResultSummariesJson.printWith(Printer.spaces2)

    println(jsonString)

    super.afterComplete()
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val analyser = new ClassSearcher(this.config)

    val elem: Elem = analyser(doc)
      .findFirstDescendantElemOrSelf(_.name == new QName("sourceFile"))
      .getOrElse(sys.error(s"Could not find 'sourceFile' element"))

    accumulatedElem.getAndUpdate { e =>
      e.unsafeUpdateApi.plusChild(elem)
    }

    Patch.empty
  }

}

object ClassSearchingRule {

  final case class PrimaryConstructor(pars: Seq[String])

  final case class ClassDefinition(classSymbol: Symbol, primaryConstructor: Option[PrimaryConstructor])

  final case class SearchResult(classCategory: String, foundClasses: Seq[ClassDefinition])

  implicit val symbolEncoder: Encoder[Symbol] = Encoder.encodeString.contramap(_.toString)

  implicit val primaryConstructorEncoder: Encoder[PrimaryConstructor] = deriveEncoder

  implicit val classDefinitionEncoder: Encoder[ClassDefinition] = deriveEncoder

  implicit val searchResultEncoder: Encoder[SearchResult] = deriveEncoder
}

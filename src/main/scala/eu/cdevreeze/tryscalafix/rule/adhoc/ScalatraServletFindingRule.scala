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

package eu.cdevreeze.tryscalafix.rule.adhoc

import eu.cdevreeze.tryscalafix.internal.QuerySupport.WithQueryMethods
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.getParentSymbolsOrSelf
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isAbstract
import eu.cdevreeze.tryscalafix.rule.adhoc.ScalatraServletFindingRule.HttpFunctionCall
import eu.cdevreeze.tryscalafix.rule.adhoc.ScalatraServletFindingRule.ScalatraServletData
import io.circe.Encoder
import io.circe.Json
import io.circe.Printer
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import scalafix.Patch
import scalafix.v1.SemanticDocument
import scalafix.v1.SemanticRule
import scalafix.v1.Symbol
import scalafix.v1.SymbolMatcher
import scalafix.v1.XtensionTreeScalafix

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import scala.meta.Defn
import scala.meta.Lit
import scala.meta.Term
import scala.meta.inputs.Input
import scala.util.chaining.scalaUtilChainingOps

/**
 * Rule that finds concrete Scalatra servlet classes, showing calls to ScalatraBase methods representing HTTP methods.
 *
 * If Scalatra is not on the class path, this rule will not break.
 *
 * @author
 *   Chris de Vreeze
 */
final class ScalatraServletFindingRule extends SemanticRule("ScalatraServletFindingRule") {

  private val scalatraBase: Symbol = Symbol("org/scalatra/ScalatraBase#")

  private val httpFunctions: Set[Symbol] = Set(
    "org/scalatra/ScalatraBase#get().",
    "org/scalatra/ScalatraBase#post().",
    "org/scalatra/ScalatraBase#put().",
    "org/scalatra/ScalatraBase#head().",
    "org/scalatra/ScalatraBase#delete().",
    "org/scalatra/ScalatraBase#options().",
    "org/scalatra/ScalatraBase#patch()."
  ).map(Symbol.apply)

  private val httpFunctionMatcher: SymbolMatcher = httpFunctions.map(s => SymbolMatcher.exact(s.value)).reduce(_ + _)

  private val jsonOutputs: AtomicReference[Seq[Json]] = new AtomicReference()

  override def beforeStart(): Unit = {
    super.beforeStart()

    this.jsonOutputs.set(Seq.empty)
  }

  override def afterComplete(): Unit = {
    val jsonResult: Json = Json.obj(
      "scalatraServlets" -> jsonOutputs.get.pipe(results => Json.fromValues(results))
    )

    val jsonString: String = jsonResult.printWith(Printer.spaces2)

    println(jsonString)

    super.afterComplete()
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    if (httpFunctions.exists(_.info.isEmpty)) {
      Patch.empty
    } else {
      val fileName: Path = doc.input.asInstanceOf[Input.VirtualFile].path.pipe(Paths.get(_)).getFileName

      val classSymbolMatcher: SymbolMatcher = SymbolMatcher.exact(scalatraBase.toString)

      // Assuming classes, not singleton/companion objects
      val servletClassDefns: Seq[Defn.Class] = doc.tree.filterDescendants[Defn.Class] { t =>
        getParentSymbolsOrSelf(t.symbol).exists { pt =>
          classSymbolMatcher.matches(pt)
        } && !t.mods.exists(isAbstract)
      }

      val servletDataSeq: Seq[ScalatraServletData] =
        servletClassDefns.map { classDefn => collectScalatraServletData(classDefn, fileName)(doc) }

      if (servletDataSeq.nonEmpty) {
        val json: Json = servletDataSeq.asJson

        jsonOutputs.updateAndGet(_.appended(json))
      }

      Patch.empty
    }
  }

  private def collectScalatraServletData(scalatraClassDefn: Defn.Class, fileName: Path)(implicit
      doc: SemanticDocument
  ): ScalatraServletData = {
    val topmostFoundFunctionCalls: Seq[Term.Apply] =
      scalatraClassDefn.findTopmost[Term.Apply](t => httpFunctionMatcher.matches(t))

    val httpFunctionCalls: Seq[HttpFunctionCall] = topmostFoundFunctionCalls.map { term =>
      val nestedCallsOrSelf: Seq[Term.Apply] = term.filterDescendantsOrSelf[Term.Apply](_.symbol == term.symbol)

      // Below, we could have used quasiquotes instead.
      // For quasiquotes, see https://scalameta.org/docs/trees/quasiquotes.html
      val uriPathOption: Option[String] = nestedCallsOrSelf.collectFirst {
        case Term.Apply.After_4_6_0(
              Term.Name(_),
              Term.ArgClause(List(Lit.String(uriPath), _*), _)
            ) =>
          uriPath
      }

      HttpFunctionCall(term.getClass.getSimpleName, term.symbol, uriPathOption)
    }

    ScalatraServletData(fileName.toString, scalatraClassDefn.symbol, httpFunctionCalls)
  }

}

object ScalatraServletFindingRule {

  final case class HttpFunctionCall(termClassName: String, symbol: Symbol, uriPathOption: Option[String])

  final case class ScalatraServletData(fileName: String, classSymbol: Symbol, httpFunctionCalls: Seq[HttpFunctionCall])

  private implicit val symbolEncoder: Encoder[Symbol] = Encoder.encodeString.contramap(_.toString)

  private implicit val httpFunctionCallEncoder: Encoder[HttpFunctionCall] = deriveEncoder[HttpFunctionCall]

  private implicit val scalatraServletDataEncoder: Encoder[ScalatraServletData] = deriveEncoder[ScalatraServletData]
}

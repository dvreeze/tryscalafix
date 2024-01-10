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
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isAbstract
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.resolveLocalSymbol
import eu.cdevreeze.tryscalafix.rule.adhoc.KafkaProducerFindingRule.SourceFileResult
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
import scala.meta.Stat
import scala.meta.Term
import scala.meta.inputs.Input
import scala.util.chaining.scalaUtilChainingOps

/**
 * Rule that finds Kafka Producer calls to method "send", and if feasible, the event type passed.
 *
 * If Kafka support is not on the class path, this rule will not break.
 *
 * @author
 *   Chris de Vreeze
 */
final class KafkaProducerFindingRule() extends SemanticRule("KafkaProducerFindingRule") {

  private val kafkaProducerSymbol: Symbol = Symbol("org/apache/kafka/clients/producer/Producer#")
  private val kafkaProducerSymbolMatcher: SymbolMatcher = SymbolMatcher.exact(kafkaProducerSymbol.toString)

  private val kafkaProducerRecordSymbol: Symbol = Symbol("org/apache/kafka/clients/producer/ProducerRecord#")
  private val kafkaProducerRecordSymbolMatcher: SymbolMatcher = SymbolMatcher.exact(kafkaProducerRecordSymbol.toString)

  private val sendMethodDisplayName = "send"

  private val jsonOutputs: AtomicReference[Seq[Json]] = new AtomicReference()

  override def beforeStart(): Unit = {
    super.beforeStart()

    this.jsonOutputs.set(Seq.empty)
  }

  override def afterComplete(): Unit = {
    val jsonResult: Json = Json.obj(
      "kafkaProducerUsers" -> jsonOutputs.get.pipe(results => Json.fromValues(results))
    )

    val jsonString: String = jsonResult.printWith(Printer.spaces2)

    println(jsonString)

    super.afterComplete()
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    if (kafkaProducerSymbol.info.isEmpty) {
      Patch.empty
    } else {
      val fileName: Path = doc.input.asInstanceOf[Input.VirtualFile].path.pipe(Paths.get(_)).getFileName

      val matchingMethodCalls: Seq[Term.Apply] = doc.tree.findTopmost[Term.Apply] { t =>
        // Matching terms must be found within a function, and not within imports or as a package, for example
        kafkaProducerSymbolMatcher.matches(t.symbol.owner) &&
        t.symbol.displayName == sendMethodDisplayName &&
        t.findFirstAncestor[Defn.Def]((_: Defn.Def) => true).nonEmpty
      }

      val eventTypes: Seq[Symbol] = matchingMethodCalls.flatMap(t => findEventType(t)).distinctBy(_.value)

      val classOrObjectDefns: Seq[Defn] =
        matchingMethodCalls.flatMap { t =>
          t.findFirstAncestor[Defn with Stat.WithMods] { (anc: Defn with Stat.WithMods) =>
            (anc.isInstanceOf[Defn.Class] || anc.isInstanceOf[Defn.Object]) && !anc.mods.exists(isAbstract)
          }
        }.distinct

      if (classOrObjectDefns.nonEmpty) {
        val result: SourceFileResult =
          SourceFileResult(fileName.toString, eventTypes, classOrObjectDefns.map(_.symbol))

        val json: Json = result.asJson

        jsonOutputs.updateAndGet(_.appended(json))
      }

      Patch.empty
    }
  }

  private def findEventType(sendFunctionCall: Term.Apply)(implicit doc: SemanticDocument): Option[Symbol] = {
    for {
      argClause <- Option(sendFunctionCall.argClause)
      firstArg <- argClause.values.headOption.flatMap(t =>
        resolveLocalSymbol(t.symbol).collect { case d: Defn.Val => d.rhs }.orElse(Option(t))
      )
      if kafkaProducerRecordSymbolMatcher.matches(firstArg.symbol)
      firstArgAsConstructorCall <- firstArg.findFirstDescendantOrSelf[Term.New]((_: Term.New) => true)
      ev <- firstArgAsConstructorCall.init.argClauses.headOption
        .flatMap(_.values.ensuring(_.sizeIs >= 3).drop(2).headOption)
        .flatMap(t => resolveLocalSymbol(t.symbol).collect { case d: Defn.Val => d.rhs }.orElse(Option(t)))
    } yield {
      if (ev.symbol.info.exists(_.isMethod)) ev.symbol.owner else ev.symbol
    }
  }

}

object KafkaProducerFindingRule {

  final case class SourceFileResult(
      fileName: String,
      eventTypesDiscovered: Seq[Symbol],
      classes: Seq[Symbol]
  )

  implicit val symbolEncoder: Encoder[Symbol] = Encoder.encodeString.contramap(_.toString)

  implicit val sourceFileResultEncoder: Encoder[SourceFileResult] = deriveEncoder

}

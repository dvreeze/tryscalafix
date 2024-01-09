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
import eu.cdevreeze.tryscalafix.rule.adhoc.KafkaEventProcessorFindingRule.EventTypeFinder
import eu.cdevreeze.tryscalafix.rule.adhoc.KafkaEventProcessorFindingRule.FoundClass
import io.circe.Encoder
import io.circe.Json
import io.circe.Printer
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import metaconfig.ConfDecoder
import metaconfig.Configured
import metaconfig.generic.Surface
import scalafix.Patch
import scalafix.v1.ClassSignature
import scalafix.v1.Configuration
import scalafix.v1.MethodSignature
import scalafix.v1.Rule
import scalafix.v1.SemanticDocument
import scalafix.v1.SemanticRule
import scalafix.v1.SemanticType
import scalafix.v1.Symbol
import scalafix.v1.SymbolInformation
import scalafix.v1.SymbolMatcher
import scalafix.v1.TypeRef
import scalafix.v1.ValueSignature
import scalafix.v1.XtensionTreeScalafix

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import scala.meta.Defn
import scala.meta.Pat
import scala.meta.Term
import scala.meta.Type
import scala.meta.inputs.Input
import scala.util.chaining.scalaUtilChainingOps

/**
 * Rule that finds concrete Kafka event processor classes, assuming that they have a given super-type.
 *
 * If Kafka support is not on the class path, this rule will not break.
 *
 * @author
 *   Chris de Vreeze
 */
final class KafkaEventProcessorFindingRule(val config: KafkaEventProcessorFinderConfig)
    extends SemanticRule("KafkaEventProcessorFindingRule") {

  def this() = this(KafkaEventProcessorFinderConfig.default)

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    config.conf
      .getOrElse("KafkaEventProcessorFindingRule")(this.config)
      .map(newConfig => new KafkaEventProcessorFindingRule(newConfig))
  }

  private val eventProcessorSymbol = Symbol(config.superType.replace("..", "#"))

  private val receiveMethodName: String = config.receiveMethodName

  private val eventTypeFinderSimpleName: String = config.eventTypeFinderSimpleName

  private val jsonOutputs: AtomicReference[Seq[Json]] = new AtomicReference()

  override def beforeStart(): Unit = {
    super.beforeStart()

    this.jsonOutputs.set(Seq.empty)
  }

  override def afterComplete(): Unit = {
    val jsonResult: Json = Json.obj(
      "kafkaEventProcessors" -> jsonOutputs.get.pipe(results => Json.fromValues(results))
    )

    val jsonString: String = jsonResult.printWith(Printer.spaces2)

    println(jsonString)

    super.afterComplete()
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    if (eventProcessorSymbol.info.isEmpty) {
      Patch.empty
    } else {
      val fileName: Path = doc.input.asInstanceOf[Input.VirtualFile].path.pipe(Paths.get(_)).getFileName

      val classSymbolMatcher: SymbolMatcher = SymbolMatcher.exact(eventProcessorSymbol.toString)

      // Assuming classes, not singleton/companion objects
      val servletClassDefns: Seq[Defn.Class] = doc.tree.filterDescendants[Defn.Class] { t =>
        getParentSymbolsOrSelf(t.symbol).exists { pt =>
          classSymbolMatcher.matches(pt)
        } && !t.mods.exists(isAbstract)
      }

      val foundClasses: Seq[FoundClass] = servletClassDefns.map { classDefn =>
        val receiveMethodDefns: Seq[Defn.Def] =
          classDefn.findTopmost[Defn.Def](t => t.symbol.info.map(_.displayName).contains(receiveMethodName))
        val eventTypes: Set[Symbol] = receiveMethodDefns.flatMap(t => findEventTypes(t)).distinctBy(_.value).toSet

        FoundClass(
          fileName = fileName.toString,
          classSymbol = classDefn.symbol,
          classSignature = classDefn.symbol.info
            .map(info => info.signature.asInstanceOf[ClassSignature].toString)
            .getOrElse(sys.error(s"Class definition ${classDefn.symbol} not on class path")),
          foundEventTypes = eventTypes
        )
      }

      if (foundClasses.nonEmpty) {
        val json: Json = foundClasses.asJson

        jsonOutputs.updateAndGet(_.appended(json))
      }

      Patch.empty
    }
  }

  private def findEventTypes(receiveMethodCall: Defn.Def)(implicit doc: SemanticDocument): Set[Symbol] = {
    eventTypeFinderSimpleName match {
      case "EventTypeFinderInspectingFirstParameter" =>
        EventTypeFinderInspectingFirstParameter.findEventTypes(receiveMethodCall)
      case "EventTypeFinderInspectingReturnedPartialFunction" =>
        EventTypeFinderInspectingReturnedPartialFunction.findEventTypes(receiveMethodCall)
      case _ => Set.empty
    }
  }

}

object KafkaEventProcessorFindingRule {

  final case class FoundClass(
      fileName: String,
      classSymbol: Symbol,
      classSignature: String,
      foundEventTypes: Set[Symbol]
  )

  implicit val symbolEncoder: Encoder[Symbol] = Encoder.encodeString.contramap(_.toString)

  implicit val foundClassEncoder: Encoder[FoundClass] = deriveEncoder

  sealed trait EventTypeFinder {

    def findEventTypes(receiveMethodDefn: Defn.Def)(implicit doc: SemanticDocument): Set[Symbol]
  }

}

object EventTypeFinderInspectingFirstParameter extends EventTypeFinder {

  def findEventTypes(receiveMethodDefn: Defn.Def)(implicit doc: SemanticDocument): Set[Symbol] = {
    val receiveMethod: Symbol = receiveMethodDefn.symbol
    val methodSignature = receiveMethod.info.get.signature.asInstanceOf[MethodSignature]
    val parameterLists: List[List[SymbolInformation]] = methodSignature.parameterLists

    require(
      parameterLists.nonEmpty && parameterLists.head.nonEmpty,
      s"Expected at least one parameter list, and at least one parameter in first parameter list"
    )
    val par: SymbolInformation = parameterLists.head.head.pipe(p => p.ensuring(_.isParameter, s"Not a parameter: ${p.symbol}"))

    par.signature.pipe {
      case ValueSignature(TypeRef(_, sym, _)) => Set(sym)
      case _                                  => Set.empty
    }
  }

}

object EventTypeFinderInspectingReturnedPartialFunction extends EventTypeFinder {

  def findEventTypes(receiveMethodDefn: Defn.Def)(implicit doc: SemanticDocument): Set[Symbol] = {
    val receiveMethod: Symbol = receiveMethodDefn.symbol
    val methodSignature = receiveMethod.info.get.signature.asInstanceOf[MethodSignature]
    val parameterLists: List[List[SymbolInformation]] = methodSignature.parameterLists
    val returnType: SemanticType = methodSignature.returnType

    require(
      parameterLists.isEmpty || parameterLists.head.isEmpty,
      s"Expected no parameter lists, or no parameters in first parameter list"
    )
    require(returnType.isInstanceOf[TypeRef], s"Expected the return type to be a TypeRef")

    val partialFunctionMatcher: SymbolMatcher = SymbolMatcher.exact("scala/PartialFunction#")
    require(
      getParentSymbolsOrSelf(returnType.asInstanceOf[TypeRef].symbol).exists { p =>
        partialFunctionMatcher.matches(p)
      },
      s"Expected the return type to be PartialFunction (or a subtype)"
    )

    receiveMethodDefn.body match {
      case Term.PartialFunction(
            cases
          ) =>
        cases.map(_.pat).collect { case Pat.Typed(_, tn @ Type.Name(_)) => tn.symbol }.distinctBy(_.value).toSet
      case _ => Set.empty
    }
  }

}

final case class KafkaEventProcessorFinderConfig(
    superType: String,
    receiveMethodName: String,
    eventTypeFinderSimpleName: String, // One of a known set of event type finders
    displayName: String
)

object KafkaEventProcessorFinderConfig {

  // Mind the two dots below, that must be replaced to get the symbol
  val default: KafkaEventProcessorFinderConfig =
    KafkaEventProcessorFinderConfig(
      "com/test/kafka/EventProcessor..",
      "receive",
      "EventTypeFinderInspectingReturnedPartialFunction",
      "Kafka EventProcessor"
    )

  implicit val surface: Surface[KafkaEventProcessorFinderConfig] =
    metaconfig.generic.deriveSurface[KafkaEventProcessorFinderConfig]

  implicit val decoder: ConfDecoder[KafkaEventProcessorFinderConfig] = metaconfig.generic.deriveDecoder(default)
}

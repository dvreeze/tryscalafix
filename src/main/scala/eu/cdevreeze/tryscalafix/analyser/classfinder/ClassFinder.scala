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

import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinder.SearchResult
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isPublicClassOrObjectSymbol
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isPublicMethodSymbol
import scalafix.v1.SemanticDocument
import scalafix.v1.Symbol
import scalafix.v1.SymbolMatcher

import scala.meta.Defn
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

/**
 * Finder of definitions of classes (or singleton objects) given certain specific search criteria, such as finding
 * sub-types of a certain type, or having methods taking or returning a certain type.
 *
 * @author
 *   Chris de Vreeze
 */
trait ClassFinder {

  /**
   * A "pretty" name for the (kind of) class, such as "Servlet" or "Kafka event processor".
   */
  def classCategoryDisplayName: String

  /**
   * The symbols used for matching in the implementation of this Class finder.
   */
  def searchInputSymbols: Seq[Symbol]

  /**
   * Runs the class finder on a given SemanticDocument.
   */
  def findClasses(implicit doc: SemanticDocument): SearchResult
}

object ClassFinder {

  final case class FoundSymbols(description: String, symbols: Seq[Symbol]) {

    def plusSymbol(sym: Symbol): FoundSymbols = {
      FoundSymbols(description, this.symbols.appended(sym).distinct)
    }

  }

  final case class SearchResult(
      definitions: Seq[Defn],
      classCategoryDisplayName: String,
      foundSymbolsOption: Option[FoundSymbols]
  )

  /**
   * Factory method for ClassFinder, keeping in mind that the configured symbols to search for may be absent from the
   * class path.
   */
  def tryToCreateFromConfigEntry(
      configEntry: ClassFinderConfigEntry
  )(implicit doc: SemanticDocument): Try[ClassFinder] = {
    require(
      ClassFinderConfigEntry.knownEntryTypes.contains(configEntry.typeOfEntry),
      s"Unknown config entry type: '${configEntry.typeOfEntry}'"
    )

    val searchInputSymbols: Seq[Symbol] = configEntry.searchInputSymbols.ensuring(_.nonEmpty).map { symbolString =>
      // Replacing ".." in HOCON by "#"
      symbolString.replace("..", "#").pipe(Symbol.apply)
    }

    // Checks below may fail if the symbol is unknown due to it not being on the classpath

    configEntry.typeOfEntry match {
      case "HasSuperType" =>
        Try(searchInputSymbols.foreach(checkClassSymbol)).flatMap { _ =>
          Try(new SubTypeDefinitionFinder(searchInputSymbols, configEntry.classCategoryDisplayName))
        }
      case "UsesType" =>
        Try(searchInputSymbols.foreach(checkClassSymbol)).flatMap { _ =>
          Try(new TypeUserFinder(searchInputSymbols, configEntry.classCategoryDisplayName))
        }
      case "UsesMethod" =>
        Try(searchInputSymbols.foreach(checkMethodSymbol)).flatMap { _ =>
          Try(new MethodUserFinder(searchInputSymbols, configEntry.classCategoryDisplayName))
        }
      case "UsesTypeOrSubType" =>
        Try(searchInputSymbols.foreach(checkClassSymbol)).flatMap { _ =>
          Try(new TypeOrSubtypeUserFinder(searchInputSymbols, configEntry.classCategoryDisplayName))
        }
      case "UsesAnnotation" =>
        Try(searchInputSymbols.foreach(checkClassSymbol)).flatMap { _ =>
          Try(new AnnotationUserFinder(searchInputSymbols, configEntry.classCategoryDisplayName))
        }
      case _ =>
        sys.error(s"Unknown config entry type: '${configEntry.typeOfEntry}'")
    }
  }

  /**
   * The symbols turned into a SymbolMatcher that matches on those symbols. Typically used for convenience in
   * ClassFinder implementations, to turn search input symbols into a SymbolMatcher.
   */
  final def toSymbolMatcher(symbols: Seq[Symbol]): SymbolMatcher = {
    symbols.map(sym => SymbolMatcher.exact(sym.toString)).reduce(_ + _)
  }

  // Helper methods

  private def checkClassSymbol(sym: Symbol)(implicit doc: SemanticDocument): Unit = {
    require(
      isPublicClassOrObjectSymbol(sym),
      s"Not a public class/trait/interface/object: $sym"
    )
  }

  private def checkMethodSymbol(sym: Symbol)(implicit doc: SemanticDocument): Unit = {
    require(
      isPublicMethodSymbol(sym),
      s"Not a public method: $sym"
    )
  }

}

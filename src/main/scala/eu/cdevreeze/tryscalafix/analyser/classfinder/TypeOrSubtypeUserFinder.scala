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

import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinder.FoundSymbols
import eu.cdevreeze.tryscalafix.analyser.classfinder.ClassFinder.SearchResult
import eu.cdevreeze.tryscalafix.internal.QuerySupport.WithQueryMethods
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.getParentSymbolsOrSelf
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isAbstract
import scalafix.v1._

import scala.meta.Defn
import scala.meta.Stat
import scala.meta.Term
import scala.util.chaining.scalaUtilChainingOps

/**
 * Finder of definitions of non-abstract types that use any of the types given as search input symbols, either directly
 * or indirectly using those types by inheritance.
 *
 * @author
 *   Chris de Vreeze
 */
final class TypeOrSubtypeUserFinder(val searchInputSymbols: Seq[Symbol], val classCategoryDisplayName: String)
    extends ClassFinder {

  require(searchInputSymbols.nonEmpty, "Expected at least one search input symbol")

  override def findClasses(implicit doc: SemanticDocument): ClassFinder.SearchResult = {
    require(
      searchInputSymbols.forall(
        _.info.exists(info => info.isClass || info.isInterface || info.isTrait || info.isObject)
      ),
      s"Not all search input symbols are classes/interfaces/traits/objects"
    )

    val symbolMatcher: SymbolMatcher = ClassFinder.toSymbolMatcher(searchInputSymbols)

    // For performance, first collecting matching symbol owners, and then reusing this Set for fast lookups

    val matchingSymbolOwners: Set[Symbol] =
      doc.tree
        .filterDescendants[Term](_.symbol.info.exists(_.isMethod))
        .map(_.symbol.owner)
        .toSet
        .filter(symOwner => getParentSymbolsOrSelf(symOwner).exists(ps => symbolMatcher.matches(ps)))

    val matchingTerms: Seq[Term] = doc.tree.filterDescendants[Term] { t =>
      // Matching terms (matching the term's symbol owner) must be found within a function, and not within
      // imports or as a package, for example
      matchingSymbolOwners.contains(t.symbol.owner) && t.findFirstAncestor[Defn.Def]((_: Defn.Def) => true).nonEmpty
    }

    val classOrObjectDefns: Seq[Defn] =
      matchingTerms.flatMap { t =>
        t.findFirstAncestor[Defn with Stat.WithMods]((anc: Defn with Stat.WithMods) =>
          (anc.isInstanceOf[Defn.Class] || anc.isInstanceOf[Defn.Object]) && !anc.mods.exists(isAbstract)
        )
      }.distinct

    val foundSymbols: FoundSymbols = FoundSymbols(
      s"Found used sub-types or self of [ ${searchInputSymbols.mkString(", ")} ]",
      matchingTerms.map(_.symbol.owner).distinct
    )

    classOrObjectDefns.pipe(defns => SearchResult(defns, classCategoryDisplayName, Some(foundSymbols)))
  }

}

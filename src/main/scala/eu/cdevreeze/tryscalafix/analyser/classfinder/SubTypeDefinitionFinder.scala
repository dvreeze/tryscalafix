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

import eu.cdevreeze.tryscalafix.internal.QuerySupport.WithQueryMethods
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.getParentSymbolsOrSelf
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isAbstract
import scalafix.v1._

import scala.meta.Defn
import scala.util.chaining.scalaUtilChainingOps

/**
 * Finder of definitions of non-abstract sub-types of the types given as search input symbols.
 *
 * @author
 *   Chris de Vreeze
 */
final class SubTypeDefinitionFinder(val searchInputSymbols: Seq[Symbol], val classCategoryDisplayName: String)
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

    val classDefns: Seq[Defn.Class] = doc.tree.filterDescendants[Defn.Class] { t =>
      getParentSymbolsOrSelf(t.symbol).exists { pt =>
        symbolMatcher.matches(pt)
      } && !t.mods.exists(isAbstract)
    }

    val objectDefns: Seq[Defn.Object] = doc.tree.filterDescendants[Defn.Object] { t =>
      getParentSymbolsOrSelf(t.symbol).exists { pt =>
        symbolMatcher.matches(pt)
      } && !t.mods.exists(isAbstract)
    }

    classDefns.appendedAll(objectDefns).pipe(defns => ClassFinder.SearchResult(defns, classCategoryDisplayName, None))
  }

}

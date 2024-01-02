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
import eu.cdevreeze.tryscalafix.internal.QuerySupport.WithQueryMethods
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isAbstract
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.isAnnot
import scalafix.v1._

import scala.meta.Decl
import scala.meta.Defn
import scala.meta.Stat
import scala.util.chaining.scalaUtilChainingOps

/**
 * Finder of definitions of non-abstract types that use any of the annotation types given as search input symbols.
 *
 * @author
 *   Chris de Vreeze
 */
final class AnnotationUserFinder(val searchInputSymbols: Seq[Symbol], val classCategoryDisplayName: String)
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

    val matchingDefns: Seq[Defn with Stat.WithMods] = doc.tree.filterDescendants[Defn with Stat.WithMods] { t =>
      t.mods.exists(mod => isAnnot(mod, symbolMatcher))
    }

    val matchingDecls: Seq[Decl with Stat.WithMods] = doc.tree.filterDescendants[Decl with Stat.WithMods] { t =>
      t.mods.exists(mod => isAnnot(mod, symbolMatcher))
    }

    val classOrObjectDefns: Seq[Defn] =
      matchingDefns
        .appendedAll(matchingDecls)
        .flatMap { t =>
          t.findFirstAncestorOrSelf[Defn with Stat.WithMods]((anc: Defn with Stat.WithMods) =>
            (anc.isInstanceOf[Defn.Class] || anc.isInstanceOf[Defn.Object]) && !anc.mods.exists(isAbstract)
          )
        }
        .distinct

    classOrObjectDefns.pipe(defns => SearchResult(defns, classCategoryDisplayName, None))
  }

}

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

package eu.cdevreeze.tryscalafix.internal.xmlsupport

import javax.xml.XMLConstants
import scala.util.chaining.scalaUtilChainingOps

/**
 * Namespace scope, that is, in-scope namespaces.
 *
 * @author
 *   Chris de Vreeze
 */
final case class Scope(prefixNamespaceMapping: Map[String, String]) {
  require(prefixNamespaceMapping.values.forall(_ != XMLConstants.NULL_NS_URI))
  require(!prefixNamespaceMapping.contains(XMLConstants.XML_NS_PREFIX))

  def effectivePrefixNamespaceMapping: Map[String, String] =
    prefixNamespaceMapping.updated(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI)

  def defaultNamespaceOption: Option[String] = prefixNamespaceMapping.get(XMLConstants.DEFAULT_NS_PREFIX)

  def withoutDefaultNamespace: Scope = Scope(prefixNamespaceMapping.filterNot(_._1 == XMLConstants.DEFAULT_NS_PREFIX))

  def resolve(prefix: String): Option[String] = {
    if (prefix == XMLConstants.DEFAULT_NS_PREFIX) {
      prefixNamespaceMapping.get(prefix)
    } else {
      effectivePrefixNamespaceMapping
        .getOrElse(prefix, sys.error(s"Could not resolve prefix '$prefix'"))
        .pipe(Option(_))
    }
  }

  def resolve(otherScope: Scope): Scope = {
    val prefixes: Seq[String] = this.prefixNamespaceMapping.keySet.union(otherScope.prefixNamespaceMapping.keySet).toSeq
    val newMapping: Map[String, String] = prefixes.map { pref =>
      pref -> otherScope.prefixNamespaceMapping.getOrElse(pref, this.prefixNamespaceMapping(pref))
    }.toMap
    Scope(newMapping)
  }

  def relativize(scope: Scope): Declarations = {
    if (Scope.this == scope) {
      Declarations(Map.empty)
    } else {
      val newlyDeclared: Map[String, String] = scope.prefixNamespaceMapping.filter { case (pref, ns) =>
        assert(ns.nonEmpty)
        Scope.this.prefixNamespaceMapping.getOrElse(pref, "") != ns
      }

      val removed: Set[String] =
        Scope.this.prefixNamespaceMapping.keySet.diff(scope.prefixNamespaceMapping.keySet)
      val undeclarations: Map[String, String] = removed.map(pref => pref -> "").toMap

      assert(newlyDeclared.keySet.intersect(removed).isEmpty)
      val m: Map[String, String] = newlyDeclared ++ undeclarations

      Declarations(m)
    }
  }

}

object Scope {
  val empty: Scope = Scope(Map.empty)
}

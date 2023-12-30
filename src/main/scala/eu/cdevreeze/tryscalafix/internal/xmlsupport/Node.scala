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
import javax.xml.namespace.QName

/**
 * XML tree node, in particular element node or text node.
 *
 * @author
 *   Chris de Vreeze
 */
sealed trait Node

final case class Text(text: String, isCData: Boolean) extends Node

final case class Elem(
    name: QName,
    attributes: Map[QName, String],
    scope: Scope,
    children: Seq[Node]
) extends Node {

  require {
    val prefix: String = Option(name.getPrefix).getOrElse(XMLConstants.DEFAULT_NS_PREFIX)
    val nsOption: Option[String] = Option(name.getNamespaceURI).filterNot(Set(XMLConstants.NULL_NS_URI))
    scope.resolve(prefix) == nsOption
  }

  require {
    val attrScope = scope.withoutDefaultNamespace
    attributes.forall { case (attrName, _) =>
      val prefix: String = Option(attrName.getPrefix).getOrElse(XMLConstants.DEFAULT_NS_PREFIX)
      val nsOption: Option[String] = Option(attrName.getNamespaceURI).filterNot(Set(XMLConstants.NULL_NS_URI))
      attrScope.resolve(prefix) == nsOption
    }
  }

  // Child axis

  def filterChildElems(p: Elem => Boolean): Seq[Elem] = {
    children.collect { case e: Elem if p(e) => e }
  }

  def findAllChildElems(): Seq[Elem] = filterChildElems(_ => true)

  def findFirstChildElem(p: Elem => Boolean): Option[Elem] = {
    children.collectFirst { case e: Elem if p(e) => e }
  }

  // Descendant-or-self axis

  def filterDescendantElemsOrSelf(p: Elem => Boolean): Seq[Elem] = {
    // Recursion
    findAllChildElems()
      .flatMap(_.filterDescendantElemsOrSelf(p))
      .prependedAll(Seq(Elem.this).filter(p))
  }

  def findAllDescendantElemsOrSelf(): Seq[Elem] = filterDescendantElemsOrSelf(_ => true)

  def findFirstDescendantElemOrSelf(p: Elem => Boolean): Option[Elem] = {
    findTopmostElemsOrSelf(p).headOption
  }

  // Descendant axis

  def filterDescendantElems(p: Elem => Boolean): Seq[Elem] = {
    findAllChildElems().flatMap(_.filterDescendantElemsOrSelf(p))
  }

  def findAllDescendantElems(): Seq[Elem] = filterDescendantElems(_ => true)

  def findFirstDescendantElem(p: Elem => Boolean): Option[Elem] = {
    findTopmostElems(p).headOption
  }

  // Like descendant-or-self axis, but only topmost

  def findTopmostElemsOrSelf(p: Elem => Boolean): Seq[Elem] = {
    if (p(Elem.this)) {
      Seq(Elem.this)
    } else {
      // Recursion
      findAllChildElems().flatMap(_.findTopmostElemsOrSelf(p))
    }
  }

  // Like descendant axis, but only topmost

  def findTopmostElems(p: Elem => Boolean): Seq[Elem] = {
    findAllChildElems().flatMap(_.findTopmostElemsOrSelf(p))
  }

}

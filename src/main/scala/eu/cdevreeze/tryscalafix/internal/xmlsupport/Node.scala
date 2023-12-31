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
import scala.util.chaining.scalaUtilChainingOps

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

  // Query API
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

  // Unsafe functional update API.

  /**
   * Returns an unsafe functional update API for this element node. It is unsafe in that it does not protect against any
   * issues with changed Scopes. For example, suddenly introducing a default namespace may affect element names, in
   * particular their namespaces. As another example, Scope changes may require namespace undeclarations from parent to
   * child element, which in XML 1.0 is illegal for non-default namespaces.
   *
   * Obviously it is safest to create and functionally update an element tree using one and the same implicit parent
   * Scope.
   */
  def unsafeUpdateApi: Node.UnsafeElemUpdateApi = Node.UnsafeElemUpdateApi(this)
}

object Node {

  final case class UnsafeElemUpdateApi(elm: Elem) {

    def plusChild(addedChild: Node): Elem = plusChildren(Seq(addedChild))

    def plusChildren(addedChildren: Seq[Node]): Elem = {
      withChildren(elm.children.appendedAll(addedChildren))
    }

    /**
     * Replaces all children by the passed collection of children.
     */
    def withChildren(newChildren: Seq[Node]): Elem = {
      elm.copy(children = newChildren)
    }

    def plusAttribute(attrName: QName, attrValue: String): Elem =
      plusAttributes(Map(attrName -> attrValue))

    def plusAttributes(addedAttributes: Map[QName, String]): Elem = {
      withAttributes(elm.attributes.concat(addedAttributes))
    }

    /**
     * Replaces all attributes by the passed collection of attributes.
     */
    def withAttributes(newAttributes: Map[QName, String]): Elem = {
      elm.copy(attributes = newAttributes)
    }

    def transformChildElems(f: Elem => Elem): Elem = withChildren {
      elm.children.map {
        case che: Elem => f(che)
        case n         => n
      }
    }

    def transformDescendantElemsOrSelf(f: Elem => Elem): Elem = {
      // Recursive
      transformChildElems(_.unsafeUpdateApi.transformDescendantElemsOrSelf(f))
        .pipe(f)
    }

    def transformDescendantElems(f: Elem => Elem): Elem = {
      transformChildElems(_.unsafeUpdateApi.transformDescendantElemsOrSelf(f))
    }

  }

  def elemName(prefix: String, localName: String)(implicit scope: Scope): QName = {
    val nsOption: Option[String] = scope.resolve(prefix)
    nsOption
      .map(ns => new QName(ns, localName, prefix))
      .getOrElse {
        if (prefix == XMLConstants.DEFAULT_NS_PREFIX) {
          new QName(localName)
        } else {
          sys.error(s"Could not resolve name with prefix '$prefix' and local name '$localName'")
        }
      }
  }

  def elemName(localName: String)(implicit scope: Scope): QName = {
    elemName(XMLConstants.DEFAULT_NS_PREFIX, localName)(scope)
  }

  def attrName(prefix: String, localName: String)(implicit scope: Scope): QName = {
    elemName(prefix, localName)(scope.withoutDefaultNamespace)
  }

  def attrName(localName: String)(implicit scope: Scope): QName = {
    attrName(XMLConstants.DEFAULT_NS_PREFIX, localName)(scope)
  }

  def text(t: String): Text = Text(t, isCData = false)

  def cdataText(t: String): Text = Text(t, isCData = true)

  def elem(name: QName, attrs: Map[QName, String], children: Seq[Node])(implicit parentScope: Scope): Elem =
    Elem(
      name = name,
      attributes = attrs,
      scope = parentScope,
      children = children
    )

  def elem(name: QName, children: Seq[Node])(implicit parentScope: Scope): Elem =
    elem(name, Map.empty, children)(parentScope)

  def textElem(name: QName, attrs: Map[QName, String], text: Text)(implicit parentScope: Scope): Elem =
    elem(name, attrs, Seq(text))(parentScope)

  def textElem(name: QName, text: Text)(implicit parentScope: Scope): Elem =
    textElem(name, Map.empty, text)(parentScope)

}

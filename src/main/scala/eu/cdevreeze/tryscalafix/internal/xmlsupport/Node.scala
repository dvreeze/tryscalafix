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
 * This API: <ul> <li>Offers thread-safe immutable XML DOM tree nodes, in particular element nodes</li> <li>Is
 * element-centric in its query and functional update API</li> <li>Makes element tree creation and functional updates
 * rather easy</li> <li>Offers good support for XML namespaces</li> <li>Does not try to follow XML standards where
 * that's neither practical nor needed</li> <li>For example, inter-element whitespace is recognized "syntactically"
 * rather than looking at DTDs</li> <li>As another example, namespace declaration "attributes" are not considered
 * attributes in this API</li> </ul>
 *
 * @author
 *   Chris de Vreeze
 */
sealed trait Node

final case class Text(text: String, isCData: Boolean) extends Node

/**
 * Comment node (but not top-level as document element sibling, since document nodes are not modelled).
 */
final case class Comment(text: String) extends Node

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

  // Specific queries

  /**
   * Returns the concatenation of the texts of text children, including whitespace and CData. Non-text children are
   * ignored. If there are no text children, the empty string is returned.
   */
  def text: String = {
    val textStrings: Seq[String] = children.collect { case Text(t, _) => t }
    textStrings.mkString
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

  // Specific transformations

  /**
   * Returns a copy where inter-element whitespace has been removed, throughout the node tree.
   *
   * That is, for each descendant-or-self element determines if it has at least one child element and no non-whitespace
   * text child nodes, and if so, removes all (whitespace) text children.
   *
   * This method is useful if it is known that whitespace around element nodes is used for formatting purposes, and (in
   * the absence of an XML Schema or DTD) can therefore be treated as "ignorable whitespace". In the case of "mixed
   * content" (if text around element nodes is not all whitespace), this method will not remove any text children of the
   * parent element.
   *
   * XML space attributes (xml:space) are not respected by this method. If such whitespace preservation functionality is
   * needed, it can be written as a transformation where for specific elements this method is not called.
   */
  def removeAllInterElementWhitespace(): Elem = {
    def isWhitespaceText(n: Node): Boolean = n match {
      case t: Text if t.text.trim.isEmpty => true
      case _                              => false
    }

    def isTextNode(n: Node): Boolean = n match {
      case Text(_, _) => true
      case _          => false
    }

    def doStripWhitespace(e: Elem): Boolean =
      e.findFirstChildElem(_ => true).nonEmpty &&
        e.children.forall(n => isWhitespaceText(n) || !isTextNode(n))

    // Safe element transformation w.r.t. namespaces.
    this.unsafeUpdateApi.transformDescendantElemsOrSelf { e =>
      val childNodes: Seq[Node] = if (doStripWhitespace(e)) e.children.filterNot(isTextNode) else e.children
      e.unsafeUpdateApi.withChildren(childNodes)
    }
  }

  /**
   * Enhances the Scope with the given parameter Scope, but ignoring its default namespace, if any. Descendant elements
   * are updated w.r.t. their Scope in the same way.
   *
   * This method is meant to prevent namespace undeclarations for non-empty prefixes, which is illegal in XML 1.0. This
   * method is safe w.r.t. namespaces, but potentially expensive (depending on the size of the element tree).
   */
  def safelyUsingParentScope(otherScope: Scope): Elem = {
    val extraScope: Scope = otherScope.withoutDefaultNamespace

    this.unsafeUpdateApi.transformDescendantElemsOrSelf { e =>
      e.unsafeUpdateApi.withScope(extraScope.resolve(e.scope))
    }
  }

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

    /**
     * Replaces the scope by the given Scope. Very unsafe method. Prefer method deeplyAddingMissingPrefixesFrom instead.
     */
    def withScope(newScope: Scope): Elem = {
      elm.copy(scope = newScope)
    }

    // Functional transformation methods. They work in a bottom-up fashion. If instead it is needed
    // to transform element trees in a top-down fashion, consider recursion instead (where a parent
    // Scope is typically added as recursive function parameter).

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

    def transformChildElemsToNodeSeq(f: Elem => Seq[Node]): Elem = withChildren {
      elm.children.flatMap {
        case che: Elem => f(che)
        case n         => Seq(n)
      }
    }

    def transformDescendantElemsOrSelfToNodeSeq(f: Elem => Seq[Node]): Seq[Node] = {
      // Recursive
      transformChildElemsToNodeSeq(_.unsafeUpdateApi.transformDescendantElemsOrSelfToNodeSeq(f))
        .pipe(f)
    }

    def transformDescendantElemsToNodeSeq(f: Elem => Seq[Node]): Elem = {
      transformChildElemsToNodeSeq(_.unsafeUpdateApi.transformDescendantElemsOrSelfToNodeSeq(f))
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

  def comment(t: String): Comment = Comment(t)

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

  def emptyElem(name: QName, attrs: Map[QName, String])(implicit parentScope: Scope): Elem =
    elem(name, attrs, Seq.empty)(parentScope)

  def emptyElem(name: QName)(implicit parentScope: Scope): Elem =
    emptyElem(name, Map.empty)(parentScope)

}

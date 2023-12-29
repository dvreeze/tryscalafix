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

package eu.cdevreeze.tryscalafix.internal

import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.AttributesImpl

import javax.xml.XMLConstants
import javax.xml.namespace.QName
import javax.xml.transform.OutputKeys
import javax.xml.transform.Result
import javax.xml.transform.sax.SAXTransformerFactory
import scala.util.chaining.scalaUtilChainingOps

/**
 * Very basic support for XML (with namespaces). This supports contains nested construction of XML, basic
 * namespace-aware querying, and (indirectly) serialization.
 *
 * @author
 *   Chris de Vreeze
 */
object XmlSupport {

  final case class Scope(prefixNamespaceMapping: Map[String, String]) {
    require(prefixNamespaceMapping.values.forall(_ != XMLConstants.NULL_NS_URI))

    def defaultNamespaceOption: Option[String] = prefixNamespaceMapping.get(XMLConstants.DEFAULT_NS_PREFIX)

    def withoutDefaultNamespace: Scope = Scope(prefixNamespaceMapping.filterNot(_._1 == XMLConstants.DEFAULT_NS_PREFIX))

    def resolve(prefix: String): Option[String] = {
      if (prefix == XMLConstants.DEFAULT_NS_PREFIX) {
        prefixNamespaceMapping.get(prefix)
      } else {
        prefixNamespaceMapping
          .getOrElse(prefix, sys.error(s"Could not resolve prefix '$prefix'"))
          .pipe(Option(_))
      }
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

  /**
   * Namespace declarations. For namespace undeclarations, the mapped value is the empty string.
   */
  final case class Declarations(prefixNamespaceMapping: Map[String, String])

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

  final class ConverterToSax(val handler: ContentHandler) {

    def convertDocumentElem(docElem: Elem): Unit = {
      handler.startDocument()
      convertElem(docElem, Scope.empty)
      handler.endDocument()
    }

    def convertNode(node: Node, parentScope: Scope): Unit = {
      node match {
        case e: Elem => convertElem(e, parentScope)
        case t: Text => convertText(t)
        case _       => ()
      }
    }

    def convertElem(elm: Elem, parentScope: Scope): Unit = {
      // Not tail-recursive, but the recursion depth should be limited

      val namespaces: Declarations = parentScope.relativize(elm.scope)
      val namespacesMap = namespaces.prefixNamespaceMapping

      for ((prefix, nsUri) <- namespacesMap) handler.startPrefixMapping(prefix, nsUri)

      generateStartElementEvent(elm)

      // Recursive calls. Not tail-recursive, but recursion depth should be limited.

      for (node <- elm.children) {
        convertNode(node, elm.scope)
      }

      generateEndElementEvent(elm)

      for ((prefix, _) <- namespacesMap) handler.endPrefixMapping(prefix)
    }

    def convertText(t: Text): Unit = {
      handler match {
        case handler: ContentHandler with LexicalHandler =>
          if (t.isCData) handler.startCDATA()
          handler.characters(t.text.toCharArray, 0, t.text.length)
          if (t.isCData) handler.endCDATA()
        case _ =>
          handler.characters(t.text.toCharArray, 0, t.text.length)
      }
    }

    private def generateStartElementEvent(elm: Elem): Unit = {
      val uri = elm.name.getNamespaceURI

      val attrs: Attributes = getAttributes(elm)

      handler.startElement(uri, elm.name.getLocalPart, syntacticQName(elm.name), attrs)
    }

    private def generateEndElementEvent(elm: Elem): Unit = {
      val uri = elm.name.getNamespaceURI

      handler.endElement(uri, elm.name.getLocalPart, syntacticQName(elm.name))
    }

    private def getAttributes(elm: Elem): Attributes = {
      val attrs = new AttributesImpl

      addNormalAttributes(elm, attrs)
      attrs
    }

    /**
     * Gets the normal (non-namespace-declaration) attributes, and adds them to the passed Attributes object. This
     * method is called internally, providing the attributes that are passed to the startElement call.
     */
    private def addNormalAttributes(elm: Elem, attrs: AttributesImpl): Attributes = {
      for ((attrQName, attrValue) <- elm.attributes) {
        val uri = attrQName.getNamespaceURI
        val tpe = "CDATA"

        attrs.addAttribute(uri, attrQName.getLocalPart, syntacticQName(attrQName), tpe, attrValue)
      }

      attrs
    }

    private def syntacticQName(name: QName): String = {
      if (Option(name.getPrefix).forall(_ == XMLConstants.DEFAULT_NS_PREFIX)) {
        name.getLocalPart
      } else {
        s"${name.getPrefix}:${name.getLocalPart}"
      }
    }

  }

  def print(elem: Elem, result: Result)(implicit tf: SAXTransformerFactory): Unit = {
    val handler: ContentHandler = tf
      .newTransformerHandler()
      .tap(_.getTransformer.setOutputProperty(OutputKeys.INDENT, "yes"))
      .tap(_.setResult(result))
    val converterToSax = new ConverterToSax(handler)
    converterToSax.convertDocumentElem(elem)
  }

}

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

import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler

import javax.xml.XMLConstants
import javax.xml.namespace.QName

/**
 * Converter of XML nodes to SAX events.
 *
 * @author
 *   Chris de Vreeze
 */
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
   * Gets the normal (non-namespace-declaration) attributes, and adds them to the passed Attributes object. This method
   * is called internally, providing the attributes that are passed to the startElement call.
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

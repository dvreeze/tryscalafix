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

package eu.cdevreeze.tryscalafix.internal.xmlsupport.parse

import eu.cdevreeze.tryscalafix.internal.xmlsupport.{Declarations, Elem, Node, Scope, Text}
import ElemProducingSaxHandler.DefaultHandler.InternalElemNode
import ElemProducingSaxHandler.DefaultHandler.InternalTextNode
import org.xml.sax.Attributes
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.helpers.NamespaceSupport

import javax.xml.XMLConstants
import javax.xml.namespace.QName
import scala.jdk.CollectionConverters.EnumerationHasAsScala

/**
 * Elem producing SAX handler that, once ready, can be queried for the resulting document element.
 *
 * @author
 *   Chris de Vreeze
 */
trait ElemProducingSaxHandler extends DefaultHandler {

  /**
   * The resulting document element. Only call this method once the handler is ready processing.
   */
  def resultingElem: Elem
}

object ElemProducingSaxHandler {

  class DefaultHandler(ignoreWhitespace: Boolean = false) extends ElemProducingSaxHandler with LexicalHandler {

    private var currentRoot: InternalElemNode = _

    private var currentElem: InternalElemNode = _

    private var currentlyInCData: Boolean = false

    private val namespaceSupport = new NamespaceSupport()

    private var namespaceContextStarted: Boolean = false

    override def startElement(uri: String, localName: String, qName: String, atts: Attributes): Unit = {
      pushContextIfNeeded()
      namespaceContextStarted = false

      val parentScope: Scope = Option(currentElem).map(_.scope).getOrElse(Scope.empty)
      val elm: InternalElemNode = startElementToInternalElemNode(uri, localName, qName, atts, parentScope)

      if (currentRoot eq null) {
        require(currentElem eq null)

        currentRoot = elm
        currentElem = currentRoot
      } else {
        require(currentElem ne null)

        currentElem.addChild(elm)
        elm.parentOption = Some(currentElem)
        currentElem = elm
      }
    }

    override def endElement(uri: String, localName: String, qName: String): Unit = {
      require(!namespaceContextStarted, s"Corrupt internal namespace state!")
      namespaceSupport.popContext()

      require(currentRoot ne null)
      require(currentElem ne null)

      currentElem = currentElem.parentOption.orNull
    }

    override def characters(ch: Array[Char], start: Int, length: Int): Unit = {
      val isCData = this.currentlyInCData
      val txt: InternalTextNode = InternalTextNode(new String(ch, start, length), isCData)

      if (currentRoot eq null) {
        // Ignore
        require(currentElem eq null)
      } else {
        require(currentElem ne null)

        currentElem.addChild(txt)
      }
    }

    override def ignorableWhitespace(ch: Array[Char], start: Int, length: Int): Unit = {
      if (!ignoreWhitespace) {
        // Self call. If ignorable whitespace makes it until here, we store it in the result tree.
        characters(ch, start, length)
      }
    }

    // ContentHandler methods skippedEntity, setDocumentLocator not overridden

    override def startPrefixMapping(prefix: String, uri: String): Unit = {
      pushContextIfNeeded()

      namespaceSupport.declarePrefix(prefix, uri)
    }

    override def endPrefixMapping(prefix: String): Unit = {}

    override def startCDATA(): Unit = {
      this.currentlyInCData = true
    }

    override def endCDATA(): Unit = {
      this.currentlyInCData = false
    }

    override def startDTD(name: String, publicId: String, systemId: String): Unit = {}

    override def endDTD(): Unit = {}

    override def startEntity(name: String): Unit = {}

    override def endEntity(name: String): Unit = {}

    override def comment(ch: Array[Char], start: Int, length: Int): Unit = {}

    override def resultingElem: Elem = {
      require(currentRoot ne null, "When parsing is ready, the current root must not be null")
      require(currentElem eq null, "When parsing is ready, the current path must be at the root")

      currentRoot.toNode
    }

    // Private methods

    private def pushContextIfNeeded(): Unit = {
      if (!namespaceContextStarted) {
        namespaceSupport.pushContext()
        namespaceContextStarted = true
      }
    }

    private def startElementToInternalElemNode(
        uri: String,
        localName: String,
        qName: String,
        atts: Attributes,
        parentScope: Scope
    ): InternalElemNode = {

      require(uri ne null)
      require(localName ne null)
      require(qName ne null)

      val nsDecls = extractDeclarations(namespaceSupport)
      val newScope = parentScope.resolve(nsDecls)
      val attrSeq = extractAttributeMap(atts, newScope).toMap

      val elmName: QName =
        if (qName.nonEmpty) parseQName(qName, newScope) else new QName(localName)

      new InternalElemNode(
        parentOption = None,
        name = elmName,
        attributes = attrSeq,
        scope = newScope,
        children = IndexedSeq()
      )
    }

    private def extractDeclarations(nsSupport: NamespaceSupport): Declarations = {
      val prefixIterator: Iterator[String] = nsSupport.getDeclaredPrefixes.asScala

      val prefUriMap =
        prefixIterator.map { pref =>
          val uri = nsSupport.getURI(pref)
          (pref, Option(uri).getOrElse(""))
        }.toMap

      Declarations(prefUriMap)
    }

    private def extractAttributeMap(atts: Attributes, scope: Scope): IndexedSeq[(QName, String)] = {
      val result = attributeOrDeclarationSeq(atts).collect {
        case (qn, v) if !isNamespaceDeclaration(qn) =>
          val qname = parseQName(qn, scope)
          val attValue = v
          qname -> attValue
      }
      result
    }

    private def attributeOrDeclarationSeq(atts: Attributes): IndexedSeq[(String, String)] = {
      val result = (0 until atts.getLength).map { (idx: Int) => atts.getQName(idx) -> atts.getValue(idx) }
      result
    }

    /** Returns true if the attribute qualified (prefixed) name is a namespace declaration */
    private def isNamespaceDeclaration(attrQName: String): Boolean = {
      val arr = attrQName.split(':')
      require(arr.length >= 1 && arr.length <= 2)
      val result = arr(0) == "xmlns"
      result
    }

    private def parseQName(syntacticQName: String, scope: Scope): QName = {
      val arr: Array[String] = syntacticQName.split(':').ensuring(_.lengthIs <= 2).ensuring(_.lengthIs >= 1)
      val prefix: String = if (arr.lengthIs == 1) "" else arr(0)
      val localName: String = arr.last
      val nsOption: Option[String] = scope.resolve(prefix)
      new QName(nsOption.getOrElse(XMLConstants.NULL_NS_URI), localName, prefix)
    }

  }

  object DefaultHandler {

    private sealed trait InternalNode {
      type NodeType <: Node

      def toNode: NodeType
    }

    private class InternalElemNode(
        var parentOption: Option[InternalElemNode],
        val name: QName,
        val attributes: Map[QName, String],
        val scope: Scope,
        var children: Seq[InternalNode] = Seq.empty
    ) extends InternalNode {
      type NodeType = Elem

      def toNode: Elem = {
        // Recursive, but not tail-recursive
        val childNodes: Seq[Node] = children.map(_.toNode).toIndexedSeq

        Elem(
          name = this.name,
          attributes = this.attributes,
          scope = this.scope,
          children = childNodes
        )
      }

      def addChild(child: InternalNode): Unit = {
        this.children = this.children.appended(child)
      }

    }

    private case class InternalTextNode(text: String, isCData: Boolean) extends InternalNode {
      type NodeType = Text

      def toNode: Text = Text(text = text, isCData = isCData)
    }

  }

}

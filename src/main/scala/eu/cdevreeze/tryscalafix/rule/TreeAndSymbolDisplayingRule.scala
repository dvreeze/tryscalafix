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

package eu.cdevreeze.tryscalafix.rule

import eu.cdevreeze.tryscalafix.analyser.TreeAndSymbolDisplayer
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Elem
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Node
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Scope
import eu.cdevreeze.tryscalafix.internal.xmlsupport.XmlPrinter
import scalafix.patch.Patch
import scalafix.v1.SemanticDocument
import scalafix.v1.SemanticRule

import java.util.concurrent.atomic.AtomicReference
import javax.xml.namespace.QName
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.stream.StreamResult

/**
 * SemanticRule that shows Scalameta trees and symbols, by invoking TreeAndSymbolDisplayer.
 *
 * @author
 *   Chris de Vreeze
 */
final class TreeAndSymbolDisplayingRule() extends SemanticRule("TreeAndSymbolDisplayingRule") {

  private implicit val parentScope: Scope = Scope.empty

  private final val accumulatedElem = new AtomicReference(
    Node.elem(
      name = Node.elemName("DummyRoot"),
      children = Seq.empty
    )
  )

  override def beforeStart(): Unit = {
    super.beforeStart()

    this.accumulatedElem.set {
      Node.elem(
        name = Node.elemName("TreeAndSymbolDisplayer"),
        children = Seq.empty
      )
    }
  }

  override def afterComplete(): Unit = {
    // When using method newDefaultInstance, CDATA is emitted when the text node says so
    implicit val tf: SAXTransformerFactory =
      TransformerFactory.newDefaultInstance().asInstanceOf[SAXTransformerFactory]
    XmlPrinter(tf).print(accumulatedElem.get(), new StreamResult(System.out))

    super.afterComplete()
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val analyser = new TreeAndSymbolDisplayer()

    val elem: Elem = analyser(doc)
      .findFirstDescendantElemOrSelf(_.name == new QName("sourceDocument"))
      .getOrElse(sys.error(s"Could not find 'sourceDocument' element"))

    accumulatedElem.getAndUpdate { e =>
      e.copy(children = e.children.appended(elem))
    }

    Patch.empty
  }

}

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

package eu.cdevreeze.tryscalafix.internal.xmlsupport.print

import eu.cdevreeze.tryscalafix.internal.xmlsupport.Elem
import org.xml.sax.ContentHandler

import javax.xml.transform.{OutputKeys, Result, TransformerFactory}
import javax.xml.transform.sax.SAXTransformerFactory
import scala.util.chaining.scalaUtilChainingOps

/**
 * XML node printer, using ConverterToSax.
 *
 * @author
 *   Chris de Vreeze
 */
final class XmlPrinter(val tf: SAXTransformerFactory) {

  def print(elem: Elem, result: Result): Unit = {
    val handler: ContentHandler = tf
      .newTransformerHandler()
      .tap(_.getTransformer.setOutputProperty(OutputKeys.INDENT, "yes"))
      .tap(_.setResult(result))
    val converterToSax = new ConverterToSax(handler)
    converterToSax.convertDocumentElem(elem)
  }

}

object XmlPrinter {

  def apply(tf: SAXTransformerFactory): XmlPrinter = new XmlPrinter(tf)

  def newDefaultInstance(): XmlPrinter = apply {
    // When using method newDefaultInstance, CDATA is emitted when the text node says so
    TransformerFactory.newDefaultInstance().asInstanceOf[SAXTransformerFactory]
  }

}

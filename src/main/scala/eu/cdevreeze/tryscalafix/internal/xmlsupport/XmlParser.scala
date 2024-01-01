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

import org.xml.sax.InputSource
import org.xml.sax.ext.LexicalHandler

import java.io.{File, FileInputStream, InputStream}
import java.net.URI
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import scala.util.control.Exception.ignoring

/**
 * XML node parser, using ElemProducingSaxHandler.
 *
 * The implementation has mostly been taken from the yaidom XML library.
 *
 * @author
 *   Chris de Vreeze
 */
final class XmlParser(
    val parserFactory: SAXParserFactory,
    val parserCreator: SAXParserFactory => SAXParser,
    val handlerCreator: () => ElemProducingSaxHandler
) {

  /** Parses the content of the given SAX input source into an Elem. */
  def parse(inputSource: InputSource): Elem = {
    try {
      val sp: SAXParser = parserCreator(parserFactory)
      val handler = handlerCreator()

      if (handler.isInstanceOf[LexicalHandler]) {
        // Property "http://xml.org/sax/properties/lexical-handler" registers a LexicalHandler. See the corresponding API documentation.
        // It is assumed here that in practice all SAX parsers support LexicalHandlers.
        sp.getXMLReader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
      }

      sp.parse(inputSource, handler)

      handler.resultingElem
    } finally {
      ignoring(classOf[Exception]) {
        Option(inputSource.getByteStream).foreach(bs => bs.close())
      }
      ignoring(classOf[Exception]) {
        Option(inputSource.getCharacterStream).foreach(cs => cs.close())
      }
    }
  }

  /** Parses the content of the given input stream into an Elem. */
  def parse(inputStream: InputStream): Elem = {
    // See http://docs.oracle.com/cd/E13222_01/wls/docs90/xml/best.html
    val inputSource = new InputSource(inputStream)
    parse(inputSource)
  }

  /** Parses the content of the given URI into an Elem. */
  def parse(uri: URI): Elem = parse(uri.toURL.openStream())

  /** Parses the content of the given File into an Elem. */
  def parse(file: File): Elem = parse(new FileInputStream(file))

}

object XmlParser {

  /**
   * Returns a new instance. Same as:
   * {{{
   * newInstance(SAXParserFactory.newInstance.makeNamespaceAndPrefixAware)
   * }}}
   * Calling the `setNamespaceAware` method instead does not suffice, and is not needed. See
   * http://www.cafeconleche.org/slides/xmlone/london2002/namespaces/36.html.
   */
  def newInstance(): XmlParser = {
    val spf = SAXParserFactory.newInstance.makeNamespaceAndPrefixAware
    newInstance(spf)
  }

  /**
   * Returns `newInstance(parserFactory, new DefaultElemProducingSaxHandler {})`.
   *
   * Do not forget to configure namespace handling, by calling `makeNamespaceAndPrefixAware` on the `SAXParserFactory`.
   */
  def newInstance(parserFactory: SAXParserFactory): XmlParser = {
    newInstance(parserFactory, () => new ElemProducingSaxHandler.DefaultHandler())
  }

  /**
   * Invokes the 3-arg `newInstance` method on `parserFactory`, a `SAXParserFactory => SAXParser` "SAX parser creator",
   * and `handlerCreator`. The "SAX parser creator" invokes `parserFactory.newSAXParser()`.
   *
   * Do not forget to configure namespace handling, by calling `makeNamespaceAndPrefixAware` on the `SAXParserFactory`.
   */
  def newInstance(parserFactory: SAXParserFactory, handlerCreator: () => ElemProducingSaxHandler): XmlParser = {
    newInstance(
      parserFactory = parserFactory,
      parserCreator = { (spf: SAXParserFactory) =>
        val parser = spf.newSAXParser()
        parser
      },
      handlerCreator = handlerCreator
    )
  }

  /**
   * Returns a new instance, by invoking the primary constructor.
   *
   * Do not forget to configure namespace handling, by calling `makeNamespaceAndPrefixAware` on the `SAXParserFactory`.
   */
  def newInstance(
      parserFactory: SAXParserFactory,
      parserCreator: SAXParserFactory => SAXParser,
      handlerCreator: () => ElemProducingSaxHandler
  ): XmlParser = {
    new XmlParser(parserFactory, parserCreator, handlerCreator)
  }

  /**
   * Enriches a `SAXParserFactory` with method `makeNamespaceAndPrefixAware`.
   */
  final implicit class RichSAXParserFactory(val saxParserFactory: SAXParserFactory) extends AnyVal {

    /**
     * Returns the SAXParserFactory, after configuring it as follows:
     * {{{
     * setFeature("http://xml.org/sax/features/namespaces", true)
     * setFeature("http://xml.org/sax/features/namespace-prefixes", true)
     * }}}
     */
    def makeNamespaceAndPrefixAware: SAXParserFactory = {
      saxParserFactory.setFeature("http://xml.org/sax/features/namespaces", true)
      saxParserFactory.setFeature("http://xml.org/sax/features/namespace-prefixes", true)
      saxParserFactory
    }

  }

}

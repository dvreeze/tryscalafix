package eu.cdevreeze.tryscalafix.internal

import eu.cdevreeze.tryscalafix.internal.XmlSupport.Elem
import eu.cdevreeze.tryscalafix.internal.XmlSupport.Scope
import eu.cdevreeze.tryscalafix.internal.XmlSupport.Text
import org.scalatest.flatspec.AnyFlatSpec

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.xml.namespace.QName
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.stream.StreamResult

class XmlSupportSpec extends AnyFlatSpec {

  private val elem1: Elem =
    Elem(
      name = new QName("root"),
      attributes = Map.empty,
      scope = Scope.empty,
      children = Seq(
        Elem(
          name = new QName("child"),
          attributes = Map.empty,
          scope = Scope.empty,
          children = Seq(Text("child 1", isCData = false))
        ),
        Elem(
          name = new QName("child"),
          attributes = Map.empty,
          scope = Scope.empty,
          children = Seq(Text("child 2", isCData = false))
        ),
        Elem(
          name = new QName("child"),
          attributes = Map.empty,
          scope = Scope.empty,
          children = Seq(
            Elem(
              name = new QName("grandchild"),
              attributes = Map.empty,
              scope = Scope.empty,
              children = Seq(Text("grandchild", isCData = false))
            )
          )
        ),
        Elem(
          name = new QName("child"),
          attributes = Map.empty,
          scope = Scope.empty,
          children = Seq(Text("child 4", isCData = true))
        )
      )
    )

  behavior.of("querying of XML")

  it should "find all element nodes" in {
    val allElemsOrSelf: Seq[Elem] = elem1.findAllDescendantElemsOrSelf()
    assert(allElemsOrSelf.map(_.name.getLocalPart) === Seq("root", "child", "child", "child", "grandchild", "child"))
  }

  it should "find all non-whitespace text nodes" in {
    val allElemsOrSelf: Seq[Elem] = elem1.findAllDescendantElemsOrSelf()
    val allElemTexts: Seq[String] =
      allElemsOrSelf.flatMap(_.children.collect { case t: Text if t.text.trim.nonEmpty => t.text })
    assert(allElemTexts === Seq("child 1", "child 2", "grandchild", "child 4"))
  }

  it should "find all element nodes with a given name" in {
    val elems: Seq[Elem] = elem1.filterDescendantElemsOrSelf(_.name.getLocalPart == "child")
    assert(elems.size === 4)
  }

  it should "find the topmost descendant element nodes with a given name, if any" in {
    val elems: Seq[Elem] = elem1.findTopmostElemsOrSelf(_.name.getLocalPart == "child")
    assert(elems.size === 4)
  }

  it should "find the first element node with a given name, if any" in {
    val elemOption: Option[Elem] = elem1.findFirstDescendantElem(_.name.getLocalPart == "grandchild")
    assert(elemOption.nonEmpty === true)
  }

  it should "find no first element node with a given name, if there is not any" in {
    val elemOption: Option[Elem] = elem1.findFirstDescendantElem(_.name.getLocalPart == "grand-grandchild")
    assert(elemOption.isEmpty === true)
  }

  behavior.of("serialisation of XML")

  it should "serialize XML correctly" in {
    val bos = new ByteArrayOutputStream()
    val result = new StreamResult(bos)
    // When using method newDefaultInstance, CDATA is emitted when the text node says so
    implicit val tf: SAXTransformerFactory =
      TransformerFactory.newDefaultInstance().asInstanceOf[SAXTransformerFactory]
    XmlSupport.print(elem1, result)
    val xmlString = bos.toString(StandardCharsets.UTF_8)

    println(xmlString)

    assert(
      xmlString.contains("<grandchild>grandchild</grandchild>") &&
        xmlString.contains("<![CDATA[child 4]]>") === true
    )
  }

}

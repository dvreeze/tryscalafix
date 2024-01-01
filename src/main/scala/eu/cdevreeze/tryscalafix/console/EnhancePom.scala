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

package eu.cdevreeze.tryscalafix.console

import eu.cdevreeze.tryscalafix.internal.xmlsupport.Elem
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Node
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Scope
import eu.cdevreeze.tryscalafix.internal.xmlsupport.XmlParser
import eu.cdevreeze.tryscalafix.internal.xmlsupport.XmlPrinter

import java.io.File
import java.nio.file.Path
import javax.xml.namespace.QName
import javax.xml.transform.stream.StreamResult
import scala.util.chaining.scalaUtilChainingOps

/**
 * Enhances an input POM file with Scalafix rules from this project.
 *
 * @author
 *   Chris de Vreeze
 */
object EnhancePom {

  private val mvnNs: String = "http://maven.apache.org/POM/4.0.0"
  private val xsiNs = "http://www.w3.org/2001/XMLSchema-instance"
  private val defaultScope: Scope = Scope(Map("" -> mvnNs, "xsi" -> xsiNs))

  def main(args: Array[String]): Unit = {
    require(args.lengthIs == 1, s"Usage: EnhancePom <POM file path>")
    val pomFile = Path.of(args(0))

    val xmlParser: XmlParser = XmlParser.newInstance()

    val pomDocElem: Elem = xmlParser
      .parse(pomFile.toFile)
      .removeAllInterElementWhitespace()
    require(pomDocElem.scope.defaultNamespaceOption.nonEmpty, s"Expected default namespace")
    require(
      pomDocElem.scope.defaultNamespaceOption.get == mvnNs,
      s"Expected default namespace '$mvnNs'"
    )
    require(pomDocElem.name == new QName(mvnNs, "project"), s"Expected root element '{$mvnNs}project'")

    val templatePomFile: Path =
      classOf[Node].getResource("/maven-pom-scalafix-sample.xml").toURI.pipe(new File(_)).toPath
    require(templatePomFile.toFile.isFile, s"Not an existing file: '$templatePomFile'")
    val templatePom: Elem = xmlParser.parse(templatePomFile.toFile).removeAllInterElementWhitespace()
    assert(templatePom.scope == defaultScope)
    assert(templatePom.findAllDescendantElemsOrSelf().forall(_.scope == defaultScope))

    val templateSemanticdbProfileElem: Elem =
      findSemanticdbProfileElem(templatePom)
        .ensuring(_.nonEmpty, s"Missing semanticdb profile in template POM file ('programming' error)")
        .get

    val enhancedPomElem: Elem =
      pomDocElem
        .pipe { docElem =>
          // Removing semanticdb profile element, if any
          docElem.unsafeUpdateApi.transformDescendantElemsToNodeSeq { e =>
            if (isSemanticdbProfileElem(e)) Seq.empty else Seq(e)
          }
        }
        .pipe { docElem =>
          // Adding profiles element, if absent
          if (docElem.findFirstChildElem(isProfilesElem).isEmpty) {
            implicit val scope: Scope = defaultScope

            docElem.unsafeUpdateApi.plusChild {
              Node
                .emptyElem(Node.elemName("profiles"))
                .unsafeUpdateApi
                .deeplyAddingMissingPrefixesFrom(docElem.scope)
            }
          } else {
            docElem
          }
        }
        .pipe { docElem =>
          // Adding semanticdb profile element from template
          implicit val scope: Scope = defaultScope

          docElem.unsafeUpdateApi.transformDescendantElems { e =>
            if (e.name == new QName(mvnNs, "profiles")) {
              e.unsafeUpdateApi
                .deeplyAddingMissingPrefixesFrom(templateSemanticdbProfileElem.scope)
                .unsafeUpdateApi
                .plusChild(templateSemanticdbProfileElem)
            } else {
              e
            }
          }
        }

    XmlPrinter.newDefaultInstance().print(enhancedPomElem, new StreamResult(System.out))
  }

  private def isProfilesElem(elm: Elem): Boolean = {
    elm.name == new QName(mvnNs, "profiles")
  }

  private def isSemanticdbProfileElem(elm: Elem): Boolean = {
    elm.name == new QName(mvnNs, "profile") &&
    elm.findFirstChildElem { idElem =>
      idElem.name == new QName(mvnNs, "id") && idElem.text.trim == "semanticdb"
    }.nonEmpty
  }

  private def findSemanticdbProfileElem(docElem: Elem): Option[Elem] = {
    val semanticdbProfileElems: Seq[Elem] =
      for {
        profilesElem <- docElem.filterChildElems(_.name == new QName(mvnNs, "profiles"))
        profileElem <- profilesElem.filterChildElems(isSemanticdbProfileElem)
      } yield profileElem

    semanticdbProfileElems.headOption
  }

}

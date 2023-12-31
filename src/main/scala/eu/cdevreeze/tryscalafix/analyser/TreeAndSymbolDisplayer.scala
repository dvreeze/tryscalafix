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

package eu.cdevreeze.tryscalafix.analyser

import eu.cdevreeze.tryscalafix.SemanticDocumentAnalyser
import eu.cdevreeze.tryscalafix.analyser.TreeAndSymbolDisplayer.SymbolDisplayInfo
import eu.cdevreeze.tryscalafix.internal.QuerySupport.WithQueryMethods
import eu.cdevreeze.tryscalafix.internal.SymbolQuerySupport.getParentSymbolsOrSelf
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Elem
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Node
import eu.cdevreeze.tryscalafix.internal.xmlsupport.Scope
import scalafix.XtensionScalafixProductInspect
import scalafix.v1._

import java.nio.file.Path
import java.nio.file.Paths
import scala.meta.Tree
import scala.meta.inputs.Input
import scala.util.chaining.scalaUtilChainingOps

/**
 * SemanticDocumentAnalyser that shows Scalameta trees and symbols.
 *
 * @author
 *   Chris de Vreeze
 */
final class TreeAndSymbolDisplayer() extends SemanticDocumentAnalyser[Elem] {

  override def apply(doc: SemanticDocument): Elem = {
    implicit val implicitDoc: SemanticDocument = doc

    val fileName: Path = doc.input.asInstanceOf[Input.VirtualFile].path.pipe(Paths.get(_)).getFileName

    val treeStructure: String = doc.tree.structureLabeled(80)

    val trees: Seq[Tree] = doc.tree.findAllDescendantsOrSelf[Tree]()

    val symbolInfo: Seq[SymbolDisplayInfo] =
      trees
        .map { tree =>
          val symbol: Symbol = tree.symbol
          val pos: String =
            s"[${tree.pos.startLine},${tree.pos.startColumn} .. ${tree.pos.endLine},${tree.pos.endColumn}]"

          val ancestorsOrSelf: Seq[Symbol] = {
            if (symbol.info.exists(_.signature.isInstanceOf[ClassSignature])) {
              val parentsOrSelf: Seq[Symbol] = getParentSymbolsOrSelf(symbol)
              parentsOrSelf
            } else {
              Seq.empty
            }
          }
          SymbolDisplayInfo(tree.getClass.getSimpleName, pos, symbol, ancestorsOrSelf)
        }

    // Note that XML is used rather than JSON for the (CDATA) multi-line tree structure
    // Yet, when using XML as output format, the Symbols should be CDATA text as well
    implicit val parentScope: Scope = Scope.empty
    val newElem: Elem =
      Node.elem(
        Node.elemName("sourceDocument"),
        Seq(
          Node.textElem(Node.elemName("file"), Node.text(fileName.toString)),
          Node.textElem(Node.elemName("treeStructure"), Node.cdataText(treeStructure)),
          Node.elem(
            Node.elemName("symbols"),
            symbolInfo.map { symInfo =>
              Node.textElem(
                Node.elemName("symbol"),
                Map(Node.attrName("treeClassName") -> symInfo.treeSimpleClassName, Node.attrName("pos") -> symInfo.pos),
                Node.cdataText(symInfo.symbol.toString)
              )
            }
          ),
          Node.elem(
            Node.elemName("symbolAncestries"),
            symbolInfo.distinctBy(_.symbol.toString).filter(_.ancestorSymbolsOrSelf.nonEmpty).map { symInfo =>
              Node.elem(
                Node.elemName("symbolAncestry"),
                Map(Node.attrName("symbol") -> symInfo.symbol.toString),
                symInfo.ancestorSymbolsOrSelf.map { ancestorOrSelf =>
                  Node.textElem(Node.elemName("ancestorOrSelf"), Node.cdataText(ancestorOrSelf.toString))
                }
              )
            }
          )
        )
      )

    newElem
  }

}

object TreeAndSymbolDisplayer {

  final case class SymbolDisplayInfo(
      treeSimpleClassName: String,
      pos: String,
      symbol: Symbol,
      ancestorSymbolsOrSelf: Seq[Symbol]
  )

}

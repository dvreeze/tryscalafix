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

import scala.meta.Tree
import scala.reflect.ClassTag

/**
 * Support for querying Scalameta trees, inspired by XPath axes. It somewhat resembles and overlaps with
 * contrib.TreeOps. It has been copied (and slightly adapted) from the try-scalameta project.
 *
 * @author
 *   Chris de Vreeze
 */
object QuerySupport {

  trait QueryApi {

    // Child axis

    def filterChildren[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    def findFirstChild[A <: Tree: ClassTag](p: A => Boolean): Option[A]

    def findFirstChild[A <: Tree: ClassTag](): Option[A]

    // Descendant-or-self axis

    def filterDescendantsOrSelf[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    def findAllDescendantsOrSelf[A <: Tree: ClassTag](): Seq[A]

    def findFirstDescendantOrSelf[A <: Tree: ClassTag](p: A => Boolean): Option[A]

    def findFirstDescendantOrSelf[A <: Tree: ClassTag](): Option[A]

    // Descendant axis

    def filterDescendants[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    /**
     * Equivalent to `TreeOps.descendants(this).collect { case t: A => t }`
     */
    def findAllDescendants[A <: Tree: ClassTag](): Seq[A]

    def findFirstDescendant[A <: Tree: ClassTag](p: A => Boolean): Option[A]

    def findFirstDescendant[A <: Tree: ClassTag](): Option[A]

    // Like descendant-or-self axis, but only topmost

    def findTopmostOrSelf[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    def findAllTopmostOrSelf[A <: Tree: ClassTag](): Seq[A]

    // Like descendant axis, but only topmost

    def findTopmost[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    def findAllTopmost[A <: Tree: ClassTag](): Seq[A]

    // Ancestor-or-self axis

    def filterAncestorsOrSelf[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    def findAllAncestorsOrSelf[A <: Tree: ClassTag](): Seq[A]

    def findFirstAncestorOrSelf[A <: Tree: ClassTag](p: A => Boolean): Option[A]

    def findFirstAncestorOrSelf[A <: Tree: ClassTag](): Option[A]

    // Ancestor axis

    def filterAncestors[A <: Tree: ClassTag](p: A => Boolean): Seq[A]

    /**
     * Equivalent to `TreeOps.ancestors(this).collect { case t: A => t }`
     */
    def findAllAncestors[A <: Tree: ClassTag](): Seq[A]

    def findFirstAncestor[A <: Tree: ClassTag](p: A => Boolean): Option[A]

    def findFirstAncestor[A <: Tree: ClassTag](): Option[A]
  }

  implicit class WithQueryMethods(val tree: Tree) extends QueryApi {

    // Child axis

    def filterChildren[A <: Tree: ClassTag](p: A => Boolean): Seq[A] = QuerySupport.filterChildren[A](tree, p)

    def findFirstChild[A <: Tree: ClassTag](p: A => Boolean): Option[A] = QuerySupport.findFirstChild[A](tree, p)

    def findFirstChild[A <: Tree: ClassTag](): Option[A] = QuerySupport.findFirstChild[A](tree)

    // Descendant-or-self axis

    def filterDescendantsOrSelf[A <: Tree: ClassTag](p: A => Boolean): Seq[A] =
      QuerySupport.filterDescendantsOrSelf[A](tree, p)

    def findAllDescendantsOrSelf[A <: Tree: ClassTag](): Seq[A] =
      QuerySupport.findAllDescendantsOrSelf[A](tree)

    def findFirstDescendantOrSelf[A <: Tree: ClassTag](p: A => Boolean): Option[A] =
      QuerySupport.findFirstDescendantOrSelf[A](tree, p)

    def findFirstDescendantOrSelf[A <: Tree: ClassTag](): Option[A] = QuerySupport.findFirstDescendantOrSelf[A](tree)

    // Descendant axis

    def filterDescendants[A <: Tree: ClassTag](p: A => Boolean): Seq[A] =
      QuerySupport.filterDescendants[A](tree, p)

    def findAllDescendants[A <: Tree: ClassTag](): Seq[A] = QuerySupport.findAllDescendants[A](tree)

    def findFirstDescendant[A <: Tree: ClassTag](p: A => Boolean): Option[A] =
      QuerySupport.findFirstDescendant[A](tree, p)

    def findFirstDescendant[A <: Tree: ClassTag](): Option[A] = QuerySupport.findFirstDescendant[A](tree)

    // Like descendant-or-self axis, but only topmost

    def findTopmostOrSelf[A <: Tree: ClassTag](p: A => Boolean): Seq[A] =
      QuerySupport.findTopmostOrSelf[A](tree, p)

    def findAllTopmostOrSelf[A <: Tree: ClassTag](): Seq[A] = QuerySupport.findAllTopmostOrSelf[A](tree)

    // Like descendant axis, but only topmost

    def findTopmost[A <: Tree: ClassTag](p: A => Boolean): Seq[A] = QuerySupport.findTopmost[A](tree, p)

    def findAllTopmost[A <: Tree: ClassTag](): Seq[A] = QuerySupport.findAllTopmost[A](tree)

    // Ancestor-or-self axis

    def filterAncestorsOrSelf[A <: Tree: ClassTag](p: A => Boolean): Seq[A] =
      QuerySupport.filterAncestorsOrSelf[A](tree, p)

    def findAllAncestorsOrSelf[A <: Tree: ClassTag](): Seq[A] = QuerySupport.findAllAncestorsOrSelf[A](tree)

    def findFirstAncestorOrSelf[A <: Tree: ClassTag](p: A => Boolean): Option[A] =
      QuerySupport.findFirstAncestorOrSelf[A](tree, p)

    def findFirstAncestorOrSelf[A <: Tree: ClassTag](): Option[A] = QuerySupport.findFirstAncestorOrSelf[A](tree)

    // Ancestor axis

    def filterAncestors[A <: Tree: ClassTag](p: A => Boolean): Seq[A] =
      QuerySupport.filterAncestors[A](tree, p)

    def findAllAncestors[A <: Tree: ClassTag](): Seq[A] = QuerySupport.findAllAncestors[A](tree)

    def findFirstAncestor[A <: Tree: ClassTag](p: A => Boolean): Option[A] = QuerySupport.findFirstAncestor[A](tree, p)

    def findFirstAncestor[A <: Tree: ClassTag](): Option[A] = QuerySupport.findFirstAncestor[A](tree)
  }

  // The same query API, but not OO

  // Child axis

  def filterChildren[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] = {
    tree.children.collect { case t: A if p(t) => t }
  }

  def findFirstChild[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Option[A] =
    filterChildren[A](tree, p).headOption

  def findFirstChild[A <: Tree: ClassTag](tree: Tree): Option[A] = filterChildren[A](tree, _ => true).headOption

  // Descendant-or-self axis

  def filterDescendantsOrSelf[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] = {
    val optSelf: Seq[A] = Seq(tree).collect { case t: A if p(t) => t }
    // Recursive
    tree.children.flatMap(ch => filterDescendantsOrSelf[A](ch, p)).prependedAll(optSelf)
  }

  def findAllDescendantsOrSelf[A <: Tree: ClassTag](tree: Tree): Seq[A] = filterDescendantsOrSelf[A](tree, _ => true)

  def findFirstDescendantOrSelf[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Option[A] =
    findTopmostOrSelf[A](tree, p).headOption

  def findFirstDescendantOrSelf[A <: Tree: ClassTag](tree: Tree): Option[A] =
    findAllTopmostOrSelf[A](tree).headOption

  // Descendant axis

  def filterDescendants[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] = {
    tree.children.flatMap(ch => filterDescendantsOrSelf[A](ch, p))
  }

  def findAllDescendants[A <: Tree: ClassTag](tree: Tree): Seq[A] = filterDescendants[A](tree, _ => true)

  def findFirstDescendant[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Option[A] =
    findTopmost[A](tree, p).headOption

  def findFirstDescendant[A <: Tree: ClassTag](tree: Tree): Option[A] = findAllTopmost[A](tree).headOption

  // Like descendant-or-self axis, but only topmost

  def findTopmostOrSelf[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] = {
    val optSelf: Seq[A] = Seq(tree).collect { case t: A if p(t) => t }

    if (optSelf.nonEmpty) {
      optSelf
    } else {
      // Recursive
      tree.children.flatMap(ch => findTopmostOrSelf[A](ch, p))
    }
  }

  def findAllTopmostOrSelf[A <: Tree: ClassTag](tree: Tree): Seq[A] = findTopmostOrSelf[A](tree, _ => true)

  // Like descendant axis, but only topmost

  def findTopmost[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] =
    tree.children.flatMap(ch => findTopmostOrSelf[A](ch, p))

  def findAllTopmost[A <: Tree: ClassTag](tree: Tree): Seq[A] = findTopmost[A](tree, _ => true)

  // Ancestor-or-self axis

  def filterAncestorsOrSelf[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] = {
    val optSelf: Seq[A] = Seq(tree).collect { case t: A if p(t) => t }
    // Recursive
    tree.parent.toSeq.flatMap(parent => filterAncestorsOrSelf[A](parent, p)).prependedAll(optSelf)
  }

  def findAllAncestorsOrSelf[A <: Tree: ClassTag](tree: Tree): Seq[A] = filterAncestorsOrSelf[A](tree, _ => true)

  def findFirstAncestorOrSelf[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Option[A] =
    filterAncestorsOrSelf[A](tree, p).headOption

  def findFirstAncestorOrSelf[A <: Tree: ClassTag](tree: Tree): Option[A] = findAllAncestorsOrSelf[A](tree).headOption

  // Ancestor axis

  def filterAncestors[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Seq[A] = {
    tree.parent.toSeq.flatMap(parent => filterAncestorsOrSelf[A](parent, p))
  }

  def findAllAncestors[A <: Tree: ClassTag](tree: Tree): Seq[A] = filterAncestors[A](tree, _ => true)

  def findFirstAncestor[A <: Tree: ClassTag](tree: Tree, p: A => Boolean): Option[A] =
    filterAncestors[A](tree, p).headOption

  def findFirstAncestor[A <: Tree: ClassTag](tree: Tree): Option[A] = findAllAncestors[A](tree).headOption
}

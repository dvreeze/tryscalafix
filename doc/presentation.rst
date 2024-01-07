===========================================
Presentation on querying code with Scalafix
===========================================

This project, try-scalafix_, plays with the idea of using Scalafix_ semantic rules
for querying code bases, in order to help understand them and to see how they "hang together".

This presentation is about that: how can Scalafix_ help us understand potentially large code bases
written in Scala (and maybe partly even in Java)?

Purpose of this presentation
============================

Problem: understanding large (Scala/Java) code bases consisting of multiple github repositories
(each one implementing some "service") is hard. It is challenging to:

* understand flows that span multiple services
* have a mental picture of all (synchronous and asynchronous) inputs and outputs of individual services
* grasp which main "application objects" make up a service and how they "hang together"

Obviously, there are many different ways of helping ourselves with this.

Good *documentation* is needed (e.g. in readme file of a service), but cannot be too specific in
implementation details, or else it easily gets out of date.

Looking at the *runtime behaviour* of a service (logs, metrics, instrumentation, runtime service overviews) is
also valuable in understanding services and their interaction, but some of it may easily get forgotten
(in my opinion).

Using tooling or software libraries to *statically* look at source code or compiler output (e.g. Java
class files, or Scala compiler-generated "semanticdb" files) may also be quite helpful. There is plenty
of choice in tooling and libraries to help "query" code bases.

For example, `Java Reflection`_ can get us quite far, both for Scala and Java (the Scala or Java compiler
both emit "class files", after all). But Java reflection cannot peek inside method bodies, for example.

Scalameta_ and SemanticDB_, exposed via Scalafix_, can also get us quite far, with the advantage that
the entire Scala source trees can be queried. (And even for Java we query "class files", but like is the case
for the Java reflection API introspection ends at method signatures.) This combination of Scalameta_,
SemanticDB_ and Scalafix_ is what is presented here.

Scalafix, Scalameta and SemanticDB
==================================

Let's first briefly recap Scalameta_, SemanticDB_ and Scalafix_. The good news is that this stack is
quite accessible to Scala programmers, requiring no knowledge about compiler internals.

With Scalameta_ we can obtain *syntax trees* parsed from Scala source code. See the `Tree Guide`_ for
an explanation of Scalameta trees. These syntax trees can be queried for tree nodes that we might be
interested in.

Syntax trees are similar to the output of the first phase of the Scala compiler. They show the syntactic
structure of some source code, but they contain *no semantics* yet. In particular, by themselves these
trees do not help us to resolve references to classes on the one hand to the corresponding class definitions
on the other hand. Analogous remarks apply to methods, fields etc.

So, for any useful queries on syntax trees, we need those trees to be annotated with semantics.

That's where SemanticDB_ comes in. Unlike the Scalameta Trees API mentioned above, it's not a Scala API
that is currently practical to use, though.

It's foremost a *specification* (for Scala and Java) and "semanticdb" file format. The latter is emitted
by the Scala compiler with the help of the *semanticdb-scalac* compiler plugin. Build tools (such as sbt_
and Maven_) make generation of "semanticdb" files relatively easy.

What these "semanticdb" files offer are *symbols* and *types*, among other things. Symbols link references
(for methods, classes etc.) to definitions.

Besides the Scalameta_ Scala API and compiler-generated "semanticdb" files, we need a Scala API to access
the semantical information contained in the "semanticdb" files. That's where Scalafix_ comes in.

Scalafix_ is meant to be used for linting and automatic code rewriting, but it also contains the SemanticDB
data model in its `Scalafix Scala API`_, which makes it useful even beyond linting and automatic code rewriting.

That's what we are using here. Scalafix *rules* have (at least) the following structure:

.. code-block:: scala

    class MyRule extends SemanticRule("MyRule") {
      override def fix(implicit doc: SemanticDocument): Patch = {
        ???
      }
    ]

The implicit *SemanticDocument* is also a "symbol table", which helps resolve symbols for (compiled)
Java/Scala code anywhere on the class path.

Given an implicit "symbol table" (so typically, *SemanticDocument*), and given a Scalameta_ Tree node,
we can get the tree node's *Symbol*, the symbol's *SymbolInformation* and the symbol information's
*Signature*:

.. code-block:: scala

    // tree.symbol is obtained using an implicit SemanticDocument (or any Symtab)
    // tree.symbol.info is an optional SymbolInformation, but it is always non-empty
    // tree.symbol.info.get.signature is typically a class/method/value signature

    tree.symbol.info.get.signature

For any other Java/Scala *Symbol* on the class path, we can also contain *SymbolInformation* and its
*Signature*:

.. code-block:: scala

    // Symbols (Java or Scala) on the class path, obtained indirectly (from signatures, typically)

    symbol.info.get.signature

See `Scalafix Scala API`_.

Using Scalafix for querying code bases
======================================

TODO:

* "abusing" Scalafix
* example code
* what it gives us in terms of the mentioned goal
* bootstrapping
* practical etc.

Conclusion
==========

This time I can really claim that it's practical to use Scalafix_ to write "rules" that query Scala/Java code
bases to help us understand them more quickly. Moreover, any documentation generated from such Scalafix_
rules can easily be kept up-to-date.

.. _`try-scalafix`: https://github.com/dvreeze/tryscalafix
.. _`Scalafix`: https://scalacenter.github.io/scalafix
.. _`Java Reflection`: https://www.oracle.com/technical-resources/articles/java/javareflection.html
.. _`Scalameta`: https://scalameta.org
.. _`SemanticDB`: https://scalameta.org/docs/semanticdb/guide.html
.. _`Tree Guide`: https://scalameta.org/docs/trees/guide.html
.. _sbt: https://www.scala-sbt.org/
.. _Maven: https://maven.apache.org/
.. _`Scalafix Scala API`: https://scalacenter.github.io/scalafix/docs/developers/api.html


========================================================
Presentation on playing with querying code with Scalafix
========================================================

This project, try-scalafix_, plays with the idea of using Scalafix_ semantic rules
for querying code bases, in order to help understand them and to see how they "hang together".

This presentation is about that: how can Scalafix_ help us understand potentially large code bases
written in Scala (and maybe partly even in Java)?

Purpose of this presentation
============================

Problem: understanding large (Scala/Java) code bases consisting of multiple github repositories
(each one implementing some "service") is hard. It is challenging to:

* understand flows that span multiple services
* have a mental picture of (synchronous and asynchronous) inputs and outputs of individual services
* grasp which main "application objects" make up a service and how they "hang together"

There are many different ways of getting an overview of (some or all of) these things.

Good *documentation* is needed (e.g. in readme file of a service), but cannot be too specific in
implementation details or else it easily gets out of date.

Looking at the *runtime behaviour* of a service (logs, metrics, runtime service overviews) is also valuable
in understanding services and their interaction, but some of it may easily get forgotten (in my opinion).

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

symbols etc.

Using Scalafix for querying code bases
======================================

practical etc.

Conclusion
==========


.. _`try-scalafix`: https://github.com/dvreeze/tryscalafix
.. _`Scalafix`: https://scalacenter.github.io/scalafix/docs/users/installation.html
.. _`Java Reflection`: https://www.oracle.com/technical-resources/articles/java/javareflection.html
.. _`Scalameta`: https://scalameta.org
.. _`SemanticDB`: https://scalameta.org/docs/semanticdb/guide.html


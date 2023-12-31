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

package eu.cdevreeze.tryscalafix

import scalafix.v1.SemanticDocument

/**
 * SemanticDocument analyser, which is technically like a SemanticRule whose "apply" method returns another type than
 * Scalafix Patch. Typically, the "apply" method returns JSON (e.g. from the circe library).
 *
 * SemanticDocumentAnalyser instances can be nested.
 *
 * @author
 *   Chris de Vreeze
 */
trait SemanticDocumentAnalyser[R] extends Function[SemanticDocument, R]

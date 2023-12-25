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

import io.circe.Json
import scalafix.v1.SemanticDocument

/**
 * SemanticDocument analyser taking accumulated JSON as additional input and returning enhanced JSON that takes the work
 * done by this analyser on the input SemanticDocument into account.
 *
 * SemanticDocumentAnalyser instances can be nested.
 *
 * @author
 *   Chris de Vreeze
 */
trait SemanticDocumentAnalyser extends Function2[SemanticDocument, Json, Json]

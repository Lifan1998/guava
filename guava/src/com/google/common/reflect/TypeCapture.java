/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.reflect;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures the actual type of {@code T}.
 *
 * 捕获 {@code T} 的实际类型。
 *
 * @author Ben Yu
 */
abstract class TypeCapture<T> {

  /**
   * Returns the captured type.
   *
   * 返回捕获的类型
   */
  final Type capture() {
    // 返回表示此 Class 表示的实体（类、接口、原始类型或 void）的直接超类
    Type superclass = getClass().getGenericSuperclass();
    // ParameterizedType 表示参数化类型，例如 集合<字符串>。
    checkArgument(superclass instanceof ParameterizedType, "%s isn't parameterized", superclass);
    // 返回一个 {@code Type} 对象数组，表示此类型的实际类型参数。
    return ((ParameterizedType) superclass).getActualTypeArguments()[0];
  }
}

/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.integrations.cdi.jpa;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.TypedQuery;

final class ClearingTypedQuery<X> extends DelegatingTypedQuery<X> {

    private final Runnable clearer;

    ClearingTypedQuery(Runnable persistenceContextClearer, TypedQuery<X> delegate) {
        super(delegate);
        this.clearer = Objects.requireNonNull(persistenceContextClearer, "persistenceContextClearer");
    }

    @Override
    public List<X> getResultList() {
      try {
          return super.getResultList();
      } finally {
          this.clearer.run();
      }
    }

    @Override
    public X getSingleResult() {
        try {
            return super.getSingleResult();
        } finally {
            this.clearer.run();
        }
    }

}

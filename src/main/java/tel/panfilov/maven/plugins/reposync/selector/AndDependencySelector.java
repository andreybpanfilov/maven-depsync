/*-
 * #%L
 * reposync-maven-plugin
 * %%
 * Copyright (C) 2022 Project Contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package tel.panfilov.maven.plugins.reposync.selector;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class AndDependencySelector implements DependencySelector {

    private final Set<? extends DependencySelector> selectors;

    private final int hashCode;

    public AndDependencySelector(DependencySelector... selectors) {
        if (selectors != null && selectors.length > 0) {
            this.selectors = new LinkedHashSet<>(Arrays.asList(selectors));
        } else {
            this.selectors = Collections.emptySet();
        }
        this.hashCode = this.selectors.hashCode();
    }

    public AndDependencySelector(Collection<? extends DependencySelector> selectors) {
        if (selectors != null && !selectors.isEmpty()) {
            this.selectors = new LinkedHashSet<>(selectors);
        } else {
            this.selectors = Collections.emptySet();
        }
        this.hashCode = this.selectors.hashCode();
    }

    private AndDependencySelector(Set<DependencySelector> selectors) {
        if (selectors != null && !selectors.isEmpty()) {
            this.selectors = selectors;
        } else {
            this.selectors = Collections.emptySet();
        }
        this.hashCode = this.selectors.hashCode();
    }

    public static DependencySelector newInstance(DependencySelector selector1, DependencySelector selector2) {
        if (selector1 == null) {
            return selector2;
        }
        if (selector2 == null || selector2.equals(selector1)) {
            return selector1;
        }
        return new AndDependencySelector(selector1, selector2);
    }

    public boolean selectDependency(Dependency dependency) {
        for (DependencySelector selector : selectors) {
            if (!selector.selectDependency(dependency)) {
                return false;
            }
        }
        return true;
    }

    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        Set<DependencySelector> derived = selectors.stream()
                .map(selector -> selector.deriveChildSelector(context))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (derived.isEmpty()) {
            return null;
        }
        if (derived.size() == 1) {
            return derived.iterator().next();
        }
        return new AndDependencySelector(derived);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }
        AndDependencySelector that = (AndDependencySelector) obj;
        return selectors.equals(that.selectors);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

}

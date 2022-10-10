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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScopeDependencySelector implements DependencySelector {

    public static final List<String> NON_TRANSITIVE = Arrays.asList("test", "provided");

    private final int depth;

    private final Set<String> included;

    private final Set<String> excluded;

    public ScopeDependencySelector(Collection<String> included, Collection<String> excluded) {
        this(0, included, excluded);
    }

    public ScopeDependencySelector(int depth, String... excluded) {
        this(depth, null, (excluded != null) ? Arrays.asList(excluded) : null);
    }

    public ScopeDependencySelector(int depth, Collection<String> included, Collection<String> excluded) {
        this.depth = depth;
        this.included = included == null ? new HashSet<>() : new HashSet<>(included);
        this.excluded = excluded == null ? new HashSet<>() : new HashSet<>(excluded);
    }

    public boolean selectDependency(Dependency dependency) {
        String scope = dependency.getScope();
        return (included.isEmpty() || included.contains(scope)) && (excluded.isEmpty() || !excluded.contains(scope));
    }

    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        if (depth > 0 || context.getDependency() == null) {
            return this;
        }
        Set<String> excluded = new HashSet<>(this.excluded);
        excluded.addAll(NON_TRANSITIVE);
        return new ScopeDependencySelector(depth + 1, included, excluded);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }
        ScopeDependencySelector that = (ScopeDependencySelector) obj;
        return depth == that.depth && included.equals(that.included) && excluded.equals(that.excluded);
    }


    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + depth;
        hash = hash * 31 + included.hashCode();
        hash = hash * 31 + excluded.hashCode();
        return hash;
    }

}

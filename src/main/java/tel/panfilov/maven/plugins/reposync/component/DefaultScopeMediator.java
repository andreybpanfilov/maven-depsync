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

package tel.panfilov.maven.plugins.reposync.component;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component(role = ScopeMediator.class)
public class DefaultScopeMediator implements ScopeMediator {


    public Set<String> negate(String scope) {
        List<String> scopes = Arrays.asList(StringUtils.split(scope, ","));
        return negate(scopes);
    }

    public Set<String> negate(Collection<String> scopes) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, "system", "compile", "provided", "runtime", "test");
        for (String scope : scopes) {
            if ("compile".equals(scope)) {
                result.remove("compile");
                result.remove("system");
                result.remove("provided");
            } else if ("runtime".equals(scope)) {
                result.remove("compile");
                result.remove("runtime");
            } else if ("compile+runtime".equals(scope)) {
                result.remove("compile");
                result.remove("system");
                result.remove("provided");
                result.remove("runtime");
            } else if ("runtime+system".equals(scope)) {
                result.remove("compile");
                result.remove("system");
                result.remove("runtime");
            } else if ("test".equals(scope)) {
                result.clear();
            }
        }
        return result;
    }


}

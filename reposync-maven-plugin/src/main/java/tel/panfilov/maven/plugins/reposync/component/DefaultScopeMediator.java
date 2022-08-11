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

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

package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class Utils {

    private Utils() {
        super();
    }


    public static void checkResult(ArtifactResult result, Predicate<Exception> ignore) throws MojoExecutionException {
        List<Exception> exceptions = result.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (Exception exception : exceptions) {
            if (!ignore.test(exception)) {
                throw new MojoExecutionException("Failed to resolve artifact " + result.getRequest().getArtifact(), exceptions.get(0));
            }
        }
    }

    public static void checkResult(ArtifactDescriptorResult result, Predicate<Exception> ignore) throws MojoExecutionException {
        List<Exception> exceptions = result.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (Exception exception : exceptions) {
            if (!ignore.test(exception)) {
                throw new MojoExecutionException("Failed to load descriptor " + result.getRequest().getArtifact(), exceptions.get(0));
            }
        }
    }

    public static ArtifactRepositoryPolicy getUpdateAlwaysPolicy() {
        return new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);
    }

    public static ArtifactRequest artifactRequest(Artifact aetherArtifact, List<RemoteRepository> remoteRepositories) {
        return new ArtifactRequest(aetherArtifact, remoteRepositories, null);
    }

    public static List<ArtifactRequest> artifactRequests(List<Artifact> aetherArtifacts, List<RemoteRepository> remoteRepositories) {
        List<ArtifactRequest> result = new ArrayList<>();
        for (Artifact artifact : aetherArtifacts) {
            result.add(artifactRequest(artifact, remoteRepositories));
        }
        return result;
    }

    public static Artifact getPomArtifact(Parent parent) {
        return new DefaultArtifact(
                parent.getGroupId(),
                parent.getArtifactId(),
                "",
                "pom",
                parent.getVersion()
        );
    }

    public static Artifact getPomArtifact(Dependency dependency) {
        return new DefaultArtifact(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                "",
                "pom",
                dependency.getVersion()
        );
    }

    public static Artifact toSourcesArtifact(Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "sources", "jar", artifact.getVersion());
    }

    public static Artifact toJavadocArtifact(Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "javadoc", "jar", artifact.getVersion());
    }

    public static Artifact toClassesArtifact(Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "classes", "jar", artifact.getVersion());
    }

    public static boolean isSourcesArtifact(Artifact artifact) {
        return "sources".equals(artifact.getClassifier());
    }

    public static boolean isJavadocArtifact(Artifact artifact) {
        return "javadoc".equals(artifact.getClassifier());
    }

    public static boolean isPom(Artifact artifact) {
        return "pom".equals(artifact.getExtension());
    }

    public static boolean isWar(Artifact artifact) {
        return "war".equals(artifact.getExtension());
    }

    public static boolean hasClassifier(Artifact artifact) {
        return artifact.getClassifier().length() > 0;
    }

    public static String getId(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getClassifier() + ':' + artifact.getVersion() + ':' + artifact.getExtension();
    }

}

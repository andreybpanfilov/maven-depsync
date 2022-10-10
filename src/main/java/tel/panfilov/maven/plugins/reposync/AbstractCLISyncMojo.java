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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

public abstract class AbstractCLISyncMojo extends AbstractSyncMojo {

    private final org.apache.maven.model.Dependency coordinate = new org.apache.maven.model.Dependency();

    /**
     * The artifact - a string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter(property = "artifact")
    protected String artifact;

    /**
     * The groupId of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "groupId")
    protected String groupId;

    /**
     * The artifactId of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "artifactId")
    protected String artifactId;

    /**
     * The version of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "version")
    protected String version;

    /**
     * The classifier of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "classifier")
    protected String classifier;


    protected org.apache.maven.model.Dependency getCoordinate() throws MojoFailureException {
        if (coordinate.getArtifactId() == null && artifact == null) {
            throw new MojoFailureException("You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0");
        }
        if (artifact != null) {
            String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length < 3 || tokens.length > 5) {
                throw new MojoFailureException("Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            coordinate.setGroupId(tokens[0]);
            coordinate.setArtifactId(tokens[1]);
            coordinate.setVersion(tokens[2]);
            if (tokens.length >= 4) {
                coordinate.setType(tokens[3]);
            }
            if (tokens.length == 5) {
                coordinate.setClassifier(tokens[4]);
            }
        }
        return coordinate;
    }


    /**
     * @param groupId The groupId.
     */
    public void setGroupId(String groupId) {
        this.coordinate.setGroupId(groupId);
    }

    /**
     * @param artifactId The artifactId.
     */
    public void setArtifactId(String artifactId) {
        this.coordinate.setArtifactId(artifactId);
    }

    /**
     * @param version The version.
     */
    public void setVersion(String version) {
        this.coordinate.setVersion(version);
    }

    /**
     * @param classifier The classifier to be used.
     */
    public void setClassifier(String classifier) {
        this.coordinate.setClassifier(classifier);
    }

    /**
     * @param type packaging.
     */
    public void setPackaging(String type) {
        this.coordinate.setType(type);
    }


}

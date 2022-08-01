package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.util.HashMap;
import java.util.Map;

import static tel.panfilov.maven.plugins.reposync.Utils.getId;

public class ModelAwareArtifactDescriptorReader extends ArtifactDescriptorReaderDelegate {

    private Map<String, Model> modelMap = new HashMap<>();

    public void populateResult(RepositorySystemSession session, ArtifactDescriptorResult result, Model model) {
        super.populateResult(session, result, model);
        modelMap.put(getId(result.getArtifact()), model);
    }

    public Model getModel(Artifact artifact) {
        return modelMap.get(getId(artifact));
    }

}

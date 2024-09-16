package org.AItestAutomationEngineer.SourceControl;

import okhttp3.*;
import org.AItestAutomationEngineer.CodeToReview.CodeChange;
import org.kohsuke.github.GHPullRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

public class SourceControlCodeReview {
    public void postReviewComment(GHPullRequest pr, CodeChange codeChange, String comment, String githubToken) throws IOException {
        OkHttpClient client = new OkHttpClient();
        String url = String.format(
                "https://api.github.com/repos/%s/pulls/%d/comments",
                pr.getRepository().getFullName(),
                pr.getNumber()
        );

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.put("body", comment);
        jsonNode.put("commit_id", pr.getHead().getSha());
        jsonNode.put("path", codeChange.getFilePath());
        jsonNode.put("line", codeChange.getNewLineNumber());
        jsonNode.put("side", "RIGHT");

        String json = mapper.writeValueAsString(jsonNode);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                assert response.body() != null;
                throw new IOException("Failed to post review comment: " + response.body().string());
            }
        }
    }
}

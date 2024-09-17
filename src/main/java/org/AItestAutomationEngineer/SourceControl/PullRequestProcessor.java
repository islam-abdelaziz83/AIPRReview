package org.AItestAutomationEngineer.SourceControl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.AItestAutomationEngineer.AIModel.AIModelOperations;
import org.AItestAutomationEngineer.CodeToReview.CodeChange;
import org.AItestAutomationEngineer.Helper.Helper;
import org.AItestAutomationEngineer.Helper.lookup;
import org.kohsuke.github.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PullRequestProcessor {

    private final String JAVA_GUIDELINES;
    private final GitHub github;

    private final String githubToken;
    public PullRequestProcessor() throws IOException {
        // Load Java guidelines from file
        Helper helper = new Helper();
        JAVA_GUIDELINES = helper.loadInstructionsFile();

        // Load environment variables
        githubToken = helper.getEnvironmentVariableValue(lookup.GITHUB_TOKEN);

        // Initialize GitHub client
        github = new GitHubBuilder().withOAuthToken(githubToken).build();

    }
    private List<CodeChange> extractCodeChanges(String filePath, String patch) {
        List<CodeChange> codeChanges = new ArrayList<>();
        String[] lines = patch.split("\n");
        int newLineNum = 0;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                // Parse hunk header to get starting line numbers
                // Example hunk header: @@ -10,7 +10,6 @@
                String[] parts = line.split(" ");
                // e.g., "-10,7"
                String newFileRange = parts[2]; // e.g., "+10,6"

                newLineNum = Integer.parseInt(newFileRange.split(",")[0].substring(1)) - 1;
            } else {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    // Added line
                    newLineNum++;
                    String addedLine = line.substring(1); // Remove '+'
                    codeChanges.add(new CodeChange(filePath, addedLine, newLineNum));
                } else {
                    // Context line
                    newLineNum++;
                }
            }
        }
        return codeChanges;
    }
    private List<GHPullRequest> getOpenPullRequests(String repoName) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        return repo.getPullRequests(GHIssueState.OPEN);
    }
    private GHPullRequest getOpenPullRequestById(String repoName, Integer prId) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        return repo.getPullRequest(prId);
    }
    public int getPullRequestComments(String repoName, int prNumber) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        GHPullRequest pr = repo.getPullRequest(prNumber);

        int reviewCommentsCount = pr.listReviewComments().toList().size();
        int issueCommentsCount = pr.listComments().toList().size();

        return reviewCommentsCount + issueCommentsCount;
    }

    private List<CodeChange> processPullRequestData(GHPullRequest pullRequest){
        List<CodeChange> codeChanges = new ArrayList<>();
        for (GHPullRequestFileDetail file : pullRequest.listFiles()) {
            if (file.getFilename().endsWith(".java") && file.getPatch() != null) {
                List<CodeChange> extractedChanges = extractCodeChanges(file.getFilename(), file.getPatch());
                codeChanges.addAll(extractedChanges);
            }
        }
        return codeChanges;
    }

    public void processPullRequests(String repoName) throws IOException {
        AIModelOperations aiModelOperations = new AIModelOperations();
        List<GHPullRequest> pullRequests = getOpenPullRequests(repoName);
        for (GHPullRequest pr : pullRequests) {
            if (getPullRequestComments(repoName, pr.getNumber()) >= 0) {
                List<CodeChange> codeChanges = processPullRequestData(pr);

                for (CodeChange codeChange : codeChanges) {
                    String suggestion = aiModelOperations.suggestCommentsUsingAI(codeChange, JAVA_GUIDELINES);
                    assert suggestion != null;
                    if (!"0".equals(suggestion.trim())) {
                        // Post the comment directly
                        postReviewComment(pr, codeChange, suggestion, githubToken);
                    }
                }
            }
        }
    }
    public void processPullRequests(String repoName, Integer prId) throws IOException {
        AIModelOperations aiModelOperations = new AIModelOperations();
        GHPullRequest pr = getOpenPullRequestById(repoName, prId);
        if (getPullRequestComments(repoName, pr.getNumber()) >= 0) {
            List<CodeChange> codeChanges = processPullRequestData(pr);

            for (CodeChange codeChange : codeChanges) {
                String suggestion = aiModelOperations.suggestCommentsUsingAI(codeChange, JAVA_GUIDELINES);
                assert suggestion != null;
                if (!"0".equals(suggestion.trim())) {
                    // Post the comment directly
                    postReviewComment(pr, codeChange, suggestion, githubToken);
                }
            }
        }
    }
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
package org.AItestAutomationEngineer;

import io.github.cdimascio.dotenv.Dotenv;
import org.kohsuke.github.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PullRequestProcessor {

    private final String JAVA_GUIDELINES;
    private final String githubToken;
    private final String genaiApiKey;
    private final GitHub github;

    public PullRequestProcessor() throws IOException {
        JAVA_GUIDELINES = loadInstructionsFile("src/main/instructions/java_guidelines.txt");
        Dotenv dotenv = Dotenv.load();
        githubToken = dotenv.get("GITHUB_TOKEN");
        genaiApiKey = dotenv.get("GENAI_API_KEY");
        github = new GitHubBuilder().withOAuthToken(githubToken).build();
    }

    private String loadInstructionsFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private List<GHPullRequest> getOpenPullRequests(String repoName) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        return repo.getPullRequests(GHIssueState.OPEN);
    }

    private String extractCodeChanges(String patch) {
        StringBuilder changes = new StringBuilder();
        String[] lines = patch.split("\n");
        for (String line : lines) {
            if (!line.startsWith("@@")) {
                if (line.startsWith("+") || line.startsWith("-")) {
                    changes.append(line).append("\n");
                }
            }
        }
        return changes.toString();
    }

    public int getPullRequestComments(String repoName, int prNumber) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        GHPullRequest pr = repo.getPullRequest(prNumber);

        int reviewCommentsCount = pr.listReviewComments().toList().size();
        int issueCommentsCount = pr.listComments().toList().size();

        return reviewCommentsCount + issueCommentsCount;
    }

    private List<String> processPullRequestData(GHPullRequest pullRequest) throws IOException {
        List<String> changes = new ArrayList<>();
        for (GHPullRequestFileDetail file : pullRequest.listFiles()) {
            if (file.getFilename().endsWith(".java")) {
                String extractedChanges = extractCodeChanges(file.getPatch());
                changes.add(extractedChanges);
            }
        }
        return changes;
    }

    public String processPullRequests(String repoName) throws IOException {
        List<GHPullRequest> pullRequests = getOpenPullRequests(repoName);
        for (GHPullRequest pr : pullRequests) {
            if (getPullRequestComments(repoName, pr.getNumber()) == 0) {
                List<String> changes = processPullRequestData(pr);
                List<String> patches = changesToPatch(changes);
                for (String patch : patches) {
                    String suggestions = genaiSuggest(patch, JAVA_GUIDELINES);
                    if (Integer.parseInt(suggestions) > 0) {
                        break;
                    } else {
                        postComment(repoName, pr.getNumber(), suggestions);
                    }
                }
            }
        }
        return "Pull requests processed successfully";
    }

    private List<String> changesToPatch(List<String> changes) {
        List<String> patches = new ArrayList<>();
        for (String change : changes) {
            patches.add(change);
        }
        return patches;
    }

    private String genaiSuggest(String patch, String javaGuidelines) {
        String prompt = String.format(
                "Review the following Java method changes according to the Java guidelines: %s. " +
                        "Focus only on method names and suggest improvements based on the guidelines. " +
                        "Changes: %s. Example: The method name in %s should follow: %s. " +
                        "Do not provide general suggestions, focus solely on method names. " +
                        "Mention only one example. " +
                        "In case of any change return 0.", javaGuidelines, patch, patch, javaGuidelines
        );
        // Placeholder for the GenAI model call
        return "0"; // Assuming no suggestions for now
    }

    private void postComment(String repoName, int prNumber, String comment) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        GHPullRequest pr = repo.getPullRequest(prNumber);
        pr.comment(comment);
    }
}
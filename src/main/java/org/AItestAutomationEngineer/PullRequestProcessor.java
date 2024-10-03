package org.AItestAutomationEngineer;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
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
    private final String OpenAiApiKey;
    private final GitHub github;

    public PullRequestProcessor() throws IOException {
        JAVA_GUIDELINES = loadInstructionsFile("src/main/instructions/java_guidelines.txt");
        Dotenv dotenv = Dotenv.load();
        githubToken = dotenv.get("GITHUB_TOKEN");
        OpenAiApiKey = dotenv.get("OPENAI_API_KEY");
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
            if (getPullRequestComments(repoName, pr.getNumber()) >= 0) {
                List<String> changes = processPullRequestData(pr);
                List<String> patches = changesToPatch(changes);
                for (String patch : patches) {
                    String suggestions = SuggestCommentsUsingAI(patch, JAVA_GUIDELINES);
                    if ("0".equals(suggestions.trim())) {
                        // No changes needed, skip commenting
                        continue;
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

    private String SuggestCommentsUsingAI(String patch, String javaGuidelines) {
        OpenAiService service = new OpenAiService(OpenAiApiKey);
        String prompt = String.format(
                "Review the following Java code changes according to the Java guidelines:\n%s\n" +
                        "Provide specific suggestions for improvements based on the guidelines. " +
                        "Focus on all aspects, including method names, code structure, and best practices.\n" +
                        "Changes:\n%s\n" +
                        "If no changes are needed, return 0.",
                javaGuidelines, patch
        );

        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a Software Test Automation Lead.");
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-4o-2024-08-06")
                .messages(List.of(systemMessage, userMessage))
                .build();

        ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);
        long usedTokens = result.getUsage().getTotalTokens();
        ChatMessage response = result.getChoices().get(0).getMessage();

        System.out.println("AI Response:\n" + response.getContent());
        System.out.println("Total tokens used: " + usedTokens);

        return response.getContent();
    }
    private void postComment(String repoName, int prNumber, String comment) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        GHPullRequest pr = repo.getPullRequest(prNumber);
        pr.comment(comment);
    }
}
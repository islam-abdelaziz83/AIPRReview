package org.AItestAutomationEngineer;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import io.github.cdimascio.dotenv.Dotenv;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PullRequestProcessor {

    private final String JAVA_GUIDELINES;
    private final GitHub github;
    private final OpenAiService service; // Added OpenAiService instance

    public PullRequestProcessor() throws IOException {
        // Load Java guidelines from file
        JAVA_GUIDELINES = loadInstructionsFile();

        // Load environment variables
        Dotenv dotenv = Dotenv.load();
        String githubToken = dotenv.get("GITHUB_TOKEN");
        String openAiApiKey = dotenv.get("OPENAI_API_KEY");

        // Initialize GitHub client
        github = new GitHubBuilder().withOAuthToken(githubToken).build();

        // Initialize OpenAiService
        Duration timeout = Duration.ofSeconds(60);
        // Initialize OpenAiService with custom OkHttpClient
        service = new OpenAiService(openAiApiKey, timeout);
    }

    private String loadInstructionsFile() throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/instructions/java_guidelines.txt")));
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

    private List<String> processPullRequestData(GHPullRequest pullRequest) {
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
                    String suggestions = suggestCommentsUsingAI(patch, JAVA_GUIDELINES);
                    assert suggestions != null;
                    if (!"0".equals(suggestions.trim())) {
                        postComment(repoName, pr.getNumber(), suggestions);
                    }
                }
            }
        }
        return "Pull requests processed successfully";
    }

    private List<String> changesToPatch(List<String> changes) {
        return new ArrayList<>(changes);
    }

    private String suggestCommentsUsingAI(String patch, String javaGuidelines) {
        String prompt = String.format(
                """
                        As a Software Test Automation Lead, review the following Java code change according to the advanced Java guidelines:
                        %s
                        Provide specific suggestions for improvements based on the guidelines. \
                        Focus on all aspects, including method names, code structure, and best practices.
                        Code Change:
                        %s
                        If no changes are needed, return 0.""",
                javaGuidelines, patch
        );

        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a Software Test Automation Lead.");
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-4o-2024-08-06")
                .messages(List.of(systemMessage, userMessage))
                .build();

        int maxRetries = 3;
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                attempt++;
                // Use the existing service instance with custom timeouts
                ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);
                long usedTokens = result.getUsage().getTotalTokens();
                ChatMessage response = result.getChoices().getFirst().getMessage();

                System.out.println("AI Response:\n" + response.getContent());
                System.out.println("Total tokens used: " + usedTokens);

                return response.getContent();
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt >= maxRetries) {
                    e.printStackTrace();
                    return "Error: Unable to get response from OpenAI API after multiple attempts.";
                }
                // Wait before retrying
                try {
                    Thread.sleep(2000); // Wait 2 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return null; // This line should not be reached
    }

    private void postComment(String repoName, int prNumber, String comment) throws IOException {
        GHRepository repo = github.getRepository(repoName);
        GHPullRequest pr = repo.getPullRequest(prNumber);
        pr.comment(comment);
    }
}
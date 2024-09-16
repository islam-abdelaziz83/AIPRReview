package org.AItestAutomationEngineer;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import io.github.cdimascio.dotenv.Dotenv;
import org.AItestAutomationEngineer.CodeToReview.CodeChange;
import org.AItestAutomationEngineer.SourceControl.SourceControlCodeReview;
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
    private final String githubToken;
    public PullRequestProcessor() throws IOException {
        // Load Java guidelines from file
        JAVA_GUIDELINES = loadInstructionsFile();

        // Load environment variables
        Dotenv dotenv = Dotenv.load();
        githubToken = dotenv.get("GITHUB_TOKEN");
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
        SourceControlCodeReview codeReview = new SourceControlCodeReview();
        List<GHPullRequest> pullRequests = getOpenPullRequests(repoName);
        for (GHPullRequest pr : pullRequests) {
            if (getPullRequestComments(repoName, pr.getNumber()) >= 0) {
                List<CodeChange> codeChanges = processPullRequestData(pr);

                for (CodeChange codeChange : codeChanges) {
                    String suggestion = suggestCommentsUsingAI(codeChange, JAVA_GUIDELINES);
                    assert suggestion != null;
                    if (!"0".equals(suggestion.trim())) {
                        // Post the comment directly
                        codeReview.postReviewComment(pr, codeChange, suggestion, githubToken);
                    }
                }
            }
        }
    }

    private String suggestCommentsUsingAI(CodeChange codeChange, String javaGuidelines) {
        String prompt = String.format(
                """
                        As a Software Test Automation Lead, review the following Java code change according to the advanced Java guidelines:
                        %s
                        Provide specific suggestions for improvements based on the guidelines. \
                        Focus on all aspects, including method names, code structure, and best practices. \
                        Rules to follow during review: \
                        1- If there is no code in the provided change, do not add a comment for it in your response. \
                        2- Focus on the provided code change only, do not add any expression means the code is short or it is better to add more context to your response or comment
                        2- For each comment you would add in your response, the comment must be short as possible. \
                        Code Change:
                        %s
                        If no changes are needed, return 0.""",
                javaGuidelines, codeChange.getChangedCode()
        );

        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a Software Test Automation Lead.");
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-4")
                .messages(List.of(systemMessage, userMessage))
                .build();

        int maxRetries = 3;
        int attempt = 0;
        while (true) {
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
    }
}
package org.AItestAutomationEngineer.AIModel;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.AItestAutomationEngineer.CodeToReview.CodeChange;
import org.AItestAutomationEngineer.Helper.Helper;
import org.AItestAutomationEngineer.Helper.lookup;
import java.time.Duration;
import java.util.List;

public class AIModelOperations {
    Helper helper = new Helper();
    String openAiApiKey = helper.getEnvironmentVariableValue(lookup.OPENAI_API_KEY);
    private final OpenAiService service; // Added OpenAiService instance
    // Initialize OpenAiService
    Duration timeout = Duration.ofSeconds(60);

    public AIModelOperations() {
        // Initialize OpenAiService with custom OkHttpClient
        service = new OpenAiService(openAiApiKey, timeout);
    }
    public String suggestCommentsUsingAI(CodeChange codeChange, String javaGuidelines) {
        String prompt = String.format(
                """
                        As a Software Test Automation Lead, review the following Java code change according to the advanced Java guidelines: \
                        %s
                        Provide specific suggestions for improvements based on the guidelines. \
                        Focus on all aspects, including method names, code structure, and best practices. \
                        Rules to follow during review: \
                        1- If there is no code in the provided change, do not add a comment for it in your response. \
                        2- Focus on the provided code change only, do not add any expression means the code is short or it is better to add more context to your response or comment. \
                        3- For each comment you would add in your response, the comment must be short as possible. \
                        4- If no changes are needed, return 0.
                        Code Change:
                        %s
                        """,
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

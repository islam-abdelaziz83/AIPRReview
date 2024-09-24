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
                As a **Senior Test Automation Engineer**, review the following Java code change, which is part of a **test automation framework** built using **Java**, **Selenium**, **Maven**, and **TestNG**. Refer to the advanced Java guidelines and the specific best practices for test automation frameworks:
            
                %s
            
                Provide specific suggestions for improvements based on these guidelines. Focus on all aspects, including:
            
                - Test case design and structure
                - Proper use of Selenium WebDriver methods
                - Synchronization and waits
                - Use of TestNG annotations and configurations
                - Code readability and maintainability
                - Exception handling specific to automation testing
                - Adherence to the Page Object Model (if applicable)
            
                **Rules to follow during review:**
            
                1. **If the provided code change contains no code or is empty, respond with exactly "0" and do not add any additional text or explanation.**
                2. **If the provided code change contains code and no changes are needed, respond with exactly "0" and do not add any additional text or explanation.**
                3. **If the provided code change contains code and suggestions are needed, provide the specific suggestions.**
                4. **Do not mention anything about the length of the code or the need for more context.**
                5. **Keep each suggestion as short as possible.**
                6. **Do not include any greetings, sign-offs, or additional commentary beyond the specific suggestions.**
            
                **Code Change:**
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
                // Allow 2 seconds before sending another message to avoid rate limiting
                Thread.sleep(2000);
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

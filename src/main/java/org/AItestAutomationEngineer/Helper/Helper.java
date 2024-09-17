package org.AItestAutomationEngineer.Helper;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Helper {
    public String loadInstructionsFile() throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/instructions/java_guidelines.txt")));
    }
    public  String getEnvironmentVariableValue(lookup lookupKey)
    {
        // Load environment variables
        Dotenv dotenv = Dotenv.load();
        return switch (lookupKey) {
            case OPENAI_API_KEY -> dotenv.get("OPENAI_API_KEY");
            case GITHUB_TOKEN -> dotenv.get("GITHUB_TOKEN");
        };
    }
}

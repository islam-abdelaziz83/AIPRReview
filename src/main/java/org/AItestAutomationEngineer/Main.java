package org.AItestAutomationEngineer;

import org.AItestAutomationEngineer.SourceControl.PullRequestProcessor;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        PullRequestProcessor pullRequestProcessor = new PullRequestProcessor();
        try {
            pullRequestProcessor.processPullRequests("islam-abdelaziz83/AppiumPOC", 5);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
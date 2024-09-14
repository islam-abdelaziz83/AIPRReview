package org.AItestAutomationEngineer;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        PullRequestProcessor pullRequestProcessor = new PullRequestProcessor();
        try {
            System.out.println(pullRequestProcessor.processPullRequests("islam-abdelaziz83/AppiumPOC"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
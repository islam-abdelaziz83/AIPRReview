package org.AItestAutomationEngineer;

import org.AItestAutomationEngineer.SourceControl.PullRequestProcessor;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        PullRequestProcessor pullRequestProcessor = new PullRequestProcessor();
        try {
            pullRequestProcessor.processPullRequests("Breadfast/QA_Automation_Framework", 66);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
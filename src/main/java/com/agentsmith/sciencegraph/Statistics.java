package com.agentsmith.sciencegraph;

/**
 * Maintains a record of answer accuracy, and any problems encountered
 */
public class Statistics
{
    // only set during training, where our guess matches the provided answer
    public static int correct = 0;

    // only set during training, where our guess does not match provided answer
    public static int incorrect = 0;

    // keeps record of cases where we have insufficient understanding to reach an answer
    public static int unanswered = 0;

    public static int noQuestionConcepts = 0;
    public static int noAnswerConcepts = 0;

    // this is incremented when there are less than four possible answers to choose between
    // this is not a fatal error, but is a useful insight into the concept matching process
    public static int incompleteAnswerOptions = 0;


    public static int incorrectLimit = 10;

    public static void reset()
    {
        correct = 0;
        incorrect = 0;
        unanswered = 0;
        noQuestionConcepts = 0;
        noAnswerConcepts = 0;
        incompleteAnswerOptions = 0;
        incorrectLimit = 0;
    }

    public static String getSuccessRate() {
        if (correct + incorrect == 0) return "0";
        double rate = ((double)correct / (double)(correct + incorrect)) * 100.0;
        return String.format("%.1f", rate);
    }

    public static void print()
    {
        if (App.runMode != App.RunMode.VALIDATE)
        {
            System.out.println("\n*** ANSWER SUMMARY ***");
            System.out.println("Correct Answers: " + correct);
            System.out.println("Wrong Answers:   " + incorrect);
            System.out.println("Success Rate:    " + getSuccessRate() + "%");
        }
        System.out.println("\n*** ISSUES ***");
        System.out.println("Unanswered questions      : " + unanswered);
        System.out.println("No question concepts found: " + noQuestionConcepts);
        System.out.println("No answer concepts found  : " + noAnswerConcepts);
        System.out.println("Incomplete answer options : " + incompleteAnswerOptions);
        System.out.println();
    }
}

package com.agentsmith.sciencegraph;

import java.util.StringTokenizer;

/**
 * Represents a question: each consisting of id, question, correctAnswer and answerA, answerB, answerC, answerD
 */
public class Question
{
    // possibly also: complete the sentence, when there is no full stop or question mark, e.g. TRAIN700
    // and "least likely" - use highest rather than lowest score e.g. TRAIN731
    public enum QuestionType { UNCLASSIFIED, IS_OPINION, MISSING_WORD, COMPOSED_OF, NUMERIC, LEAST_LIKELY, HAS_PROPERTY, ALL_OPTION }

    public String id;

    public String text;

    public String correctAnswer;
    public int correctAnswerIndex;

    public String[] answers = new String[4];

    public QuestionType type;


    /** Constructor for the simple TSV format */
    public Question(StringTokenizer tok)
    {
        boolean answerProvided = (tok.countTokens() == 7);
        this.id = tok.nextToken();
        this.text = tok.nextToken();
        if (answerProvided)
        {
            correctAnswer = tok.nextToken();
            if (correctAnswer.equals("A")) this.correctAnswerIndex = 0;
            if (correctAnswer.equals("B")) this.correctAnswerIndex = 1;
            if (correctAnswer.equals("C")) this.correctAnswerIndex = 2;
            if (correctAnswer.equals("D")) this.correctAnswerIndex = 3;
        }
        answers[0] = tok.nextToken();
        answers[1] = tok.nextToken();
        answers[2] = tok.nextToken();
        answers[3] = tok.nextToken();
    }


    /** Constructor for the more elaborate CSV format */
    public Question(String id, String text, String[] options, String correct)
    {
        boolean answerProvided = (correct != null);
        this.id = id;
        this.text = text;
        if (answerProvided)
        {
            this.correctAnswer = correct;
            if (correctAnswer.equals("A")) this.correctAnswerIndex = 0;
            if (correctAnswer.equals("B")) this.correctAnswerIndex = 1;
            if (correctAnswer.equals("C")) this.correctAnswerIndex = 2;
            if (correctAnswer.equals("D")) this.correctAnswerIndex = 3;
        }
        this.answers = options;
    }



    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("[" + id + "] " + text + "\n");
        for (int i=0; i<answers.length; i++)
        {
            if (i == correctAnswerIndex) builder.append("**");
            builder.append("\t");
            builder.append(answers[i] + "\n");
        }
        return builder.toString();
    }

}

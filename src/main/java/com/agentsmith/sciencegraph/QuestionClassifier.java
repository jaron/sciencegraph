package com.agentsmith.sciencegraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Contains the logic to determine a question type, in order to apply alternative solution strategies
 */
public class QuestionClassifier
{
    /** Entry method to categorise a question to a type */
    public static void classify(Blackboard blackboard)
    {
        // TODO ultimately might want to use some kind of trained classifier
        blackboard.question.type = Question.QuestionType.UNCLASSIFIED;

        blackboard.linkProperties = detectProperties(blackboard);


        if (blackboard.isNumericQuestion())
            blackboard.question.type = Question.QuestionType.NUMERIC;
        else if (blackboard.questionConcepts.contains("opinion"))
            blackboard.question.type = Question.QuestionType.IS_OPINION;
        else if (checkForAllAboveAnswer(blackboard))
            blackboard.question.type = Question.QuestionType.ALL_OPTION;
        else if (blackboard.question.text.contains("___"))
        {
            if (blackboard.question.text.contains(" made from _"))
                blackboard.question.type = Question.QuestionType.COMPOSED_OF;
            else
                blackboard.question.type = Question.QuestionType.MISSING_WORD;
        }
        else if (blackboard.question.text.contains(" composed") || blackboard.question.text.contains(" composition of"))
        {
            // TODO might want to restrict this to the last phrase of the question
            blackboard.question.type = Question.QuestionType.COMPOSED_OF;
        }
        else if (blackboard.linkProperties.size() > 0) {
            blackboard.question.type = Question.QuestionType.HAS_PROPERTY;
        }
        else
        {
            int lastFullStop = blackboard.question.text.lastIndexOf('.') + 1;
            String fragment = blackboard.question.text.substring(lastFullStop);
            App.log("Question => " + fragment);

            // more efficient to process text using lookups than string searches
            HashSet<String> questionWords = new HashSet<String>();
            List<WordTag> fragmentTags = GraphQueryService.getInstance().identifyTags(fragment);
            for (WordTag tag : fragmentTags) questionWords.add(tag.text);

            if ((questionWords.contains("which") && questionWords.contains("not")) ||
                    (questionWords.contains("least") && questionWords.contains("likely")))
            {
                if (questionWords.contains("because")) {
                    // make an exception for questions in the form of which... because they do not ...
                }
                else
                    blackboard.question.type = Question.QuestionType.LEAST_LIKELY;
            }
        }
    }


    /** Determines if the question contains any linkwords that can be used for property lookups
      * For instance, the verb attach can be used to find object-[attach]-[?], with the results set
      * being compared to the possible answers for matches */
    public static List<String> detectProperties(Blackboard blackboard)
    {
        List<String> results = new ArrayList<String>();
        for (String concept: blackboard.questionConcepts)
        {
            boolean linkword = GraphQueryService.getInstance().isLinkword(concept);
            if (linkword) {
                App.log("Found linkword: " + concept);
                results.add(concept);
            }
        }
        return results;
    }


    public static boolean checkForAllAboveAnswer(Blackboard blackboard)
    {
        for (AnswerData answer : blackboard.answers)
        {
            String text = answer.text.toLowerCase();
            if (text.contains("all of ") && text.contains("above"))
                return true;
        }
        return false;
    }


}

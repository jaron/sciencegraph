package com.agentsmith.sciencegraph;

import java.util.ArrayList;
import java.util.List;

import com.agentsmith.sciencegraph.AnswerData.AnswerCode;
import org.apache.commons.lang3.StringUtils;

/**
 * Working information for a question
 */
public class Blackboard
{
    // a reference to the question being attempted
    public Question question;

    // the concepts we believe are significant to this question
    public List<String> questionConcepts = new ArrayList<String>();

    // the question tokenised into word, part-of-speech pairs
    public List<WordTag> questionTags;

    // the concepts we believe are significant in the multiple choice answers
    public List<AnswerData> answers = new ArrayList<AnswerData>();

    // the concepts we've found that can be used as bridges between concepts
    public List<String> linkProperties = new ArrayList<String>();


    public Blackboard(Question question) {
        this.question = question;
    }

    /** Adds a new question and determines the candidate concepts */
    public void addQuestion(List<WordTag> tags) {
        this.questionTags = tags;
        questionConcepts = TaggingService.findConcepts(questionTags);
    }

    /** Adds a new answer possibility to the blackboard */
    public void addAnswer(String text, List<WordTag> wordTags, AnswerCode code)
    {
        AnswerData answer = new AnswerData(text, wordTags, code);
        answers.add(answer);
    }


    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("\n[" + question.id + "] " + question.text + "\n");
        builder.append("QUESTION TYPE: \t" + question.type.toString() + "\n");
        // builder.append("QUESTION CONCEPTS: \t" + questionConcepts.toString() + "\n");
        builder.append("QUESTION CONCEPTS: \n");
        for (String concept : questionConcepts)
        {
            int degree = GraphQueryService.getInstance().getDegree(concept);
            builder.append(concept + " (" + degree + ") ");
        }

        builder.append("\nANSWERS:\n");
        for (AnswerData a : answers)
        {
            builder.append(a.getCode() +  " -> [");
            for (String concept : a.getConcepts())
            {
                int degree = GraphQueryService.getInstance().getDegree(concept);
                builder.append(concept + " (" + degree + ") ");
            }
            builder.append("]" + " SCORE: " + a.getScore() + "\n");
            for (int i=0; i < a.getQueries().size(); i++)
                builder.append("\t" + a.getQueries().get(i).substring(0,100) + "... = " + a.getResults().get(i)+ "\n");
        }
        return builder.toString();
    }


    public String getAnswer()
    {

        if (question.type == Question.QuestionType.LEAST_LIKELY)
            return getHighestAnswer();
        if (question.type == Question.QuestionType.MISSING_WORD && question.text.contains("EXCEPT"))
            return getHighestAnswer();
        return getLowestAnswer();
    }

    public String getLowestAnswer()
    {
        double best = 100;
        String code = null;
        for (AnswerData a : answers)
        {
            double score = a.getScore();
            if (score > 0 && score < best) {
                best = a.getScore();
                code = a.getCode();
            }
        }
        return code;
    }

    public String getHighestAnswer()
    {
        double best = 0;
        String code = null;
        for (AnswerData a : answers)
        {
            double score = a.getScore();
            if (score > 0 && score > best) {
                best = a.getScore();
                code = a.getCode();
            }
        }
        return code;
    }


    /** Returns the lowest scoring answers, may be more than one if they are tied */
    public List<AnswerData> getBestAnswers()
    {
        boolean lowerIsBetter = (question.type != Question.QuestionType.LEAST_LIKELY);
        double best = lowerIsBetter ? 1000 : 0;
        ArrayList<AnswerData> bestAnswers = new ArrayList<AnswerData>();
        for (AnswerData a : answers)
        {
            double score = a.getScore();
            if (score <= 0) continue;
            boolean isBetter = lowerIsBetter ? score < best : score > best;

            if (isBetter) {
                best = a.getScore();
                bestAnswers.clear();
                bestAnswers.add(a);
                //System.out.println("Best score now " + a.getScore() + " " + best.size() + " entries");
            }
            else if (score == best) {
                bestAnswers.add(a);
                // System.out.println("Another score with " + a.getScore() + " " + best.size() + " entries");
            }
        }
        return bestAnswers;
    }

    public boolean isNumericQuestion()
    {
        for (AnswerData a : answers)
        {
            List<WordTag> tags = a.getTags();
            if (tags == null || tags.size() != 1) return false;
            if (!("CD".equals(tags.get(0).tag) || StringUtils.isNumeric(tags.get(0).tag))) return false;
        }
        return true;
    }
}

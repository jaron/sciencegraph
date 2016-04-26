package com.agentsmith.sciencegraph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Represents the working data stored for each potential answer
 */
public class AnswerData
{
    public enum AnswerCode { A, B, C, D };

    public String text;

    // the identifier that determines whether this answer is called a,b,c or d
    private AnswerCode code;

    private List<String> queries = new ArrayList<String>();

    // the word and tags representing the text of this answer option
    private List<WordTag> tags = new ArrayList<WordTag>();

    // the candidate concepts for this answer option
    private List<String> concepts = new ArrayList<String>();

    // the results for each of the queries
    private List<Integer> results = new ArrayList<Integer>();

    // the score used to determine this answer's ranking
    private double score = 0;


    public AnswerData(String text, List<WordTag> tags, AnswerCode code)
    {
        this.text = text;
        this.tags = tags;
        this.code = code;
        concepts = TaggingService.findConcepts(this.tags);
    }

    /** Returns the candidate concepts for this answer option */
    public List<String> getConcepts() {
        return concepts;
    }


    /** Count how many times a particular part-of-speech occurs in an answer */
    public int countTokens(String token)
    {
        int count = 0;
        Iterator<WordTag> it = tags.iterator();
        while (it.hasNext()) {
            WordTag w = it.next();
            if (w.tag.equals(token)) count++;
        }
        return count;
    }


    public String getCode() {
        return code.name();
    }

    public List<String> getQueries() {
        return queries;
    }

    public List<Integer> getResults() {
        return results;
    }

    public List<WordTag> getTags() { return tags; }

    public double getScore() {
        return score;
    }

    public void add(String query) {
        queries.add(query);
        results.add(0);
    }

    public void addResult(int position, Integer result) {
        results.set(position, result);
    }

    public void setScore(double value) {
        this.score = value;
    }


    public double getAverageDegreeOfConcepts()
    {
        double sum = 0;
        for (String concept : getConcepts()) {
            sum += GraphQueryService.getInstance().getDegree(concept);
        }
        return sum / (double)getConcepts().size();
    }

    public Double getAnswerAsNumber()
    {
        if (tags == null || tags.size() != 1) return null;
        if (!("CD".equals(tags.get(0).tag))) return null;
        String value = tags.get(0).text;
        if (StringUtils.isNumeric(value)) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                App.log("Unable to parse number " + value);
                return null;
            }
        }
        else
            return TaggingService.wordToNumber(value);
    }
}

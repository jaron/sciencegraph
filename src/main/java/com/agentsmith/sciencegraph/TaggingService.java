package com.agentsmith.sciencegraph;

import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.*;

/**
 * This will be the entry class for the Part of Speech Tagger
 */
public class TaggingService
{
    // the Stanford CoreNLP lemmatizer
    private static NLPService nlp;

    private static HashSet<String> abbreviations = new HashSet<String>();
    private static HashSet<String> ngramTags = new HashSet<String>();;
    private static HashSet<String> excludeVerbs = new HashSet<String>();
    public static HashSet<String> excludedQuestionWords = new HashSet<String>();

    private static String[] abbreviationList = new String[] { "XX", "XY", "pH" };
    private static String[] ngramTagList = new String[] { "NN", "NNS", "NNP", "NNPS", "JJ", "VB", "VBG", "VBZ", "VBN" };
    private static String[] excludeVerbList = new String[] { "is", "are", "was", "were", "has", "have", "be", "been", "based" };

    private static final String[] numNames = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
            "eighteen", "nineteen", "twenty"
    };

    private static String[] excludeQuestionWordList = new String[] { "two", "majority", "primarily", "mainly", "mostly", "large", "new", "usually" };


    public TaggingService()
    {

        InputStream modelIn = null;
        InputStream tokenFile = null;
        try
        {
            nlp = new NLPService();
            abbreviations.addAll(Arrays.asList(abbreviationList));
            ngramTags.addAll(Arrays.asList(ngramTagList));
            excludeVerbs.addAll(Arrays.asList(excludeVerbList));
            excludedQuestionWords.addAll(Arrays.asList(excludeQuestionWordList));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            if (modelIn != null)
                try { modelIn.close(); } catch (Exception e) {}
            if (tokenFile != null)
                try { tokenFile.close(); } catch (Exception e) {}
        }
    }

    public List<WordTag> identifyTags(String sentence)
    {
        if (StringUtils.isEmpty(sentence)) return new ArrayList<WordTag>();

        sentence = sentence.replace('_', ' '); // remove missing word dashes
        sentence = sentence.replace('"', ' '); // remove quotes
        sentence = StringUtils.remove(sentence, '.');

        List<WordTag> tags = nlp.getPartsOfSpeech(sentence);
        App.log("Tagged: " + tags.toString());

        for (WordTag w : tags) {
            if (abbreviations.contains(w.text)) w.tag = "NNP";
        }
        return tags;
    }


    public List<WordTag> identifyQuestionTags(String sentence)
    {
        sentence = sentence.replace('_', ' '); // remove missing word dashes
        sentence = sentence.replace('"', ' '); // remove quotes
        sentence = StringUtils.remove(sentence, '.');

        List<WordTag> tags = nlp.getPartsOfSpeech(sentence);
        List<WordTag> results = new ArrayList<WordTag>();
        for (WordTag w : tags) {
            if (!excludedQuestionWords.contains(w.text)) results.add(w);
            if (abbreviations.contains(w.text)) w.tag = "NNP";
        }

        App.log("Tagged: " + results.toString());
        return results;
    }


    /** Determines the candidate concepts within a list of word tags */
    public static List<String> findConcepts(List<WordTag> tags)
    {
        List<String> results = new ArrayList<String>();
        WordTag preceding = null;
        for (int i=0; i<tags.size(); i++)
        {
            WordTag w = tags.get(i);
            w.text = w.text.toLowerCase().replace('-', '_');

            if (w.tag.equals("NNP") || w.tag.equals("NN") || w.tag.equals("NNS") || w.tag.equals("NNPS") || w.tag.equals("RP"))
            {
                boolean possibleNgram = (preceding != null && ngramTags.contains(preceding.tag));
                // if the preceding word is one of the allowed ngram constituents, check to see if we can create a 2-gram
                if (possibleNgram)
                {
                    String ngram = (preceding.text + "_" + w.text).toLowerCase();
                    if (!GraphQueryService.getInstance().exists(ngram) && w.tag.equals("NNS")) {
                        w.text = toSingular(w.text);
                        ngram = (preceding.text + "_" + w.text).toLowerCase();
                    }

                    // System.out.println("CHECK >> " + ngram);
                    boolean ngramExists = GraphQueryService.getInstance().exists(ngram);
                    if (!ngramExists) {
                        ngram = (w.text + "_" + preceding.text).toLowerCase();
                        ngramExists = GraphQueryService.getInstance().exists(ngram);
                    }

                    if (ngramExists && !results.contains(ngram)) {
                        App.log("ngram " + ngram + " exists");
                        results.add(ngram);
                        results.remove(preceding.text);
                        w.text = ngram;
                    }
                    else {
                        // if the ngram doesn't exist, just add the word
                        if (w.tag.equals("NNS") || (w.tag.equals("NNP") && w.text.endsWith("s")))
                            w.text = toSingular(w.text);
                        boolean existsInGraph = GraphQueryService.getInstance().exists(w.text);
                        if (existsInGraph && !results.contains(w.text)) results.add(w.text);
                    }
                }
                else
                {
                    // System.out.println("CHECK >> " + w.text);
                    // standard check for non-ngram nouns, see if term exists in the knowledge base
                    boolean existsInGraph = GraphQueryService.getInstance().exists(w.text);
                    if (existsInGraph && !results.contains(w.text))
                        results.add(w.text);
                    else {
                        // if the word is a plural, try converting to singular form
                        if (w.tag.equals("NNS") || (w.tag.equals("NNP") && w.text.endsWith("s")))
                            w.text = toSingular(w.text);
                        existsInGraph = GraphQueryService.getInstance().exists(w.text);
                        if (existsInGraph && !results.contains(w.text)) results.add(w.text);
                    }
                }
                preceding = w;
            }
            else if (w.tag.equals("JJ") || w.tag.equals("JJR"))
            {
                // look for adverb ngrams e.g. environmentally_friendly
                if (preceding != null && (preceding.tag.equals("RB")))
                {
                    String ngram = (preceding.text + "_" + w.text).toLowerCase();
                    // System.out.println("CHECK >> " + ngram);
                    boolean ngramExists = GraphQueryService.getInstance().exists(ngram);
                    if (!ngramExists) {
                        ngram = (w.text + "_" + preceding.text).toLowerCase();
                        ngramExists = GraphQueryService.getInstance().exists(ngram);
                    }

                    if (ngramExists && !results.contains(ngram)) {
                        App.log("ngram " + ngram + " exists");
                        results.add(ngram);
                        results.remove(preceding.text);
                    }
                    preceding = null;
                    continue;
                }

                // if we're going to use this as an n-gram, we store the non-stemmed form
                preceding = w;
                String word = w.text;

                // some VBN verbs are tagged as adjectives, we want to stem them
                if (w.text.endsWith("ed") || (w.text.endsWith("ing")) || w.text.endsWith("nt"))
                {
                    word = nlp.lemmatize(w.text);
                    if (GraphQueryService.getInstance().exists(word) && !results.contains(word)) {
                        results.add(word);
                        continue;
                    }
                }

                if (GraphQueryService.getInstance().exists(w.text) && !results.contains(w.text)) {
                    results.add(w.text);
                }
            }
            else if (w.tag.equals("IN") || w.tag.equals("TO") || w.tag.equals("RP"))
            {
                // some key words are mistagged
                if (w.text.equals("amino")) {
                    w.tag = "NN";
                    preceding = w;
                    continue;
                }

                if (!(preceding != null && ngramTags.contains(preceding.tag))) continue;
                if (i == 0 || i == tags.size()-1) continue;
                WordTag nextTag = tags.get(i+1);
                WordTag lastTag = tags.get(i-1);

                // exclude pronouns and determiners such as your or the
                if ((nextTag.tag.startsWith("PRP") || nextTag.tag.startsWith("DT")) && i < tags.size()-2) {
                    nextTag = tags.get(i + 2);
                    i++;
                }

                // attempt to create a 3-gram
                if (ngramTags.contains(nextTag.tag))
                {
                    if (nextTag.tag.equals("NNS")) nextTag.text = toSingular(nextTag.text);
                    String composite = lastTag.text + "_" + w.text + "_" + nextTag.text;
                    boolean existsInGraph = GraphQueryService.getInstance().exists(composite);
                    if (existsInGraph && !results.contains(composite)) {
                        App.log("Composite 3-gram: " + composite);
                        results.add(composite);
                        results.remove(preceding.text);
                        i++;
                        preceding = null;
                    }
                }
            }
            else if (w.tag.startsWith("VB"))
            {
                // System.out.println("CHECK >> " + w.text);
                if (excludeVerbs.contains(w.text)) continue;

                boolean possibleNgram = (preceding != null && ngramTags.contains(preceding.tag)); // "JJ".equalsIgnoreCase(preceding.tag));
                // if the preceding word is also a noun (NNP or NN) or adjective (JJ), check to see if we can create a 2-gram
                boolean ngramFound = false;
                if (possibleNgram)
                {
                    String ngram = (preceding.text + "_" + w.text).toLowerCase();
                    // System.out.println("CHECK >> " + ngram);
                    ngramFound = tryNgram(ngram, preceding, results);
                    if (!ngramFound)
                    {
                        // if the ngram doesn't exist, try again using the infinitive form of the verb
                        w.text = nlp.lemmatize(w.text);
                        ngram = (preceding.text + "_" + w.text).toLowerCase();
                        ngramFound = tryNgram(ngram, preceding, results);
                    }
                }
                if (!ngramFound)
                {
                    // System.out.println("CHECK >> " + w.text);
                    // if stemming creates something that isn't likely to be matched, may want to put the full verb form into KB
                    if (GraphQueryService.getInstance().exists(w.text) && !results.contains(w.text)) {
                        w.tag = "NN";
                        results.add(w.text);
                    }
                    else {
                        w.text = nlp.lemmatize(w.text);
                        // System.out.println("LEMMA >> " + w.text);
                        if (GraphQueryService.getInstance().exists(w.text) && !results.contains(w.text)) {
                            results.add(w.text);
                        }
                    }
                }
                preceding = w;
            }
            else if (w.tag.equals("RB"))
            {
                // some words ending in -re are mistagged as adverbs, if it's not in the KB though, we'll exclude
                boolean existsInGraph = GraphQueryService.getInstance().exists(w.text);
                if (existsInGraph && !results.contains(w.text)) results.add(w.text);
                w.tag = "NN";
                preceding = w;
            }
            else if (w.tag.equals("CD"))
            {
                // some CDs are actually mistagged proper nouns
                if (!StringUtils.isNumeric(w.text)) w.tag = "NN";
                preceding = w;
                boolean existsInGraph = GraphQueryService.getInstance().exists(w.text);
                if (existsInGraph && !results.contains(w.text)) {
                    results.add(w.text);
                }
            }
            else if (w.tag.equals("DT") || (w.tag.equals("POS")))
            {
                // do nothing
            }
            else if (w.tag.equals("EX"))
            {
                if (w.text.endsWith("tats")) w.text = toSingular(w.text);
                if (w.text.endsWith("tat") || w.text.endsWith("mal")) {
                   boolean existsInGraph = GraphQueryService.getInstance().exists(w.text);
                   if (existsInGraph && !results.contains(w.text)) results.add(w.text);
               }
            }
            else
                preceding = null;
        }

        List<String> duplicateTerms = identifyDuplicates(results);
        for (String d : duplicateTerms) results.remove(d);
        return results;
    }


    protected static boolean tryNgram(String ngram, WordTag preceding, List<String> results)
    {
        boolean ngramExists = GraphQueryService.getInstance().exists(ngram);
        if (ngramExists && !results.contains(ngram))
        {
            App.log("ngram " + ngram + " exists");
            results.add(ngram);
            results.remove(preceding.text);
            return true;
        }
        return false;
    }


    /** Identifies any single terms that might have been added as n-grams */
    private static List<String> identifyDuplicates(List<String> results)
    {
        List<String> duplicates = new ArrayList<String>();
        for (int i=0; i<results.size(); i++)
        {
            String s = results.get(i);
            if (s.indexOf('_') > 0)
            {
                String[] elements = StringUtils.split(s, '_');
                if (elements.length == 2 && results.contains(elements[0]) && results.contains(elements[1]))
                {
                    duplicates.add(elements[0]);
                    duplicates.add(elements[1]);
                }
                else if (elements.length == 3 && results.contains(elements[0]) && results.contains(elements[2])) {
                    duplicates.add(elements[0]);
                    duplicates.add(elements[2]);
                }
            }
        }
        // System.out.println("DUPLICATES = " + duplicateTerms.toString());
        return duplicates;
    }


    /** Returns all the potentially significant terms of this sentence */
    public static List<String> findWords(List<WordTag> wordTags)
    {
        HashSet<String> set = new HashSet<String>();
        for (WordTag w : wordTags)
        {
            // split noun ngrams into their constituent parts
            if (w.tag.startsWith("NN") && w.text.contains("_")) {
                String[] parts = StringUtils.split(w.text, "_");
                for (String s : parts) set.add(s);
                continue;
            }

            if (!StringUtils.isAlphanumeric(w.text)) continue; // skip punctuation
            if ("CC".equals(w.tag)) continue; // skip Conjunction
            if ("IN".equals(w.tag)) continue; // skip Preposition
            if ("DT".equals(w.tag)) continue; // skip Determiner
            if ("PRP".equals(w.tag) || "PRP$".equals(w.tag)) continue; // skip Personal and Possessive pronouns
            // if ("RB".equals(w.tag) && w.text.endsWith("ly")) continue; // skip adverbs
            if ("RP".equals(w.tag)) continue; // skip particles e.g up, down
            if ("TO".equals(w.tag)) continue; // skip To
            if ("WP".equals(w.tag) || "WP$".equals(w.tag)) continue; // skip Wh-pronouns
            if ("WRB".equals(w.tag) || "WDT".equals(w.tag)) continue; // skip Wh-adverb and Wh-determiner, e.g. when

            if ("JJ".equals(w.tag)) {
                if ("such".equals(w.text)) continue;
            }

            if ((w.tag).startsWith("VB")) {
                if (excludeVerbs.contains(w.text)) continue;
                // then lemmatize the verb for consistency
                w.text = nlp.lemmatize(w.text);
            }

            if ("NNS".equals(w.tag))
                w.text = nlp.lemmatize(w.text);

            set.add(w.text);
        }

        List<String> results = new ArrayList<String>(set);
        return results;
    }

    /** Wrapper for when a sentence hasn't been tagged yet */
    public List<String> findWords(String sentence) {
        List<WordTag> wordTags = identifyTags(sentence);
        return findWords(wordTags);
    }

    /* public static String stem(String word)
    {
        String output = stemmer.stem(word);
        return output;
    }*/


    // plural nouns to singular form
    public static String toSingular(String word)
    {
        return nlp.lemmatize(word);
    }

    // avoid turning words like gas, crisis and bacillus into singular forms
    public static boolean isIrregularEnding(char c) {
        return "AIOUaiou".indexOf(c) != -1;
    }


    /** Finds CD tags, representing numbers */
    public static List<Double> findNumbers(List<WordTag> tags)
    {
        List<Double> results = new ArrayList<Double>();
        Iterator<WordTag> it = tags.iterator();
        while (it.hasNext())
        {
            WordTag w = it.next();
            w.text = w.text.toLowerCase();
            if (w.tag.equals("CD"))
            {
                if (StringUtils.isNumeric(w.text))
                {
                    try {
                        results.add(Double.parseDouble(w.text));
                    } catch (NumberFormatException e) {
                        App.log("Unable to parse number " + w.text);
                    }
                }
                else {
                    Double d = wordToNumber(w.text);
                    if (d != null) results.add(d);
                }
            }
        }
        return results;
    }

    /** Finds any type of tags, regardless of whether they exist in the knowledge base */
    public static List<String> findExtraWords(List<WordTag> tags, String tagType)
    {
        List<String> results = new ArrayList<String>();
        Iterator<WordTag> it = tags.iterator();
        while (it.hasNext())
        {
            WordTag w = it.next();
            w.text = w.text.toLowerCase();
            if (w.tag.equals(tagType)) results.add(w.text);
        }
        return results;
    }


    public static Double wordToNumber(String word)
    {
        for (int i=0; i< numNames.length; i++) {
            if (numNames[i].equalsIgnoreCase(word)) {
                return Double.valueOf((i));
            }
        }
        return null;
    }
}

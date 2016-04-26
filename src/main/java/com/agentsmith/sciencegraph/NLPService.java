package com.agentsmith.sciencegraph;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Wrapper for the Stanford CoreNLP service, through which we can access the tagger and lemmatizer
 */
public class NLPService
{
    protected static StanfordCoreNLP pipeline;

    public NLPService()
    {
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma");
        pipeline = new StanfordCoreNLP(props);
    }


    /** For finds lemmas for a text fragment */
    public List<String> lemmatizeText(String documentText)
    {
        List<String> lemmas = new LinkedList<String>();
        // Create an empty Annotation just with the given text
        Annotation document = new Annotation(documentText);
        // run all Annotators on this text
        pipeline.annotate(document);
        // Iterate over all of the sentences found
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
            // Iterate over all tokens in a sentence
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // Retrieve and add the lemma for each word into the list of lemmas
                lemmas.add(token.get(CoreAnnotations.LemmaAnnotation.class));
            }
        }
        return lemmas;
    }

    /** For finding the lemma of a single word */
    public String lemmatize(String word)
    {
        // Create an empty Annotation just with the given text
        Annotation document = new Annotation(word);
        // run all Annotators on this text
        pipeline.annotate(document);
        // Iterate over all of the sentences found
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
            // Iterate over all tokens in a sentence
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // Retrieve and add the lemma for each word into the list of lemmas
                return token.get(CoreAnnotations.LemmaAnnotation.class);
            }
        }
        return word;
    }


    /** For finds the parts of speech for a text fragment */
    public List<WordTag> getPartsOfSpeech(String documentText)
    {
        List<WordTag> results = new LinkedList<WordTag>();

        // Create an empty Annotation just with the given text
        Annotation document = new Annotation(documentText);
        // run all Annotators on this text
        pipeline.annotate(document);
        // Iterate over all of the sentences found
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
            // Iterate over all tokens in a sentence
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // Retrieve and add the pos for each word

                String pos  = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                String text = token.get(CoreAnnotations.TextAnnotation.class);
                WordTag tag = new WordTag(text, pos);
                results.add(tag);
            }
        }
        return results;
    }


}

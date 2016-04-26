package com.agentsmith.sciencegraph;

import org.junit.Test;

import java.util.List;


/**
 * Tests various NLP operations
 */
public class NLPTest
{
    @Test
    public void testTagging()
    {
        NLPService nlp = new NLPService();
        String input = "In which way is the orbit of a comet different from the orbit of Earth?";
        List<WordTag> output = nlp.getPartsOfSpeech(input);
        System.out.println(">> " + output.toString());
    }


    @Test
    public void testLemmatizing() throws Exception
    {
        NLPService lemmatizer = new NLPService();

        String input = new String("bacillus");
        String output = lemmatizer.lemmatize(input);
        System.out.println(input + " >> " + output);

        input = "collided";
        output = lemmatizer.lemmatize(input);
        System.out.println(input + " >> " + output);
    }

}

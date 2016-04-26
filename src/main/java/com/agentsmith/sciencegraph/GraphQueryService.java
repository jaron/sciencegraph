package com.agentsmith.sciencegraph;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.neo4j.rest.graphdb.RestAPI;
import org.neo4j.rest.graphdb.RestAPIFacade;
import org.neo4j.rest.graphdb.query.RestCypherQueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;

import java.util.*;

import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * Maintains a REST connection to the graph database and provides methods that run graph queries
 */
public class GraphQueryService
{
    private static final String SERVER_ROOT_URI = "http://localhost:7474/db/data/";

    private static RestCypherQueryEngine queryEngine;

    private static GraphQueryService service = new GraphQueryService();

    private static TaggingService tagger = new TaggingService();


    public GraphQueryService()
    {
        WebResource resource = Client.create().resource(SERVER_ROOT_URI);
        ClientResponse response = resource.get(ClientResponse.class);

        try {
            System.out.println(String.format("GET on [%s], status code [%d]", SERVER_ROOT_URI, response.getStatus()));
            response.close();

            final RestAPI api = new RestAPIFacade(SERVER_ROOT_URI);
            System.out.println("API created");
            queryEngine = new RestCypherQueryEngine(api);
            System.out.println("engine created");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GraphQueryService getInstance( ) {
        return service;
    }

    /** Count the number of nodes in the knowledge base */
    public long countNodes()
    {
        String query = "start n=node(*) match n return count(n) as result";
        final QueryResult<Map<String, Object>> result = queryEngine.query(query, Collections.EMPTY_MAP);
        if (result == null) return 0;
        return ((Number) result.iterator().next().get("result")).longValue();
    }

    /** Determine whether a named node exists in the knowledge graph */
    public boolean exists(String node)
    {
        // using labels speeds this query up enormously
        String query = "match (n:Concept {name : \"/c/en/" + node + "\"}) return count(n) as result";
        final QueryResult<Map<String, Object>> result = queryEngine.query(query, Collections.EMPTY_MAP);
        if (result == null) return false;
        return ((Number) result.iterator().next().get("result")).longValue() == 1;
    }

    /** Determines if the concept is a linkword */
    public boolean isLinkword(String node)
    {
        // using labels speeds this query up enormously
        String query = "match (n:Concept {name : \"/c/en/" + node + "\"}) return n.linkword as result";
        final QueryResult<Map<String, Object>> result = queryEngine.query(query, Collections.EMPTY_MAP);
        Map<String, Object> m = result.iterator().next();
        if (m == null || m.get("result") == null) return false;
        String s = m.get("result").toString();
        return s.equals("true");
    }


    /** Fetches any definitions both from a named node and any of its synonyms */
    public String getDefinitionsUsingSynonyms(String node)
    {
        String query = "match (n:Concept {name : \"/c/en/" + node + "\"}), p= (s)-[r:SYNONYM*0..1]-(n) with n,s as x return collect(x.definition) AS definitions";
        final QueryResult<Map<String, Object>> result = queryEngine.query(query, Collections.EMPTY_MAP);
        Map<String, Object> m = result.iterator().next();
        if (m == null || m.get("definitions") == null) return null;

        ArrayList<String> defs = (ArrayList<String>)m.get("definitions");
        if (defs.size() == 0) return null;

        StringBuilder builder = new StringBuilder();
        for (String d : defs)
            builder.append(d).append(" ");
        return builder.toString();
    }


    /** Fetches the nodes that are connected to the subject node by a named link property */
    public List<String> getProperties(String node, String link)
    {
        String query = "match (n:Concept {name : \"/c/en/" + node + "\"}), ((n)-[r:HAS_PROPERTY { name : \"" + link + "\"} ]-(a)) return a.name as results";
        final QueryResult<Map<String, Object>> result = queryEngine.query(query, Collections.EMPTY_MAP);

        ArrayList<String> results = new ArrayList<String>();
        if (result != null && result.iterator().hasNext()) {
            Map<String, Object> m = result.iterator().next();
            if (m != null && m.get("results") != null)
                if (m.get("results") instanceof String)
                    results.add((String)m.get("results"));
                else if (m.get("results") instanceof ArrayList)
                    results = (ArrayList<String>) m.get("results");
        }
        return results;
    }


    public int getDegree(String concept)
    {
        String query = "match (n:Concept {name : \"/c/en/" + concept + "\"}), ((n)--()) RETURN count(*) as result";
        return executeIntegerValueQuery(query);
    }


    /** Obtains definitions from the knowledge base and then identifies the concepts within them */
    public List<String> getDefinitions(List<String> questionConcepts)
    {
        HashSet<String> results = new HashSet<String>();
        if (questionConcepts == null || questionConcepts.size() == 0) return null;

        for (String concept : questionConcepts)
        {
            String definition = getDefinitionsUsingSynonyms(concept);
            if (definition == null) {
                App.log("No definition for: " + concept);
                continue;
            }
            App.log("Definition of: " + concept + " = " + definition);

            List<String> words = tagger.findWords(definition);
            results.addAll(words);
        }
        return new ArrayList<String>(results);
    }


    /** Executes a Cypher query that returns a single integer result */
    public Integer executeIntegerValueQuery(String query)
    {
        QueryResult<Map<String, Object>> result = null;
        try {
            result = queryEngine.query(query, null);
        } catch (Exception e) {
            System.out.print("Problem whilst executing: " + query);
            System.exit(1);
        }
        Integer value = 0;
        Iterator it = result.iterator();
        if (it.hasNext()) value = ((Integer) result.iterator().next().get("result"));
        // System.out.println((value == 0 ? "NONE" : value));
        return value;
    }


    public void identifyQuestionConcepts(Blackboard blackboard)
    {
        // scan through the question text, identify nouns and phrases
        List<WordTag> words = tagger.identifyQuestionTags(blackboard.question.text);
        blackboard.addQuestion(words);
    }

    public void identifyAnswerConcepts(Blackboard blackboard)
    {
        // scan through the text of each answer, identifying what we consider to be the most significant noun or phrase

        boolean singleWordAnswers = true;

        List<String> answerConcepts = new ArrayList<String>();
        for (int i=0; i<blackboard.question.answers.length; i++)
        {
            String text = blackboard.question.answers[i];
            List<WordTag> wordTags = tagger.identifyTags(text);
            blackboard.addAnswer(text, wordTags, AnswerData.AnswerCode.values()[i]);
            if (wordTags.size() != 1) singleWordAnswers = false;
        }

        // TODO add further resolution steps, so we can find the best knowledge base match and discard what can't be resolved
        if (singleWordAnswers) {
            specialiseAnswers(blackboard);
        }
    }

    /** Attempt to refine answers to more specific terms, experimental code for questions like TRAIN-100120 */
    public void specialiseAnswers(Blackboard blackboard)
    {
        String qualifier = null;
        boolean typeQuestion = false;
        for (WordTag w : blackboard.questionTags)
        {
            if (!w.tag.startsWith("NN")) continue;
            if (w.text.equals("type")) typeQuestion = true;
            else {
                qualifier = w.text;
                break;
            }
        }

        if (qualifier == null || qualifier.contains("_")) return;
        if (!typeQuestion) return;
        App.log("Potential specialisation: " + qualifier);


        for (AnswerData a : blackboard.answers)
        {
            if (a.getConcepts() == null || a.getConcepts().size() == 0) continue;
            String value = a.getConcepts().get(0);
            String combo = value + "_" + qualifier;
            if (exists(combo)) {
                App.log("Updating " + value + " to " + combo);
                a.getConcepts().set(0, combo);
            }
        }
    }


    // the tagger instance is accessed through this service
    public List<WordTag> identifyTags(String sentence) {
        return tagger.identifyTags(sentence);
    }

    public List<String> findWords(String sentence) {
        return tagger.findWords(sentence);
    }

}

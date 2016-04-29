package com.agentsmith.sciencegraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import com.agentsmith.sciencegraph.Question.QuestionType;
import org.apache.commons.lang.StringUtils;
import org.neo4j.graphdb.RelationshipType;

/**
 * This contains the logic used for problem solving. Each problem is solved declaratively, using Neo4j's internal
 * Cypher query language, with the results being returned in Java data structures.  
 */

public class ProblemSolver
{
	enum RankMetric { SHORTEST_PATH, AVERAGE_PATH }

	private static RankMetric metric = RankMetric.AVERAGE_PATH;

	public enum RelationTypes implements RelationshipType { MADE_OF, PART_OF }


	public ProblemSolver() {}

	private static final String DEFAULT_ANSWER = "D";


	/** This is the top-level description of the problem solving process */
	public static String solve(Question question)
	{
		// the blackboard contains the intermediate results of problem solving
		Blackboard blackboard = new Blackboard(question);

		// STEP 1 - find question concepts, initially by just stepping through each token and seeing if there's a matching node
		GraphQueryService.getInstance().identifyQuestionConcepts(blackboard);

		// TODO - see if any classification might determine the question type, to support generic answer tasks
		// TODO - might also be an idea to create gazetteer properties for certain concepts, e.g. field: biochemistry,
		// TODO - this could provide heuristic boosts for answers who belong to the same subcategory

		// STEP 2 - do the same for the answers
		GraphQueryService.getInstance().identifyAnswerConcepts(blackboard);

		if (!haveSufficientData(blackboard)) return DEFAULT_ANSWER;

		// STEP 3 - classify the question type, so we can choose a more specific type of query
		QuestionClassifier.classify(blackboard);

		// STEP 4 - invoke most appropriate solving algorithm
		if (blackboard.question.type == QuestionType.IS_OPINION)
		{
			// score based on which answer has the most RBR (Adverb, comparative and superlative) tokens
			List<String> tokens = new ArrayList<String>();
			tokens.add("JJR");
			scoreByPosTokens(blackboard, tokens, false);
		}
		else if (blackboard.question.type == QuestionType.MISSING_WORD)
		{
			// TODO lookup one-line definition of question concept and compare to options in the answers
			// for now, these are currently answered by determining which answer has the most known concepts

			boolean ok = scoreByDefinition(blackboard);
			if (!ok) scoreByAnswerConcepts(blackboard);
		}
		else if (blackboard.question.type == QuestionType.COMPOSED_OF)
		{
			for (AnswerData answer : blackboard.answers) answer.setScore(100);

			// consider only direct relations pertaining to composition of a concept
			ProblemSolver.scoreByRelations(blackboard, RelationTypes.MADE_OF);
			ProblemSolver.scoreByRelations(blackboard, RelationTypes.PART_OF);
			boolean answered = false;
			for (AnswerData answer : blackboard.answers)
				if (answer.getScore() != 100) answered = true;

			if (!answered) blackboard.question.type = QuestionType.UNCLASSIFIED;
		}
		else if (blackboard.question.type == QuestionType.NUMERIC)
		{
			boolean ok = ProblemSolver.findNumericAnswer(blackboard);
			if (!ok) ProblemSolver.scoreByAnswerConcepts(blackboard);
		}
		else if (blackboard.question.type == QuestionType.LEAST_LIKELY)
		{
			formulateQueries(blackboard);
			evaluateQueries(blackboard);
			scoreResults(blackboard);
			weightResults(blackboard, false);
		}
		else if (blackboard.question.type == QuestionType.HAS_PROPERTY)
		{
			boolean ok = scoreByProperties(blackboard);
			if (!ok) blackboard.question.type = QuestionType.UNCLASSIFIED;
		}
		else if (blackboard.question.type == QuestionType.ALL_OPTION)
		{
			scoreAllAboveQuestion(blackboard);
		}

		// the default problem solving behaviour
		if (blackboard.question.type == QuestionType.UNCLASSIFIED)
		{
			// formulate graphDb query based on problem text
			ProblemSolver.formulateQueries(blackboard);

			// for each possible query, run it, with higher scores given to shorter, better paths
			ProblemSolver.evaluateQueries(blackboard);
			// STEP 5 - process the scores to determine the "best" answer
			// this might be the shortest possible path of all queries, or the average path of all queries or some other insight entirely
			// IDEA: the scoring process might not give a definitive answer, but instead just rule one or two of the options out
			// this will increase the chances of a random guess being correct
			ProblemSolver.scoreResults(blackboard);

			ProblemSolver.weightResults(blackboard, true);

			// if we can't derive an answer by graph search, try a few alternatives
			if (blackboard.getAnswer() == null) {
				App.log("Graph search returns no answer - attempting alternative solution");
				boolean ok = ProblemSolver.scoreByDefinition(blackboard);
				if (!ok) ProblemSolver.scoreByAnswerConcepts(blackboard);
			}
		}

		// dump the contents of the blackboard to the console
		App.log(blackboard.toString());

		if (blackboard.getAnswer() == null)
		{
			if (App.runOnce) System.out.println("Warning: unanswered question " + question.id);
			if (App.runMode == App.RunMode.TRAIN) {
				File file = new File("unanswered.tsv");
				writeQuestion(file, blackboard.question);
			}
			Statistics.unanswered++;
			return DEFAULT_ANSWER;
		}

		// update correct/incorrect statistics to calculate effectiveness
		if (question.correctAnswer != null) updateStats(question, blackboard.getAnswer());

		// finally, return the answer A,B,C or D
		return blackboard.getAnswer();
	}


	/** Quality control - to ensure we have sufficient data to answer questions */
	public static boolean haveSufficientData(Blackboard blackboard)
	{
		if (blackboard.questionConcepts.size() < 1) {
			App.log("Warning: unable to identify any question concepts in question " + blackboard.question.id);
			Statistics.noQuestionConcepts++;
			return false;
		}

		if (blackboard.answers.size() == 0) {
			App.log("Warning: unable to identify any answer concepts in question " + blackboard.question.id);
			Statistics.noAnswerConcepts++;
			return false;
		}

		if (blackboard.answers.size() < 4) {
			if (App.runOnce) System.out.println("Warning: only able to identify "+ blackboard.answers.size() + " answer concepts in question " + blackboard.question.id);
			Statistics.incompleteAnswerOptions++;
			// TODO where answer concepts <= 2, might want to use an alternative understanding algorithm, as some answers don't contain many nouns e.g. qId 2243
		}
		return true;
	}


	/** Generates queries to find connections between the identified concepts */
	public static void formulateQueries(Blackboard blackboard)
	{
		// only need to create queries for some types of questions
		if (!(blackboard.question.type == QuestionType.UNCLASSIFIED || blackboard.question.type == QuestionType.LEAST_LIKELY)) return;

		// a non-directional shortest path query
		String baseQuery = "MATCH (source:Concept { name:\"/c/en/{1}\" }), (dest:Concept { name:\"/c/en/{2}\" }), p = shortestPath((source)-[r*]-(dest)) RETURN length(p) as result";

		for (AnswerData answer : blackboard.answers)
		{
			if (answer.getConcepts() == null || answer.getConcepts().size() == 0) continue;
			for (String qConcept : blackboard.questionConcepts)
			{
				for (String a : answer.getConcepts()) {
					if (a.equals(qConcept)) continue;
					String thisQuery = baseQuery.replace("{1}", a.replace(' ', '_'));
					thisQuery = thisQuery.replace("{2}", qConcept);
					answer.add(thisQuery);
				}
			}
		}
	}

	/** Executes each one of the queries, and stores the results on the blackboard */
	// TODO add weightings so the certain relationships result in better scores for some queries
	public static void evaluateQueries(Blackboard blackboard)
	{
		for (AnswerData answer : blackboard.answers)
		{
			for (int i=0; i<answer.getQueries().size(); i++) {
				String query = answer.getQueries().get(i);
				Integer result = GraphQueryService.getInstance().executeIntegerValueQuery(query);
				answer.addResult(i, result);
			}
		}
	}


	/** Combines the results produced by queries to obtain a score, allowing the answers to be scored and ranked */
	public static void scoreResults(Blackboard blackboard)
	{
		for (AnswerData answer : blackboard.answers)
		{
			int total = 0;    // can rank by average path for all queries
			int lowest = 100; // can rank by shortest possible path
			for (int i=0; i<answer.getResults().size(); i++) {
				Integer value = answer.getResults().get(i);
				total += value;
				if (value > 0 && value < lowest) lowest = value;
			}

			if (metric == RankMetric.SHORTEST_PATH) {
				answer.setScore(lowest);
			}
			else if (metric == RankMetric.AVERAGE_PATH)
			{
				// find the number of results with a valid path
				double validScores = 0;
				for (int r : answer.getResults()) if (r > 0) validScores++;
				// if total is 0, no paths exist, answer disqualified
				if (total == 0 || validScores == 0)
					answer.setScore(100);
				else
					answer.setScore((double)total / validScores);
			}
		}
	}


	/** Adjusts the scores of answers so those with fewer concept terms are not unfairly advantaged */
	public static void weightResults(Blackboard blackboard, boolean lowerIsBetter)
	{
		int maxAnswerTerms = 0;
		for (AnswerData answer : blackboard.answers) {
			if (answer.getConcepts().size() > maxAnswerTerms)
				maxAnswerTerms = answer.getConcepts().size();
		}
		App.log("Max answer terms: " + maxAnswerTerms);
		for (AnswerData answer : blackboard.answers) {
			int missingTerms = maxAnswerTerms - answer.getConcepts().size();
			if (missingTerms > 0) {
				double adjustment = missingTerms * 0.20;
				App.log("Adding adjustment of " + adjustment + " to answer " + answer.getCode());
				if (lowerIsBetter)
					answer.setScore(answer.getScore() + adjustment);
				else
					answer.setScore(answer.getScore() - adjustment);
			}
		}

		// check for possible tie-breaking
		List<AnswerData> best = blackboard.getBestAnswers();
		App.log("Have " + best.size() + " top answer(s)");
		if (best.size() > 1) {
			double bestDegree = (lowerIsBetter ? Double.MAX_VALUE : 0);
			AnswerData lowestData = null;
			for (AnswerData a : best) {
				double averageDegree = a.getAverageDegreeOfConcepts();
				App.log("Average Degree of " + a.getCode() + ": " + a.getConcepts() + " = " + averageDegree);
				if (lowerIsBetter)
				{
					if (averageDegree < bestDegree) {
						bestDegree = averageDegree;
						lowestData = a;
					}
				}
				else if (averageDegree > bestDegree) {
					bestDegree = averageDegree;
					lowestData = a;
				}
			}

			if (lowestData != null) {
				App.log("Lowest Degree Bonus given to answer " + lowestData.getCode());
				if (lowerIsBetter)
					lowestData.setScore(lowestData.getScore() - 0.1);
				else
					lowestData.setScore(lowestData.getScore() + 0.1);
			}
		}

	}


	/** Score by occurrence of a particular part-of-speech token */
	public static void scoreByPosTokens(Blackboard blackboard, List<String> tokens, boolean lowestBest)
	{
		for (AnswerData answer : blackboard.answers)
		{
			int score = 0;
			for (String t : tokens) score += answer.countTokens(t);
			if (lowestBest)
				answer.setScore(score);
			else
				answer.setScore(100-score);
		}
	}

	/** Tie-breaker to give the lowest score to the answer with the most specific concepts */
	public static void scoreByAnswerConcepts(Blackboard blackboard)
	{
		App.log("Scoring by specificness of answer concepts");
		double lowestDegree = Double.MAX_VALUE;
		AnswerData lowestData = null;
		for (AnswerData a : blackboard.answers) {
			double averageDegree = a.getAverageDegreeOfConcepts();
			App.log("Average Degree of " + a.getCode() + ": " + a.getConcepts() + " = " + averageDegree);
			if (averageDegree < lowestDegree) {
				lowestDegree = averageDegree;
				lowestData = a;
			}
		}
		if (lowestData != null)
			lowestData.setScore(lowestData.getScore()-1);
	}


	/** Determines score by finding the answer with the most words that match the question concepts */
	public static boolean scoreByDefinition(Blackboard blackboard)
	{
		List<String> definitionConcepts = GraphQueryService.getInstance().getDefinitions(blackboard.questionConcepts);
		if (definitionConcepts == null) return false;
		App.log("Definition Concepts: " + definitionConcepts.toString());

		boolean haveResult = false;
		for (AnswerData answer : blackboard.answers)
		{
			List<String> matches = new ArrayList<String>();
			List<String> answerMatchList = TaggingService.findWords(answer.getTags());

			App.log(answer.getCode() + " answerMatchList: " + answerMatchList.toString());

			int count = 0;
			// first, we compare concepts in the question definition, with concepts in the answers
			for (String defWord : definitionConcepts)
			{
				for (String term : answerMatchList)
				{
					if (term.equalsIgnoreCase(defWord) && !matches.contains(defWord)) {
						App.log(answer.getCode() + ": ++ " + defWord + " (in question definition)");
						matches.add(defWord);
						count++;
					}
					// else App.log(answer.getCode() + ": no match " + defWord + " and " + term);
				}
			}

			// then try the words of any answers that also have a definition
			List<String> answerDefWords = GraphQueryService.getInstance().getDefinitions(answer.getConcepts());

			// if there are no definitions for this answer, move onto the next one
			if (answerDefWords == null || answerDefWords.size() == 0) {
				App.log(answer.getCode() + ": no definition words in answer");
				if (count > 0) haveResult = true;
				answer.setScore(100-count);
				continue;
			}
			// we want to compare words rather than tags to increases chances of matches
			List<String> questionWords = GraphQueryService.getInstance().findWords(blackboard.question.text);
			App.log("Question concepts: " + questionWords.toString());

			App.log(answer.getCode() + ": concepts: " + answerDefWords.toString());
			for (String def : answerDefWords)
			{
				for (String qWord : questionWords) {
					if (def.equalsIgnoreCase(qWord) && !matches.contains(qWord)) {
						App.log(answer.getCode() + ": ++ " + qWord);
						matches.add(qWord);
						count++;
					}
					// else App.log(answer.getCode() + ": no match " + def + " and " + qWord);
				}
			}

			answer.setScore(100-count);
			if (count > 0) haveResult = true;
		}

		// if we have scores, return true
		return haveResult;
	}


	/** Determines score by finding the answer with the most words that match the question concepts */
	public static boolean findNumericAnswer(Blackboard blackboard)
	{
		List<Double> numbers = new ArrayList<Double>();
		if (blackboard.questionConcepts == null || blackboard.questionConcepts.size() == 0) return false;
		for (String concept : blackboard.questionConcepts)
		{
			String definition = GraphQueryService.getInstance().getDefinitionsUsingSynonyms(concept);
			if (definition == null) continue;

			App.log("Definition of: " + concept + " = " + definition);
			List<WordTag> wordTags = GraphQueryService.getInstance().identifyTags(definition);
			List<Double> results = TaggingService.findNumbers(wordTags);
			if (results != null && results.size() > 0) {
				App.log(results.toString() + " found in definition of " + concept);
				numbers.addAll(results);
			}
		}

		App.log("Numbers found in definition of question: " + numbers.toString());
		boolean haveResult = false;
		for (AnswerData answer : blackboard.answers)
		{
			int count = 0;
			// first, we compare concepts in the question definition, with concepts in the answers
			for (Double defNum : numbers)
			{
				Double ansNum = answer.getAnswerAsNumber();
				if (ansNum != null && ansNum.equals(defNum)) {
					App.log(answer.getCode() + ": ++ " + defNum);
					count++;
				}
			}

			answer.setScore(100-count);
			if (count > 0) haveResult = true;
		}

		// if we have scores, return true
		return haveResult;
	}



	public static boolean scoreByRelations(Blackboard blackboard, RelationTypes relation)
	{
		boolean haveResult = false;
		for (AnswerData answer : blackboard.answers)
		{
			int count = 0;
			for (String qConcept : blackboard.questionConcepts)
			{
				for (String word : answer.getConcepts())
				{
					String query = "MATCH (to:Concept { name:\"/c/en/" + qConcept + "\" })-[:" + relation.name() + "]-(from:Concept { name:\"/c/en/" + word +"\" }) return count(*) as result";
					boolean connected = GraphQueryService.getInstance().executeIntegerValueQuery(query) > 0;
					App.log(query + " = " + connected);
					if (connected) count++;
				}
			}
			answer.setScore(answer.getScore()-count);
			if (count > 0) haveResult = true;
		}
		return haveResult;
	}


	/** Score by occurrence of a properties */
	public static boolean scoreByProperties(Blackboard blackboard)
	{
		List<String> matchList = new ArrayList<String>();

		for (String concept : blackboard.questionConcepts)
		{
			for (String link : blackboard.linkProperties)
			{
				List<String> results = GraphQueryService.getInstance().getProperties(concept, link);
				if (results.size() > 0) matchList.addAll(results);
			}
		}

		App.log("Concept Match List => " + matchList.toString());
		boolean answered = false;
		for (AnswerData answer : blackboard.answers)
		{
			answer.setScore(100);
			for (String concept : answer.getConcepts())
			{
				for (String matchWord : matchList)
				{
					matchWord = StringUtils.remove(matchWord, "/c/en/");
					if (matchWord.equals(concept)) {
						answer.setScore(answer.getScore() - 1);
						App.log(answer.getCode() + ": ++ " + concept);
						answered = true;
					}
				}
			}
		}
		return answered;
	}


	/** Placeholder logic for "all of the above?" questions, defaults to choosing all option */
	public static void scoreAllAboveQuestion(Blackboard blackboard)
	{
		for (AnswerData answer : blackboard.answers)
		{
			answer.setScore(100);
			String text = answer.text.toLowerCase();
			if (text.contains("all of ") && text.contains("above"))
				answer.setScore(99);
		}
	}


	public static void updateStats(Question question, String answer)
	{
		String outcome = "Answer is " + answer + " (should be " + question.correctAnswer + ")";
		if (answer.equals(question.correctAnswer)) {
			App.log(outcome + " -- CORRECT :)");
			Statistics.correct++;
		} else {
			App.log(outcome + " -- WRONG :(");
			Statistics.incorrect++;
			if (App.logIncorrect) {
				writeQuestion(new File("incorrect.tsv"), question);
			}
		}


	}

	public static String getRandomAnswer()
	{
		Random rand = new Random();
		int r = rand.nextInt(4);
		String[] answers = { "A", "B", "C", "D"};
		return answers[r];
	}

	// incrementally append a line representing a question
	// this is used to build new question files containing, for instance, ones we can't currently answer
	public static void writeQuestion(File file, Question q)
	{
		BufferedWriter bw = null;
		try
		{
			if (!file.exists()) bw = new BufferedWriter(new FileWriter(file, true));

			bw = new BufferedWriter(new FileWriter(file, true));
			bw.write(q.id + "\t" + q.text + "\t" + q.correctAnswer);
			for (String a : q.answers) bw.write("\t" + a);
			bw.newLine();
			bw.flush();
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		finally {
			// always close the file
			if (bw != null) try {
				bw.close();
			} catch (IOException ioe2) {
				// just ignore it
			}
		}
	}

	
}

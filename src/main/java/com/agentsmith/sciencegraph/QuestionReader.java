package com.agentsmith.sciencegraph;

import java.io.*;
import java.util.HashMap;
import java.util.Random;
import java.util.StringTokenizer;

import com.agentsmith.sciencegraph.Question.QuestionType;
import org.apache.commons.lang3.StringUtils;


/**
 * This handles the reading of the question files and the creation of question objects
 * Two formats are supported, the Kaggle competition format (TSV) and the AI2 distribution format (CSV)
 */

public class QuestionReader
{
	public enum LineFormat { TSV, CSV }

	/** Reads the datafile, line by line */
	public static Question getRandomQuestion(File dataFile)
	{
		LineFormat format = LineFormat.TSV;
		if (dataFile.getName().endsWith("csv")) format = LineFormat.CSV;

		Question question = null;
		try
		{
			LineNumberReader lnr = new LineNumberReader(new FileReader(dataFile));
			lnr.skip(Long.MAX_VALUE);
			int lines = lnr.getLineNumber() + 1; // Add 1 because line index starts at 0
			// Finally, the LineNumberReader object should be closed to prevent resource leak
			lnr.close();

			System.out.println("Reading file " + dataFile.getPath() + " with " + (lines-1) + " questions");

			// create a random number
			Random rand = new Random();
			int r = rand.nextInt(lines-1) + 1;
			BufferedReader br = new BufferedReader(new FileReader(dataFile));

			// skip header
			String line = br.readLine();
			int counter = 1;
			while ((line = br.readLine()) != null) 
			{
				if (counter == r) {
					System.out.println("Reading line " + counter);
					question = parseLine(line, format);
					break;
				}
				counter++;
			}
			br.close();
			System.out.println("\n" + question.toString() + "\n");
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return question;
	}


	/** Reads one line from the datafile */
	public static Question getOneQuestion(File dataFile, int lineNumber)
	{
		LineFormat format = LineFormat.TSV;
		if (dataFile.getName().endsWith("csv")) format = LineFormat.CSV;
		Question question = null;
		try
		{
			LineNumberReader lnr = new LineNumberReader(new FileReader(dataFile));
			lnr.skip(Long.MAX_VALUE);
			int lines = lnr.getLineNumber() + 1; // Add 1 because line index starts at 0
			// Finally, the LineNumberReader object should be closed to prevent resource leak
			lnr.close();

			System.out.println("Reading file " + dataFile.getPath() + " with " + (lines-1) + " questions");
			BufferedReader br = new BufferedReader(new FileReader(dataFile));

			// skip header
			String line = br.readLine();
			int counter = 1;
			while ((line = br.readLine()) != null)
			{
				if (counter == lineNumber) {
					System.out.println("Reading line " + counter);
					question = parseLine(line, format);
					break;
				}
				counter++;
			}
			br.close();
			System.out.println("\n" + question.toString() + "\n");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return question;
	}


	/** Steps through the questions and classifies them according to their type */
	public static void analyseQuestionTypes(File dataFile, File statsFile)
	{
		LineFormat format = LineFormat.TSV;
		if (dataFile.getName().endsWith("csv")) format = LineFormat.CSV;
		try
		{
			LineNumberReader lnr = new LineNumberReader(new FileReader(dataFile));
			lnr.skip(Long.MAX_VALUE);
			int lines = lnr.getLineNumber() + 1; // Add 1 because line index starts at 0
			lnr.close();

			// create a map of all possible question types
			HashMap<QuestionType, Integer> stats = new HashMap<QuestionType, Integer>();
			for (QuestionType type : QuestionType.values()) stats.put(type, 0);

			BufferedReader br = new BufferedReader(new FileReader(dataFile));
			// skip header
			String line = br.readLine();
			int counter = 1;
			while ((line = br.readLine()) != null)
			{
				Question question = QuestionReader.parseLine(line, format);
				Blackboard blackboard = new Blackboard(question);
				GraphQueryService.getInstance().identifyQuestionConcepts(blackboard);
				GraphQueryService.getInstance().identifyAnswerConcepts(blackboard);
				QuestionClassifier.classify(blackboard);
				int count = stats.get(blackboard.question.type) + 1;
				stats.put(blackboard.question.type, count);

				// TODO if the type is missing word, store it, so we know what definitions to add
				if (blackboard.question.type == QuestionType.ALL_OPTION) {
					ProblemSolver.writeQuestion(statsFile, question);
				}

				counter++;
				if (counter % (lines/100) == 0) {
					double progressPercent = ((double)counter/(double)lines)*100d;
					App.updateProgress(progressPercent);
				}
			}
			br.close();
			System.out.println("\nAll questions read\n");

			for (QuestionType type : QuestionType.values())
				System.out.println(type.toString() + " = " + stats.get(type));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}



	/** Parse a line, extract the information it contains, and add it to the database */
	public static Question parseLine(String line, LineFormat format)
	{
		if (line == null) return null;

		if (format == LineFormat.TSV) {
			StringTokenizer tok = new StringTokenizer(line, "\t");
			Question q = new Question(tok);
			return q;
		}

		// otherwise assume more complex CSV format
		StringTokenizer tok = new StringTokenizer(line, ",");

		String id = tok.nextToken();
		String id2 = tok.nextToken();
		String points = tok.nextToken();
		String answer = tok.nextToken();
		String isMC = tok.nextToken();
		String hasDiagram = tok.nextToken();
		String exam = tok.nextToken();
		String grade = tok.nextToken();
		String year = tok.nextToken();
		String text = tok.nextToken();

		String raw = null;
		try
		{
			// the question text can contain commas itself, so use the everything that's left, and omit the last 2 fields
			int finalCommaPos = StringUtils.lastOrdinalIndexOf(line, ",", 2);
			text = line.substring(line.indexOf(text), finalCommaPos);
			raw = StringUtils.remove(text, "\"");

			// System.out.println("RAW >> " + raw);

			int pos1 =  raw.indexOf("(A)");
			int pos2 =  raw.indexOf("(B)");
			int pos3 =  raw.indexOf("(C)");
			int pos4 =  raw.indexOf("(D)");

			text = raw.substring(0, pos1);

			// System.out.println("Question: " + text);

			String[] options = new String[4];
			options[0] = raw.substring(pos1+3, pos2);
			options[1] = raw.substring(pos2+3, pos3);
			if (pos4 > 0) {
                options[2] = raw.substring(pos3+3, pos4);
                options[3] = raw.substring(pos4+3);
            }
            else {
                options[2] = raw.substring(pos3+3);
                options[3] = "";
            }

			Question q = new Question(id, text, options, answer);
			return q;
		} catch (Exception e) {
			System.out.println("Problem parsing: " + raw);
			e.printStackTrace();
			return null;
		}
	}
	
}

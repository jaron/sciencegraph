package com.agentsmith.sciencegraph;

import java.io.*;
import java.util.*;

import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;




/**
 * The root class of the ScienceGraph application.
 * 
 * The design principle here is invest effort up front to create a graph that will be quick to subsequently query.
 */
public class App 
{
	// Modes are:
	// TRAIN - run against the training set, where answers are provided
	// VALIDATE - run against the question set, where answers are unseen
	// INCORRECT - run against the last generated incorrect question set, which we've been unable to solve

	public enum RunMode { TRAIN, VALIDATE, FINAL, INCORRECT };

	// assumes a Mac/Linux filesystem, change this if you're running on Windows
	// this database will be purged whenever the application is run anew
	// but its contents will be preserved on exit, so you can view what's been loaded using the Neo4j data browser
	public static final String DB_PATH = "/Users/jaron/Documents/Neo4j/concept5.db/";

	// this is only used by the unit tests, it will be completely purged when they finish
	// public static final String TEST_DB_PATH = "/tmp/neo4j-data/sciencegraph-test.db";

	public static RunMode runMode = RunMode.FINAL;

	// use this to configure whether we solve one question at a time or all questions at once
	public static boolean runOnce = false;

	// set this to true to analyse the question set, rather than attempt to solve it
	public static boolean analyse = false;

	// set to true if you want to record all the wrong answers
	public static boolean logIncorrect = false;

	// set to true for verbose description of the problem solving process
	public static boolean consoleLogging = runOnce;


    /** The main class of the application */
    public static void main( final String[] args )
    {
    	App sciApp = new App();
		Statistics.reset();
		Statistics.incorrectLimit = Integer.MAX_VALUE;
    	try
    	{
			// check we have a connection with the database and it's been populated
			long count = GraphQueryService.getInstance().countNodes();
			if (count == 0) {
				System.err.println("No nodes in database - please check connection");
				System.exit(1);
			}
			System.out.println(count + " nodes exist in database");

			if (args != null && args.length == 1) {
				if ("-once".equalsIgnoreCase(args[0])) {
					runOnce = true;
					consoleLogging = true;
				}
			}

    		// read from default file based on run mode
    		String filename = "src/main/resources/";
			if (runMode == RunMode.TRAIN) filename += "AI2-Elementary/Elementary-All.csv";
			if (runMode == RunMode.VALIDATE) filename += "AI2-8thGrade/8thGr-All.csv";
			if (runMode == RunMode.FINAL) filename += "AI2-8thGrade/8thGr-All.csv";
			if (runMode == RunMode.INCORRECT) filename = "incorrect.tsv";

			// or one supplied as a command line parameter
    		/* if (args.length > 1) {
				runMode = RunMode.VALIDATE;
				filename = args[0];
			} */
			// System.out.println("Usage: java -cp target/sciencegraph-1.0-jar-with-dependencies.jar com.agentsmith.sciencegraph.App <datafile>");

			File dataFile = new File(filename);
			if (!dataFile.exists()) {
				System.out.println("** Error: unable to load question file " + dataFile.getPath() + " - please check the path and file name");
				System.exit(1);
			}

			// analysis mode looks for insights in questions, rather than solving them
			if (analyse)
			{
				File statsFile = new File("stats.tsv");
				if (statsFile.exists()) statsFile.delete();
				QuestionReader.analyseQuestionTypes(dataFile, statsFile);
				System.exit(1);
			}

			if (runMode != RunMode.INCORRECT && logIncorrect) {
				File outputFile = new File("incorrect.tsv");
				if (outputFile.exists()) outputFile.delete();
			}

			if (runMode == RunMode.TRAIN || runMode == RunMode.INCORRECT)
			{
				if (runOnce)
				{
					// call the solving code with a single randomly selected problem
					Question question = QuestionReader.getRandomQuestion(dataFile);
					ProblemSolver.solve(question);
				}
				else
				{
					File answerFile = new File("test-results.txt");
					if (answerFile.exists()) answerFile.delete();
					File unasweredFile = new File("unanswered.tsv");
					if (unasweredFile.exists()) unasweredFile.delete();
					sciApp.attemptAllProblems(dataFile, answerFile);
				}
			}
			else
			{
				if (runOnce)
				{
					System.out.println("Attempting to answer one randomly chosen problem");
					Question question = QuestionReader.getRandomQuestion(dataFile);
					String answer = ProblemSolver.solve(question);
					System.out.println("Best guess for answer = " + answer);
				}
				else {
					System.out.println("Attempting all problems");
					File answerFile = new File("results.txt");
					if (answerFile.exists()) answerFile.delete();
					sciApp.attemptAllProblems(dataFile, answerFile);
				}
			}
			Statistics.print();
    	}
    	finally
    	{
    		// once finished close the database (otherwise it will remain locked)
    		sciApp.shutDown();
    	}
    }


	public void attemptAllProblems(File dataFile, File answerFile)
	{
		try
		{
			QuestionReader.LineFormat format = QuestionReader.LineFormat.TSV;
			if (dataFile.getName().endsWith("csv")) format = QuestionReader.LineFormat.CSV;

			LineNumberReader lnr = new LineNumberReader(new FileReader(dataFile));
			lnr.skip(Long.MAX_VALUE);
			int lines = lnr.getLineNumber() + 1; // Add 1 because line index starts at 0
			lnr.close();

			BufferedReader br = new BufferedReader(new FileReader(dataFile));

			App.log("Reading data file with " + lines + " lines");

			// skip header
			String line = br.readLine();
			int counter = 1;
			while ((line = br.readLine()) != null)
			{
				// System.out.println("Reading line " + counter);
				Question question = QuestionReader.parseLine(line, format);
				// System.out.println("\n" + question.toString());

				// TODO compare derived answer with real answer
				String answer = ProblemSolver.solve(question);
				writeAnswer(answerFile, question, answer);

                counter++;
				if (counter % (Math.max(1, lines/100)) == 0) {
					double progressPercent = ((double)counter/(double)lines)*100d;
					App.updateProgress(progressPercent);
				}

				if (Statistics.incorrect >= Statistics.incorrectLimit) break;
            }
			br.close();
			System.out.println("\nAll questions read\n");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}



	// incrementally append a line representing a new answer
	private static void writeAnswer(File answerFile, Question question, String answer)
	{
		BufferedWriter bw = null;
		try
		{
			if (!answerFile.exists())
			{
				bw = new BufferedWriter(new FileWriter(answerFile, true));
				bw.write("id,correctAnswer");
				bw.newLine();
				bw.flush();
			}

			bw = new BufferedWriter(new FileWriter(answerFile, true));
			bw.write(question.id + "," + answer);
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

    private void shutDown()
    {
		// if using the embedded db service, this is where you shut it down
		// if (graphDb == null) return;
        // System.out.println( "\nShutting down database ..." );
        // graphDb.shutdown();
    }


    /** Registers a shutdown hook for the Neo4j instance so that it shuts down
        nicely when the VM exits (even if you Ctrl-C the running application) */
    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        } );
    }

	/** Allows us to control whether log messages are printed to the console */
	public static void log(String message) {
		if (consoleLogging) System.out.println(message);
	}
    
    // where operations take a while to complete, it's polite to give the user something to look at
 	static void updateProgress(double progressPercent) 
 	{
 	    final int width = 50; // progress bar width in chars
 	    System.out.print("\r[");
 	    for (int i=0; i < width; i++) {
 	    	System.out.print(i <= (int)(progressPercent/(100/width)) ? "." : " ");
 	    }
 	    System.out.print("] " + (int)progressPercent + "%");
 	    System.out.flush();
 	 }
}

package com.agentsmith.sciencegraph;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Tests typical examples of problems, to ensure changes don't break solving elsewhere
 */
public class PublicQuestionsTest
{
    private File tDataFile;

    private File vDataFile;

    @Before
    public void prepareTestDatabase()
    {
        App.runOnce = true;

        System.out.println("Setting up test database...");
        long count = GraphQueryService.getInstance().countNodes();
        Assert.assertTrue(count > 0);

        tDataFile = new File("src/main/resources/AI2-8thGrade/8thGr-All.csv");
        Assert.assertTrue(tDataFile.exists());
    }


    @Test
    /** Tests a composed_of question, that finds an answer from specific relations  (PART_OF and MADE_OF) */
    public void testSolveByRelations()
    {
        Question question = QuestionReader.getOneQuestion(tDataFile, 102);
        Assert.assertEquals(question.correctAnswer, ProblemSolver.solve(question));
    }




}

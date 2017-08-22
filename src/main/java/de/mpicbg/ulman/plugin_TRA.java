/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;
import net.imagej.ImageJ;

import de.mpicbg.ulman.workers.TRA;
import de.mpicbg.ulman.workers.TRA.PenaltyConfig;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>TRA measure",
        name = "CTC_TRA",
		  description = "Calculates the TRA tracking performance measure from the AOGM paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html")
public class plugin_TRA implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to ground-truth folder: ",
		columns = 40,
		description = "Path should contain folder TRA and files: TRA/man_track???.tif and TRA/man_track.txt")
	private String gtPath;

	@Parameter(label = "Path to computed result folder: ",
		columns = 40,
		description = "Path should contain result files directly: mask???.tif and res_track.txt")
	private String resPath;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/Submission_of_Results.html";


	@Parameter(label = "Penalty preset:",
		choices = {"Cell Tracking Challenge",
		           "use the values below"},
		callback = "onPenaltyChange")
	private String penaltyModel;

	@Parameter(label = "Splitting operations penalty:",
		min = "0.0", callback = "onWeightChange")
	private Double p1 = 5.0;
	@Parameter(label = "False negative vertices penalty:",
		min = "0.0", callback = "onWeightChange")
	private Double p2 = 10.0;
	@Parameter(label = "False positive vertices penalty:",
		min = "0.0", callback = "onWeightChange")
	private Double p3 = 1.0;
	@Parameter(label = "Redundant edges to be deleted penalty:",
		min = "0.0", callback = "onWeightChange")
	private Double p4 = 1.0;
	@Parameter(label = "Edges to be added penalty:",
		min = "0.0", callback = "onWeightChange")
	private Double p5 = 1.5;
	@Parameter(label = "Edges with wrong semantics penalty:",
		min = "0.0", callback = "onWeightChange")
	private Double p6 = 1.0;

	@Parameter(label = "Consistency check of input data:",
		description = "Checks multiple consisteny-oriented criteria on both input and GT data.")
	private boolean doConsistencyCheck = true;

	@Parameter(label = "Verbose report on tracking errors:",
		description = "Logs all discrepancies (and organizes them by category) between the input and GT data.")
	private boolean doLogReports = true;


	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String citationFooter
		= "Please, cite us.... TBA";


	@Parameter(type = ItemIO.OUTPUT)
	double TRA = -1;

	///input GUI handler
	@SuppressWarnings("unused")
	private void onPenaltyChange()
	{
		if (penaltyModel.startsWith("Cell Tracking Challenge"))
		{
			p1 = 5.0;
			p2 = 10.0;
			p3 = 1.0;
			p4 = 1.0;
			p5 = 1.5;
			p6 = 1.0;
		}
		else
		if (penaltyModel.startsWith("some other future preset"))
		{
			p1 = 99.0;
		}
	}

	///input GUI handler
	@SuppressWarnings("unused")
	private void onWeightChange()
	{
		penaltyModel = "use the values below";
	}


	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//start up the worker class
			final TRA tra = new TRA(log);

			//set up its operational details
			tra.doConsistencyCheck = doConsistencyCheck;
			tra.doLogReports = doLogReports;

			//also the TRA weights
			final PenaltyConfig penalty = tra.new PenaltyConfig(p1,p2,p3,p4,p5,p6);
			tra.penalty = penalty;

			//do the calculation
			TRA = tra.calculate(gtPath,resPath);

			//do not report anything explicitly (unless special format for parsing is
			//desired) as ItemIO.OUTPUT will make it output automatically
		}
		catch (RuntimeException e) {
			log.error("TRA problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("TRA error: "+e.getMessage());
		}
	}


	//------------- command line stuff -------------
	//
	//the CLI path entry function:
	public static void main(final String... args)
	{
		/*
		//check the input parameters
		if (args.length != 2)
		{
			System.out.println("Incorrect number of parameters, expecting exactly two parameters.");
			System.out.println("Parameters: GTpath RESpath\n");
			System.out.println("GTpath should contain folder TRA and files: TRA/man_track???.tif and TRA/man_track.txt");
			System.out.println("RESpath should contain result files directly: mask???.tif and res_track.txt");
			System.out.println("Certain data format is assumed, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html");
			return;
		}
		*/

		//parse and store the arguments, if necessary
		//....

		//start up our own ImageJ without GUI
		final ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		//run this class is if from GUI
		//ij.command().run(plugin_TRA.class, true, "gtPath",args[0], "resPath",args[1]);

		//and close the IJ instance...
		//ij.appEvent().quit();
	}
}

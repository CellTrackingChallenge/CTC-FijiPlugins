/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
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
import de.mpicbg.ulman.workers.SEG;

@Plugin(type = Command.class, menuPath = "Plugins>Cell Tracking Challenge>Technical measures",
        name = "CTC_ICT", headless = true,
		  description = "Calculates technical tracking performance measures from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html")
public class plugin_CTCmeasuresICT implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to computed result folder:",
		columns = 40,
		description = "Path should contain result files directly: mask???.tif and res_track.txt")
	private String resPath;

	@Parameter(label = "Path to ground-truth folder:",
		columns = 40,
		description = "Path should contain folders SEG, TRA and files: SEG/man_seg.*tif, TRA/man_track???.tif and TRA/man_track.txt")
	private String gtPath;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/Submission_of_Results.html";


	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false,
		label = "Select measures to calculate:")
	private final String measuresHeader = "";

	@Parameter(label = "SEG",
		description = "Quantifies the amount of overlap between the reference annotations and the computed segmentation.")
	private boolean calcSEG = true;

	@Parameter(label = "TRA",
		description = "Evaluates the ability of an algorithm to track cells in time.")
	private boolean calcTRA = true;


	//citation footer...
	/*
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private final String citationFooter
		= "Please, cite us.... TBA";
	*/


	//hidden output values
	@Parameter(type = ItemIO.OUTPUT)
	String GTdir;
	@Parameter(type = ItemIO.OUTPUT)
	String RESdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double SEG = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double TRA = -1;


	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//saves the input paths for the final report table
			GTdir  = gtPath;
			RESdir = resPath;

			if (calcSEG)
			{
				final SEG seg = new SEG(log);
				seg.doLogReports = true;
				SEG = seg.calculate(gtPath, resPath);
			}

			if (calcTRA)
			{
				final TRA tra = new TRA(log);
				tra.doConsistencyCheck = true;
				tra.doLogReports = true;
				TRA = tra.calculate(gtPath, resPath);
			}

			//do not report anything explicitly (unless special format for parsing is
			//desired) as ItemIO.OUTPUT will make it output automatically
		}
		catch (RuntimeException e) {
			log.error("CTC measures problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC measures error: "+e.getMessage());
		}
	}


	//------------- command line stuff -------------
	//
	//the CLI path entry function:
	public static void main(final String... args)
	{
		//check the input parameters
		if (args.length != 2)
		{
			System.out.println("Incorrect number of parameters, expecting exactly two parameters.");
			System.out.println("Parameters: GTpath RESpath\n");
			System.out.println("GTpath should contain folders SEG, TRA and files: SEG/man_seg.*tif, TRA/man_track???.tif and TRA/man_track.txt");
			System.out.println("RESpath should contain result files directly: mask???.tif and res_track.txt");
			System.out.println("Certain data format is assumed, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html");
			return;
		}

		//parse and store the arguments, if necessary
		//....

		//start up our own ImageJ without GUI
		final ImageJ ij = new net.imagej.ImageJ();
		//DEBUG//ij.ui().showUI();

		//run this class as if from GUI
		ij.command().run(plugin_CTCmeasuresICT.class, true, "gtPath",args[0], "resPath",args[1],
			"calcTRA",true, "calcSEG",true);

		//and close the IJ instance...
		ij.appEvent().quit();
	}
}

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

import de.mpicbg.ulman.workers.SEG;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>SEG measure",
        name = "CTC_SEG",
		  description = "Calculates the SEG tracking performance measure from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html")
public class plugin_SEG implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to ground-truth folder: ",
		columns = 40,
		description = "Path should contain folder SEG and files: SEG/man_seg*.tif")
	private String gtPath;

	@Parameter(label = "Path to computed result folder: ",
		columns = 40,
		description = "Path should contain result files directly: mask???.tif")
	private String resPath;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/Submission_of_Results.html";


	@Parameter(label = "Verbose report on segmentation errors:",
		description = "Jaccard index value is logged for every overlapping input and GT marker.")
	private boolean doLogReports = true;


	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String citationFooter
		= "Please, cite us.... TBA";


	@Parameter(type = ItemIO.OUTPUT)
	double SEG = -1;


	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//start up the worker class
			final SEG seg = new SEG(log);

			//set up its operational details
			seg.doLogReports = doLogReports;

			//do the calculation
			SEG = seg.calculate(gtPath,resPath);

			//do not report anything explicitly (unless special format for parsing is
			//desired) as ItemIO.OUTPUT will make it output automatically
		}
		catch (RuntimeException e) {
			log.error("SEG problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("SEG error: "+e.getMessage());
		}
	}


	//------------- command line stuff -------------
	//
	//the CLI path entry function:
	public static void main(final String... args)
	{
		//check the input parameters

		//parse and store the arguments, if necessary
		//....

		//start up our own ImageJ without GUI
		final ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		//run this class is if from GUI
		//ij.command().run(plugin_SEG.class, true, "gtPath",args[0], "resPath",args[1]);

		//and close the IJ instance...
		//ij.appEvent().quit();
	}
}

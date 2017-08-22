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

import de.mpicbg.ulman.workers.TrackDataCache;
import de.mpicbg.ulman.workers.TRA;
import de.mpicbg.ulman.workers.SEG;
import de.mpicbg.ulman.workers.CT;
import de.mpicbg.ulman.workers.TF;
import de.mpicbg.ulman.workers.BCi;
import de.mpicbg.ulman.workers.CCA;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>All Measures",
        name = "CTC_ALL", headless = true,
		  description = "Calculates all tracking performance measures from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html")
public class plugin_CTCmeasures implements Command
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


	@Parameter(visibility = ItemVisibility.MESSAGE, label = "Select measures to calculate:")
	private final String measuresHeader = "";

	@Parameter(label = "TRA",
		description = "Evaluates the ability of an algorithm to track cells in time.")
	private boolean calcTRA = true;

	@Parameter(label = "SEG",
		description = "Quantifies the amount of overlap between the reference annotations and the computed segmentation.")
	private boolean calcSEG = true;

	@Parameter(label = "CT",
		description = "Examines how good a method is at reconstructing complete reference tracks.")
	private boolean calcCT = true;

	@Parameter(label = "TF",
		description = "Targets the longest, correctly reconstructed, continuous fraction of a reference track.")
	private boolean calcTF = true;

	@Parameter(label = "BC(i)",
		description = "Examines how good a method is at reconstructing mother-daughter relationships.")
	private boolean calcBCi = true;

	@Parameter(label = "i =", min = "0", max = "5", columns = 3,
		description = "Value of 'i' for which the BC(i) should be computed.")
	private int iForBCi = 2;

	@Parameter(label = "CCA",
		description = "Reflects the ability of an algorithm to discover true distribution of cell cycle lengths in a video.")
	private boolean calcCCA = true;


	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String citationFooter
		= "Please, cite us.... TBA";


	//hidden output values
	@Parameter(type = ItemIO.OUTPUT)
	String GTdir;
	@Parameter(type = ItemIO.OUTPUT)
	String RESdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double TRA = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double SEG = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double CT = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double TF = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double BCi = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double CCA = -1;


	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//reference on a shared object that does
			//pre-fetching of data and some common pre-calculation
			TrackDataCache cache = null;

			//start up the independent workers
			final TRA tra = new TRA(log);
			final SEG seg = new SEG(log);
			final CT  ct  = new CT(log);
			final TF  tf  = new TF(log);
			final BCi bci = new BCi(log);
			final CCA cca = new CCA(log);

			//set this one before we start any calculation
			bci.setI(iForBCi);

			GTdir  = gtPath;
			RESdir = resPath;

			//do the calculation and retrieve updated cache afterwards
			if (calcTRA)
			{
				TRA = tra.calculate(gtPath,resPath,cache);
				cache = tra.getCache();
			}

			//SEG is whole different from the tracking-oriented rest,
			//thus, it cannot really utilize the shared/cached data
			if (calcSEG)
				SEG = seg.calculate(gtPath,resPath);

			if (calcCT )
			{
				CT = ct.calculate(gtPath,resPath,cache);
				cache = ct.getCache();
			}

			if (calcTF )
			{
				TF = tf.calculate(gtPath,resPath,cache);
				cache = tf.getCache();
			}

			if (calcBCi)
			{
				BCi = bci.calculate(gtPath,resPath,cache);
				cache = bci.getCache();
			}

			if (calcCCA)
			{
				CCA = cca.calculate(gtPath,resPath,cache);
				cache = cca.getCache();
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
			System.out.println("GTpath should contain folder TRA and files: TRA/man_track???.tif and TRA/man_track.txt");
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
		ij.command().run(plugin_CTCmeasures.class, true, "gtPath",args[0], "resPath",args[1],
			"calcTRA",true, "calcSEG",true, "calcCT",true, "calcTF",true,
			"calcBCi",true, "iForBCi", 2, "calcCCA",true,
			"pathFooterA","a", "pathFooterB","a", "measuresHeader","a", "citationFooter","a");

		//and close the IJ instance...
		ij.appEvent().quit();
	}
}

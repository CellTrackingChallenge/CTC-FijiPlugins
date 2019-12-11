/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;

import org.scijava.widget.FileWidget;
import java.io.File;

import de.mpicbg.ulman.ctc.workers.TRA;
import de.mpicbg.ulman.ctc.workers.TRA.PenaltyConfig;

@Plugin(type = Command.class, menuPath = "Plugins>Tracking>AOGM: Tracking measure",
        name = "CTC_AOGM", headless = true,
		  description = "Calculates the AOGM tracking performance measure from the AOGM paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/submission-of-results.html")
public class plugin_AOGMmeasure implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to computed result folder:",
		columns = 40, style = FileWidget.DIRECTORY_STYLE,
		description = "Path should contain result files directly: mask???.tif and res_track.txt")
	private File resPath;

	@Parameter(label = "Path to ground-truth folder:",
		columns = 40, style = FileWidget.DIRECTORY_STYLE,
		description = "Path should contain folder TRA and files: TRA/man_track???.tif and TRA/man_track.txt")
	private File gtPath;

	@Parameter(label = "Number of digits used in the image filenames:", min = "1",
		description = "Set to 3 if your files are, e.g., t000.tif, or to 5 if your files are, e.g., t00021.tif")
	public int noOfDigits = 3;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/submission-of-results.html";


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
		description = "Checks multiple consistency-oriented criteria on both input and GT data.")
	private boolean doConsistencyCheck = true;

	@Parameter(label = "Verbose report on tracking errors:",
		description = "Logs all discrepancies (and organizes them by category) between the input and GT data.")
	private boolean doLogReports = true;

	@Parameter(label = "Verbose report on matching of segments:",
		description = "Logs which RES/GT segment maps onto which GT/RES in the data.")
	private boolean doMatchingReports = false;

	@Parameter(label = "Do 1.0-min(AOGM,AOGM_empty)/AOGM_empty (TRA):",
		description = "The Cell Tracking Challenge TRA is exactly a normalized AOGM with specific penalties. If checked, returns between 0.0 to 1.0, higher is better.")
	private boolean doTRAnormalization = false;


	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = "Please, cite us:")
	private final String citationFooterA
		= "Matula P, Maška M, Sorokin DV, Matula P, Ortiz-de-Solórzano C, Kozubek M.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = ":")
	private final String citationFooterB
		= "Cell tracking accuracy measurement based on comparison of acyclic";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = ":")
	private final String citationFooterC
		= "oriented graphs. PloS one. 2015 Dec 18;10(12):e0144959.";


	@Parameter(type = ItemIO.OUTPUT)
	double AOGM = -1;

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
			tra.doLogReports       = doLogReports;
			tra.doMatchingReports  = doMatchingReports;
			tra.doAOGM             = (doTRAnormalization == false);
			tra.noOfDigits         = noOfDigits;

			//also the AOGM weights
			final PenaltyConfig penalty = tra.new PenaltyConfig(p1,p2,p3,p4,p5,p6);
			tra.penalty = penalty;

			//do the calculation
			AOGM = tra.calculate(gtPath.getPath(),resPath.getPath());

			//do not report anything explicitly (unless special format for parsing is
			//desired) as ItemIO.OUTPUT will make it output automatically
		}
		catch (RuntimeException e) {
			log.error("AOGM problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("AOGM error: "+e.getMessage());
		}
	}
}

/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2018 Vladimír Ulman
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
import java.util.Set;

import de.mpicbg.ulman.ctc.workers.DET;
import de.mpicbg.ulman.ctc.util.NumberSequenceHandler;

@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>Cell Tracking Challenge DET measure",
        name = "CTC_DET", headless = true,
		  description = "Calculates segmentation performance measure from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/submission-of-results.html")
public class plugin_DETmeasure implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to computed result folder:",
		columns = 40, style = FileWidget.DIRECTORY_STYLE,
		description = "Path should contain result files directly: mask???.tif")
	private File resPath;

	@Parameter(label = "Path to ground-truth folder:",
		columns = 40, style = FileWidget.DIRECTORY_STYLE,
		description = "Path should contain folder TRA and files: TRA/man_track???.tif")
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


	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false,
		label = "Select optional preferences:")
	private final String optionsHeader = "";

	@Parameter(label = "Do only these timepoints (e.g. 1-9,23,25):",
		description = "Comma separated list of numbers or intervals, interval is number-hyphen-number. Leave empty to have all images processed.",
		validater = "timePointsStrValidator")
	private String fileIdxStr = "";

	@Parameter(label = "Verbose report on tracking errors:",
		description = "Logs all discrepancies (and organizes them by category) between the input and GT data.")
	private boolean doLogReports = true;

	@Parameter(label = "Verbose report on matching of segments:",
		description = "Logs which RES/GT segment maps onto which GT/RES in the data.")
	private boolean doMatchingReports = false;



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


	//hidden output values
	@Parameter(type = ItemIO.OUTPUT)
	String RESdir;
	@Parameter(type = ItemIO.OUTPUT)
	String GTdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double DET = -1;


	@SuppressWarnings("unused")
	private void timePointsStrValidator()
	{
		//check the string is parse-able
		NumberSequenceHandler.toSet(fileIdxStr,null);
	}


	//the GUI path entry function:
	@Override
	public void run()
	{
		//saves the input paths for the final report table
		GTdir  = gtPath.getPath();
		RESdir = resPath.getPath();

		try {
			final DET det = new DET(log);
			det.doLogReports      = doLogReports;
			det.doMatchingReports = doMatchingReports;
			det.noOfDigits        = noOfDigits;

			Set<Integer> timePoints = NumberSequenceHandler.toSet(fileIdxStr);
			if (timePoints.size() > 0)
				det.doOnlyTheseTimepoints = timePoints;

			DET = det.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC DET measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC DET measure error: "+e.getMessage());
		}

		//do not report anything explicitly (unless special format for parsing is
		//desired) as ItemIO.OUTPUT will make it output automatically
	}
}

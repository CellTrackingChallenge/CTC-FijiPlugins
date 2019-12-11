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

import de.mpicbg.ulman.ctc.workers.TRA;

@Plugin(type = Command.class, menuPath = "Plugins>Tracking>Cell Tracking Challenge TRA measure",
        name = "CTC_TRA", headless = true,
		  description = "Calculates segmentation performance measure from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/submission-of-results.html")
public class plugin_TRAmeasure implements Command
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


	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false,
		label = "Select optional preferences:")
	private final String optionsHeader = "";

	@Parameter(label = "Do consistency check",
		description = "Checks multiple consistency-oriented criteria on both input and GT data before measuring TRA.")
	private boolean doConsistencyCheck = true;

	@Parameter(label = "Verbose report on tracking errors:",
		description = "Logs all discrepancies (and organizes them by category) between the input and GT data.")
	private boolean doLogReports = true;

	@Parameter(label = "Verbose report on matching of segments:",
		description = "Logs which RES/GT segment maps onto which GT/RES in the data.")
	private boolean doMatchingReports = false;



	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = "Please, cite us:")
	private final String citationFooterA
		= "Ulman V, Maška M, Magnusson KEG, ..., Ortiz-de-Solórzano C.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = ":")
	private final String citationFooterB
		= "An objective comparison of cell-tracking algorithms.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = ":")
	private final String citationFooterC
		= "Nature Methods. 2017. doi:10.1038/nmeth.4473";


	//hidden output values
	@Parameter(type = ItemIO.OUTPUT)
	String RESdir;
	@Parameter(type = ItemIO.OUTPUT)
	String GTdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double TRA = -1;


	//the GUI path entry function:
	@Override
	public void run()
	{
		//saves the input paths for the final report table
		GTdir  = gtPath.getPath();
		RESdir = resPath.getPath();

		try {
			final TRA tra = new TRA(log);
			tra.doConsistencyCheck = doConsistencyCheck;
			tra.doLogReports       = doLogReports;
			tra.doMatchingReports  = doMatchingReports;
			tra.noOfDigits         = noOfDigits;

			TRA = tra.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC TRA measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC TRA measure error: "+e.getMessage());
		}

		//do not report anything explicitly (unless special format for parsing is
		//desired) as ItemIO.OUTPUT will make it output automatically
	}
}

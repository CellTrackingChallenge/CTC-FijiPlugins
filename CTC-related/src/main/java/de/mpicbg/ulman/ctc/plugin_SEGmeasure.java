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

import de.mpicbg.ulman.ctc.workers.SEG;
import de.mpicbg.ulman.ctc.util.NumberSequenceHandler;

@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>Cell Tracking Challenge SEG measure",
        name = "CTC_SEG", headless = true,
		  description = "Calculates segmentation performance measure from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/submission-of-results.html")
public class plugin_SEGmeasure implements Command
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
		description = "Path should contain folder SEG and files: SEG/man_seg*.tif")
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

	@Parameter(label = "Do verbose logging",
		description = "Besides reporting the measure value itself, it also reports measurement details that lead to this value.")
	private boolean optionVerboseLogging = true;

	@Parameter(label = "Report also result labels",
		description = "The output report normally reviews only all ground-truth labels. If enabled, information about all result labels is given too.")
	private boolean optionReportAllResultLabels = false;

	@Parameter(label = "Report (and stop) on empty images",
		description = "The calculation stops whenever an empty (only pixels with zero value) image is found either among the ground-truth or result images.")
	private boolean optionStopOnEmptyImages = false;


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
	double SEG = -1;


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
			final SEG seg = new SEG(log);
			seg.doLogReports = optionVerboseLogging;
			seg.doAllResReports = optionReportAllResultLabels;
			seg.doStopOnEmptyImages = optionStopOnEmptyImages;
			seg.noOfDigits = noOfDigits;

			Set<Integer> timePoints = NumberSequenceHandler.toSet(fileIdxStr);
			if (timePoints.size() > 0)
				seg.doOnlyTheseTimepoints = timePoints;

			SEG = seg.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC SEG measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC SEG measure error: "+e.getMessage());
		}

		//do not report anything explicitly (unless special format for parsing is
		//desired) as ItemIO.OUTPUT will make it output automatically
	}
}

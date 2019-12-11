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

import de.mpicbg.ulman.ctc.workers.TrackDataCache;
import de.mpicbg.ulman.ctc.workers.CT;
import de.mpicbg.ulman.ctc.workers.TF;
import de.mpicbg.ulman.ctc.workers.BCi;
import de.mpicbg.ulman.ctc.workers.CCA;

@Plugin(type = Command.class, menuPath = "Plugins>Cell Tracking Challenge>Biological measures",
        name = "CTC_BIO", headless = true,
		  description = "Calculates biological tracking performance measures from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/submission-of-results.html")
public class plugin_CTCmeasuresBIO implements Command
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
		label = "Select measures to calculate:")
	private final String measuresHeader = "";

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
		description = "Value of 'i' for which the BC(i) should be reported.")
	private int iForBCi = 2;

	@Parameter(label = "CCA",
		description = "Reflects the ability of an algorithm to discover true distribution of cell cycle lengths in a video.")
	private boolean calcCCA = true;


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
		//saves the input paths for the final report table
		GTdir  = gtPath.getPath();
		RESdir = resPath.getPath();

		//reference on a shared object that does
		//pre-fetching of data and some common pre-calculation
		TrackDataCache cache = new TrackDataCache(log);
		cache.noOfDigits = noOfDigits;

		if (calcCT )
		{
			try {
				final CT ct = new CT(log);
				CT = ct.calculate(GTdir, RESdir, cache);
				cache = ct.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC CT measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC CT measure error: "+e.getMessage());
			}
		}

		if (calcTF )
		{
			try {
				final TF tf = new TF(log);
				TF = tf.calculate(GTdir, RESdir, cache);
				cache = tf.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC TF measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC TF measure error: "+e.getMessage());
			}
		}

		if (calcBCi)
		{
			try {
				final BCi bci = new BCi(log);
				if (calcBCi) bci.setI(iForBCi);
				BCi = bci.calculate(GTdir, RESdir, cache);
				cache = bci.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC BC(i) measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC BC(i) measure error: "+e.getMessage());
			}
		}

		if (calcCCA)
		{
			try {
				final CCA cca = new CCA(log);
				CCA = cca.calculate(GTdir, RESdir, cache);
				cache = cca.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC CCA measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC CCA measure error: "+e.getMessage());
			}
		}

		//do not report anything explicitly (unless special format for parsing is
		//desired) as ItemIO.OUTPUT will make it output automatically
	}
}

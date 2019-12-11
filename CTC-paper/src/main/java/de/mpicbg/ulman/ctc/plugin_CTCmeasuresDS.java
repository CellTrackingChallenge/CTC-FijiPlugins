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

import de.mpicbg.ulman.ctc.workers.ImgQualityDataCache;
import de.mpicbg.ulman.ctc.workers.SNR;
import de.mpicbg.ulman.ctc.workers.CR;
import de.mpicbg.ulman.ctc.workers.HETI;
import de.mpicbg.ulman.ctc.workers.HETB;
import de.mpicbg.ulman.ctc.workers.RES;
import de.mpicbg.ulman.ctc.workers.DEN;
import de.mpicbg.ulman.ctc.workers.CHA;
import de.mpicbg.ulman.ctc.workers.OVE;
import de.mpicbg.ulman.ctc.workers.MIT;
/*
import de.mpicbg.ulman.ctc.workers.SHA;
import de.mpicbg.ulman.ctc.workers.SYN;
import de.mpicbg.ulman.ctc.workers.ENTLEAV;
*/

@Plugin(type = Command.class, menuPath = "Plugins>Cell Tracking Challenge>Dataset measures",
        name = "CTC_DS", headless = true,
		  description = "Calculates dataset quality measures from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/submission-of-results.html")
public class plugin_CTCmeasuresDS implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to images folder:",
		columns = 40, style = FileWidget.DIRECTORY_STYLE,
		description = "Path should contain cell image files directly: t???.tif")
	private File imgPath;

	@Parameter(label = "Number of digits used in the image filenames:", min = "1",
		description = "Set to 3 if your files are, e.g., t000.tif, or to 5 if your files are, e.g., t00021.tif")
	public int noOfDigits = 3;

	@Parameter(label = "Resolution (um/px) of the images, x-axis:",
		min = "0.0001", stepSize = "0.1",
		description = "Size of single pixel/voxel along the x-axis in micrometers.")
	double xRes = 1.0;

	@Parameter(label = "y-axis:",
		min = "0.0001", stepSize = "0.1",
		description = "Size of single pixel/voxel along the y-axis in micrometers.")
	double yRes = 1.0;

	@Parameter(label = "z-axis:",
		min = "0.0001", stepSize = "0.1",
		description = "Size of single pixel/voxel along the z-axis in micrometers.")
	double zRes = 1.0;

	@Parameter(label = "Path to annotations folder:",
		columns = 40, style = FileWidget.DIRECTORY_STYLE,
		description = "Path should contain folders BG and TRA and annotation files: "
			+ "BG/mask???.tif, TRA/man_track???.tif and man_track.txt. "
			+ "The TRA/man_track???.tif must provide realistic masks of cells (not just blobs representing centres etc.).")
	private File annPath;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/submission-of-results.html";


	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false,
		label = "Select measures to calculate:")
	private final String measuresHeader = "";

	@Parameter(label = "SNR",
		description = "Evaluates the average signal to noise ratio over all annotated cells.")
	private boolean calcSNR = true;

	@Parameter(label = "CR",
		description = "Evaluates the average contrast ratio over all annotated cells.")
	private boolean calcCR = true;

	@Parameter(label = "Heti",
		description = "Evaluates the average internal signal heterogeneity of the cells.")
	private boolean calcHeti = true;

	@Parameter(label = "Hetb",
		description = "Evaluates the heterogeneity (as standard deviation) of the signal between cells.")
	private boolean calcHetb = true;

	@Parameter(label = "Res",
		description = "Evaluates the average resolution, measured as the average size of the cells in number of pixels (2D) or voxels (3D).")
	private boolean calcRes = true;

	/*
	@Parameter(label = "Sha",
		description = "Evaluates the average regularity of the cell shape, normalized between 0 (completely irregular) and 1 (perfectly regular).")
	*/
	private boolean calcSha = false;

	@Parameter(label = "Den",
		description = "Evaluates the cell density measured as average minimum pixel (2D) or voxel (3D) distance between cells.")
	private boolean calcDen = true;

	@Parameter(label = "Cha",
		description = "Evaluates the absolute change of the average intensity of the cells with time.")
	private boolean calcCha = true;

	@Parameter(label = "Ove",
		description = "Evaluates the average level of overlap of the cells in consecutive frames, normalized between 0 (no overlap) and 1 (complete overlap).")
	private boolean calcOve = true;

	@Parameter(label = "Mit",
		description = "Evaluates the average number of division events per frame.")
	private boolean calcMit = true;

	/*
	@Parameter(label = "Syn",
		description = "Evaluates the foo.")
	private boolean calcSyn = true;

	@Parameter(label = "EntLeav",
		description = "Evaluates the foo.")
	private boolean calcEntLeav = true;
	*/


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
	String IMGdir;
	@Parameter(type = ItemIO.OUTPUT)
	String ANNdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double SNR = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double CR = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Heti = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Hetb = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Res = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Sha = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Den = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Cha = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Ove = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Mit = -1;

	/*
	@Parameter(type = ItemIO.OUTPUT)
	byte Syn = -1;

	@Parameter(type = ItemIO.OUTPUT)
	byte EntLeav = -1;
	*/


	//the GUI path entry function:
	@Override
	public void run()
	{
		//store the resolution information
		final double[] resolution = new double[3];
		resolution[0] = xRes;
		resolution[1] = yRes;
		resolution[2] = zRes;

		//saves the input paths for the final report table
		IMGdir = imgPath.getPath();
		ANNdir = annPath.getPath();

		//reference on a shared object that does
		//pre-fetching of data and some common pre-calculation
		//
		//create an "empty" object and tell it what features we wanna calculate,
		//the first measure to be calculated will recognize that this object does not fit
		//and will make a new one that fits and will retain the flags of demanded features
		ImgQualityDataCache cache = new ImgQualityDataCache(log);
		if (calcDen) cache.doDensityPrecalculation = true;
		if (calcSha) cache.doShapePrecalculation = true;
		cache.noOfDigits = noOfDigits;

		//do the calculation and retrieve updated cache afterwards
		if (calcSNR)
		{
			try {
				final SNR snr = new SNR(log);
				SNR = snr.calculate(IMGdir, resolution, ANNdir, cache);
				cache = snr.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC SNR measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC SNR measure error: "+e.getMessage());
			}
		}

		if (calcCR)
		{
			try {
				final CR cr = new CR(log);
				CR = cr.calculate(IMGdir, resolution, ANNdir, cache);
				cache = cr.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC CR measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC CR measure error: "+e.getMessage());
			}
		}

		if (calcHeti)
		{
			try {
				final HETI heti = new HETI(log);
				Heti = heti.calculate(IMGdir, resolution, ANNdir, cache);
				cache = heti.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Heti measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Heti measure error: "+e.getMessage());
			}
		}

		if (calcHetb)
		{
			try {
				final HETB hetb = new HETB(log);
				Hetb = hetb.calculate(IMGdir, resolution, ANNdir, cache);
				cache = hetb.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Hetb measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Hetb measure error: "+e.getMessage());
			}
		}

		if (calcRes)
		{
			try {
				final RES res = new RES(log);
				Res = res.calculate(IMGdir, resolution, ANNdir, cache);
				cache = res.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Res measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Res measure error: "+e.getMessage());
			}
		}

		/*
		if (calcSha)
		{
			try {
				final SHA sha = new SHA(log);
				Sha = sha.calculate(IMGdir, resolution, ANNdir, cache);
				cache = sha.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Sha measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Sha measure error: "+e.getMessage());
			}
		}
		*/

		if (calcDen)
		{
			try {
				final DEN den = new DEN(log);
				Den = den.calculate(IMGdir, resolution, ANNdir, cache);
				cache = den.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Den measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Den measure error: "+e.getMessage());
			}
		}

		if (calcCha)
		{
			try {
				final CHA cha = new CHA(log);
				Cha = cha.calculate(IMGdir, resolution, ANNdir, cache);
				cache = cha.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Cha measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Cha measure error: "+e.getMessage());
			}
		}

		if (calcOve)
		{
			try {
				final OVE ove = new OVE(log);
				Ove = ove.calculate(IMGdir, resolution, ANNdir, cache);
				cache = ove.getCache();
			}
			catch (RuntimeException e) {
				log.error("CTC Ove measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Ove measure error: "+e.getMessage());
			}
		}

		if (calcMit)
		{
			try {
				final MIT mit = new MIT(log);
				Mit = mit.calculate(null,null, ANNdir);
			}
			catch (RuntimeException e) {
				log.error("CTC Mit measure problem: "+e.getMessage());
			}
			catch (Exception e) {
				log.error("CTC Mit measure error: "+e.getMessage());
			}
		}

		//do not report anything explicitly (unless special format for parsing is
		//desired) as ItemIO.OUTPUT will make it output automatically
	}
}

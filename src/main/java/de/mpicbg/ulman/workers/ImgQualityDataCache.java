/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import net.imglib2.img.Img;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import io.scif.img.ImgIOException;

import java.util.Vector;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class ImgQualityDataCache
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public ImgQualityDataCache(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	///GT and RES paths combination for which this cache is valid, null means invalid
	private String imgPath = null;
	///GT and RES paths combination for which this cache is valid, null means invalid
	private String annPath = null;

	///reference-based-only check if the parameters are those on which this cache was computed
	public boolean validFor(final String _imgPath, final String _annPath)
	{
		return (imgPath == _imgPath && annPath == _annPath);
	}


	// ----------- the common upper stage essentially starts here -----------
	//auxiliary data:

	///representation of resolution, no dimensionality restriction (unlike in GUI)
	private double[] resolution = null;

	public void setResolution(final double[] _res)
	{
		//check if resolution data is sane
		if (_res == null)
			throw new IllegalArgumentException("No pixel resolution data supplied!");
		for (double r : _res)
			if (r <= 0.0)
				throw new IllegalArgumentException("Negative or zero resolution supplied!");

		//copy the supplied resolution to the class structures,
		resolution = new double[_res.length];
		for (int n=0; n < resolution.length; ++n)
			resolution[n] = _res[n];
	}

	/**
	 * Representation of average & std. deviations within individual
	 * foreground masks.
	 * Usage: avgFG[timePoint].get(labelID) = averageIntensityValue
	 */
	public final Vector<HashMap<Integer,Double>> avgFG = new Vector<>(1000,100);
	/// similar to this.avgFG
	public final Vector<HashMap<Integer,Double>> stdFG = new Vector<>(1000,100);

	/**
	 * Stores how many voxels are there in the intersection of masks of the same
	 * marker at time point and previous time point.
	 */
	public final Vector<HashMap<Integer,Long>> overlapFG = new Vector<>(1000,100);

	/// Stores volumes of the FG masks at time points.
	public final Vector<HashMap<Integer,Long>> volumeFG = new Vector<>(1000,100);

	/// Stores surfaces of the FG masks at time points.
	public final Vector<HashMap<Integer,Long>> surfaceFG = new Vector<>(1000,100);

	/**
	 * Representation of average & std. deviations of background region.
	 * There is only one background marker expected in the images.
	 */
	public final Vector<Double> avgBG = new Vector<>(1000,100);
	/// similar to this.avgBG
	public final Vector<Double> stdBG = new Vector<>(1000,100);

	//---------------------------------------------------------------------/
	//aux data fillers -- merely markers' properties calculator

	public void ClassifyLabels(IterableInterval<UnsignedShortType> img,
	                           RandomAccessibleInterval<UnsignedShortType> imgFG,
	                           RandomAccessibleInterval<UnsignedByteType> imgBG)
	{
		//uses resolution from the class internal structures, check it is set already
		if (resolution == null)
			throw new IllegalArgumentException("No pixel resolution data is available!");
		//assume that resolution is sane

		//check we have a resolution data available for every dimension
		if (img.numDimensions() > resolution.length)
			throw new IllegalArgumentException("Raw image has greater dimensionality"
				+" than the available resolution data.");

		//check the sizes of the images
		if (img.numDimensions() != imgFG.numDimensions())
			throw new IllegalArgumentException("Raw image and FG label image"
				+" are not of the same dimensionality.");
		if (img.numDimensions() != imgBG.numDimensions())
			throw new IllegalArgumentException("Raw image and BG label image"
				+" are not of the same dimensionality.");

		for (int n=0; n < img.numDimensions(); ++n)
			if (img.dimension(n) != imgFG.dimension(n))
				throw new IllegalArgumentException("Raw image and FG label image"
					+" are not of the same size.");
		for (int n=0; n < img.numDimensions(); ++n)
			if (img.dimension(n) != imgBG.dimension(n))
				throw new IllegalArgumentException("Raw image and BG label image"
					+" are not of the same size.");

		//.... populate the internal structures ....
	}

	//---------------------------------------------------------------------/
	/**
	 * Measure calculation happens in two stages. The first/upper stage does
	 * data pre-fetch and calculations to populate the TrackDataCache.
	 * TrackDataCache.calculate() actually does this job. The sort of
	 * calculations is such that the other measures could benefit from
	 * it and re-use it (instead of loading images again and doing some
	 * calculations again), and the results are stored in the cache.
	 * The second/bottom stage is measure-specific. It basically finishes
	 * the measure calculation procedure, possible using data from the cache.
	 *
	 * This function computes the common upper stage of measures.
	 */
	public void calculate(final String imgPath, final double[] resolution,
	                      final String annPath)
	throws IOException, ImgIOException
	{
		log.info("IMG path: "+imgPath);
		log.info("ANN path: "+annPath);
		//DEBUG//
		log.info("Computing the common upper part...");

		//test and save the given resolution
		setResolution(resolution);

		//we gonna re-use image loading functions...
		final TrackDataCache tCache = new TrackDataCache(log);

		//iterate through the RAW images folder and read files, one by one,
		//find the appropriate file in the annotations folders,
		//and call ClassifyLabels() for every such tripple
		int time = 0;
		while (Files.isReadable(
			new File(String.format("%s/t%03d.tif",imgPath,time)).toPath()))
		{
			//read the image tripple (raw image, FG labels, BG label)
			Img<UnsignedShortType> img
				= tCache.ReadImage(String.format("%s/t%03d.tif",imgPath,time));

			Img<UnsignedShortType> imgFG
				= tCache.ReadImage(String.format("%s/TRA/man_track%03d.tif",annPath,time));

			Img<UnsignedByteType> imgBG
				= tCache.ReadImageG8(String.format("%s/BG/mask%03d.tif",annPath,time));

			ClassifyLabels(img, imgFG, imgBG);
			++time;

			//to be on safe side (with memory)
			img = null;
			imgFG = null;
			imgBG = null;
		}

		if (time == 0)
			throw new IllegalArgumentException("No raw image was found!");

		if (volumeFG.size() != time)
			throw new IllegalArgumentException("Internal consistency problem with FG data!");

		if (avgBG.size() != time)
			throw new IllegalArgumentException("Internal consistency problem with BG data!");

		//now that we got here, note for what data
		//this cache is valid, see validFor() above
		this.imgPath = imgPath;
		this.annPath = annPath;
	}
}

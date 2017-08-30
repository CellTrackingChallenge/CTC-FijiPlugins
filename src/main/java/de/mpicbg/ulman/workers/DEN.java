/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

import de.mpicbg.ulman.workers.ImgQualityDataCache;

public class DEN
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public DEN(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	///reference on cache that we used recently
	private ImgQualityDataCache cache = null;

	///to provide the cache to others/to share it with others
	public ImgQualityDataCache getCache()
	{ return (cache); }


	// ----------- the DEN essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double den = 0.0;


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
	 * This function is asked to use, if applicable, such cache data
	 * as the caller believes the given cache is still valid. The measure
	 * can only carry on with the bottom stage then (thus being overall faster
	 * than when computing both stages).
	 *
	 * The class never re-uses its own cache to allow for fresh complete
	 * re-calculation on the (new) data in the same folders.
	 *
	 * This is the main DEN calculator.
	 */
	public double calculate(final String imgPath, final double[] resolution,
	                        final String annPath,
	                        final ImgQualityDataCache _cache)
	throws IOException, ImgIOException
	{
		//invalidate own cache
		cache = null;

		//check we got some hint/cache
		//and if it fits our input, then use it
		if (_cache != null && _cache.validFor(imgPath,annPath)) cache = _cache;

		//if no cache is available after all, compute it
		if (cache == null)
		{
			//do the upper stage
			cache = new ImgQualityDataCache(log);
			cache.calculate(imgPath, resolution, annPath);
		}

		//do the bottom stage
		//DEBUG//
		log.info("Computing the DEN bottom part...");
		den = 0.0;

		//shadows of the/short-cuts to the cache data
		final Vector<HashMap<Integer,Double>> avgFG = cache.avgFG;
		final Vector<Double> avgBG = cache.avgBG;
		final Vector<Double> stdBG = cache.stdBG;

		//go over all FG objects and calc their DENs
		long noFGs = 0;
		//over all time points
		for (int time=0; time < avgFG.size(); ++time)
		{
			//over all objects, in fact use their avg intensities
			for (Double fg : avgFG.get(time).values())
			{
				den += (fg - avgBG.get(time)) / stdBG.get(time);
				++noFGs;
			}
		}

		//finish the calculation of the average
		if (noFGs > 0)
		{
			den /= (double)noFGs;
			log.info("DEN: "+den);
		}
		else
			log.info("DEN: Couldn't calculate average DEN because there are no cells labelled.");

		return (den);
	}

	/// This is the wrapper DEN calculator, assuring complete re-calculation.
	public double calculate(final String imgPath, final double[] resolution,
	                        final String annPath)
	throws IOException, ImgIOException
	{
		return this.calculate(imgPath, resolution, annPath, null);
	}
}

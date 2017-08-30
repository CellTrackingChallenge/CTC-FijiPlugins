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
import java.util.HashMap;

import de.mpicbg.ulman.workers.ImgQualityDataCache;

public class CHA
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public CHA(final LogService _log)
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


	// ----------- the CHA essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double cha = 0.0;


	//---------------------------------------------------------------------/
	/**
	 * The function returns average FG intensity over all objects found that
	 * are present at time points (inclusive) \e from till \e to.
	 * Returns -1 if no object has been found at all.
	 */
	private double avgFGfromTimeSpan(final int from, final int to)
	{
		//shadows of the/short-cuts to the cache data
		final Vector<HashMap<Integer,Double>> avgFG = cache.avgFG;

		if (from < 0 || from >= avgFG.size()) return (-1.0);
		if ( to  < 0 ||  to  >= avgFG.size()) return (-1.0);

		double avg = 0.0;
		int cnt = 0;

		for (int time = from; time <= to; ++time)
		{
			for (Double fg : avgFG.get(time).values())
			{
				avg += fg;
				++cnt;
			}
		}

		return (cnt > 0 ? avg/(double)cnt : -1.0);
	}


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
	 * This is the main CHA calculator.
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
		//DEBUG//log.info("Computing the CHA bottom part...");
		cha = 0.0;

		//shadows of the/short-cuts to the cache data
		final Vector<HashMap<Integer,Double>> avgFG = cache.avgFG;

		//TODO: process pairwise the image sequence and consider only
		//      pairs where both images have some FG labels, hence
		//      CHA can be calculated for such pair, and take the average
		//      from this (and complain if no such pair has been detected)

		double a = -1.0, b = -1.0;

		if (avgFG.size() < 2)
		{
			throw new IllegalArgumentException("Cannot calculate CHA from less than two images.");
		}
		else
		if (avgFG.size() == 2)
		{
			a = avgFGfromTimeSpan(0,0);
			b = avgFGfromTimeSpan(1,1);
			cha = b - a;
		}
		else
		if (avgFG.size() == 3)
		{
			//use largest possible (still overlapping, though) window
			//windows size = 2 time points
			a = avgFGfromTimeSpan(0,1);
			b = avgFGfromTimeSpan(1,2);
			cha = b - a;
			cha /= 2.0;
		}
		else
		//if (avgFG.size() > 3)
		{
			//use largest possible (possibly overlapping, though) window
			//windows size = 3 time points
			final int last = avgFG.size() - 1;
			a = avgFGfromTimeSpan(0,2);
			b = avgFGfromTimeSpan(last-2,last);
			cha = b - a;
			cha /= (double)last;
		}

		if (a < 0.0 || b < 0.0)
			throw new IllegalArgumentException("Current CHA implementation cannot deal "
				+"with images with no FG labels.");

		log.info("CHA_debug: avg. int. "+a+" -> "+b+", over "+avgFG.size()+" frames");
		log.info("CHA: "+cha);
		return (cha);
	}

	/// This is the wrapper CHA calculator, assuring complete re-calculation.
	public double calculate(final String imgPath, final double[] resolution,
	                        final String annPath)
	throws IOException, ImgIOException
	{
		return this.calculate(imgPath, resolution, annPath, null);
	}
}

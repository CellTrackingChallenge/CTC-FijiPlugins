/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.util.Vector;
import java.util.HashMap;

import de.mpicbg.ulman.workers.ImgQualityDataCache;

public class HETB
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public HETB(final LogService _log)
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


	// ----------- the HETB essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double hetb = 0.0;


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
	 * This is the main HETB calculator.
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
		log.info("Computing the HETB bottom part...");
		hetb = 0.0;

		double intSum = 0.; //for mean and variance
		double int2Sum = 0.;
		//see ImgQualityDataCache.extractFGObjectStats() for explanation of this variable
		double valShift=-1.;

		//shadows of the/short-cuts to the cache data
		final Vector<HashMap<Integer,Double>> avgFG = cache.avgFG;
		final Vector<Double> avgBG = cache.avgBG;

		//go over all FG objects and calc their HETBs
		long noFGs = 0;
		//over all time points
		for (int time=0; time < avgFG.size(); ++time)
		{
			//skip this frame if it is empty
			if (avgFG.get(time).size() == 0) continue;

			//get average signal height from all objects in the given frame
			//NB: the denominator of the HETb_i,t expression
			double frameAvgFGSignal = 0.0;
			for (Double fg : avgFG.get(time).values())
				frameAvgFGSignal += Math.abs(fg - avgBG.get(time));
			frameAvgFGSignal /= (double)avgFG.get(time).size();

			//over all objects, in fact use their avg intensities
			for (Double fg : avgFG.get(time).values())
			{
				//object signal height "normalized" with respect to the
				//usual signal height in this frame, we have to calculate
				//std.dev. from these values
				hetb = Math.abs(fg - avgBG.get(time)) / frameAvgFGSignal;

				if (valShift == -1) valShift = hetb;

				intSum += (hetb-valShift);
				int2Sum += (hetb-valShift) * (hetb-valShift);
				++noFGs;
			}
		}

		//finish the calculation of the average
		if (noFGs > 0)
		{
			//finish calculation of the variance...
			int2Sum -= (intSum*intSum/(double)noFGs);
			int2Sum /= (double)noFGs;

			//...to get the final standard deviation
			hetb = Math.sqrt(int2Sum);

			log.info("HETB: "+hetb);
		}
		else
			log.info("HETB: Couldn't calculate average HETB because there are missing labels.");

		return (hetb);
	}

	/// This is the wrapper HETB calculator, assuring complete re-calculation.
	public double calculate(final String imgPath, final double[] resolution,
	                        final String annPath)
	throws IOException, ImgIOException
	{
		return this.calculate(imgPath, resolution, annPath, null);
	}
}

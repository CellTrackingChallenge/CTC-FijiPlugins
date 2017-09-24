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
import de.mpicbg.ulman.workers.ImgQualityDataCache.videoDataContainer;

public class SNR
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public SNR(final LogService _log)
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


	// ----------- the SNR essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double snr = 0.0;


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
	 * This is the main SNR calculator.
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
			cache = new ImgQualityDataCache(log, _cache);
			cache.calculate(imgPath, resolution, annPath);
		}

		//do the bottom stage
		//DEBUG//log.info("Computing the SNR bottom part...");
		snr = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. SNRs and average them
		for (videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Double>> avgFG = data.avgFG;
			final Vector<Double> avgBG = data.avgBG;
			final Vector<Double> stdBG = data.stdBG;

			//go over all FG objects and calc their SNRs
			long noFGs = 0;
			double l_snr = 0.; //local snr

			//over all time points
			for (int time=0; time < avgFG.size(); ++time)
			{
				//skip this frame if we cannot compute anything on it
				if (stdBG.get(time) == 0.0) continue;

				//over all objects, in fact use their avg intensities
				for (Double fg : avgFG.get(time).values())
				{
					l_snr += Math.abs(fg - avgBG.get(time)) / stdBG.get(time);
					++noFGs;
				}
			}

			//finish the calculation of the local average SNR
			if (noFGs > 0)
			{
				l_snr /= (double)noFGs;
				log.info("SNR for video "+data.video+": "+l_snr);

				snr += l_snr;
				++videoCnt;
			}
			else
				log.info("SNR for video "+data.video+": Couldn't calculate average SNR because there are missing labels.");
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			snr /= (double)videoCnt;
			log.info("SNR for dataset: "+snr);
		}
		else
			log.info("SNR for dataset: Couldn't calculate average SNR because there are missing labels.");

		return (snr);
	}

	/// This is the wrapper SNR calculator, assuring complete re-calculation.
	public double calculate(final String imgPath, final double[] resolution,
	                        final String annPath)
	throws IOException, ImgIOException
	{
		return this.calculate(imgPath, resolution, annPath, null);
	}
}

/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.workers;

import org.scijava.log.LogService;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.util.Vector;
import java.util.HashMap;

import de.mpicbg.ulman.ctc.workers.TrackDataCache.Track;
import de.mpicbg.ulman.ctc.workers.TrackDataCache.Fork;

public class CCA
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public CCA(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	///reference on cache that we used recently
	private TrackDataCache cache = null;

	///to provide the cache to others/to share it with others
	public TrackDataCache getCache()
	{ return (cache); }


	// ----------- the CCA essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double cca = 0.0;


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
	 * This is the main CCA calculator.
	 */
	public double calculate(final String gtPath, final String resPath,
	                        final TrackDataCache _cache)
	throws IOException, ImgIOException
	{
		//invalidate own cache
		cache = null;

		//check we got some hint/cache
		//and if it fits our input, then use it
		if (_cache != null && _cache.validFor(gtPath,resPath)) cache = _cache;

		//if no cache is available after all, compute it
		if (cache == null)
		{
			//do the upper stage
			cache = new TrackDataCache(log);
			cache.calculate(gtPath,resPath);
		}

		//do the bottom stage
		//DEBUG//log.info("Computing the CCA bottom part...");
		cca = 0.0;

		//shadows of the/short-cuts to the cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final HashMap<Integer,Track> res_tracks = cache.res_tracks;

		final Vector<Fork> gt_forks  = cache.gt_forks;
		final Vector<Fork> res_forks = cache.res_forks;

		//detects complete cell cycles and save frequency histogram of their
		//lengths/durations, complete cell cycle corresponds to a track whose
		//begin and end is evidenced in the data, i.e. we see the whole
		//life of a cell from its birth till its death/division
		//
		//this we do by looking for tracks that connect two branching events

		//number of detected complete cell cycles
		int gt_count=0;
		int res_count=0;

		//maximum length spotted
		int maxLength = 0;

		//frequency histograms of their lengths
		HashMap<Integer,Integer> gt_lenHist  = new HashMap<>();
		HashMap<Integer,Integer> res_lenHist = new HashMap<>();

		//now, scan all GT branching events
		for (Fork parent : gt_forks) //later/ending point of some track
		for (Fork child  : gt_forks) //earlier branching (may have many daughters)
		for (int i=0; i < child.m_child_ids.length; ++i) //earlier/starting point of some track
		{
			//here we consider all combinations of starting vs. ending track points,
			//see if the connection is realized with the same track
			if (child.m_child_ids[i] == parent.m_parent_id)
			{
				//detected connecting track, its id and its duration/length
				final int id = parent.m_parent_id;
				final int length = gt_tracks.get(id).m_end - gt_tracks.get(id).m_begin +1;

				//add the length to the histogram
				Integer count = gt_lenHist.get(length);
				gt_lenHist.put(length, count == null ? 1 : count+1);

				//updated the max length observed so far
				if (length > maxLength) maxLength = length;

				//another complete cycle detected...
				++gt_count;
			}
		}

		//the same for RES branching events and tracks
		for (Fork parent : res_forks)
		for (Fork child  : res_forks)
		for (int i=0; i < child.m_child_ids.length; ++i)
		{
			if (child.m_child_ids[i] == parent.m_parent_id)
			{
				//detected connecting track, its id and its duration/length
				final int id = parent.m_parent_id;
				final int length = res_tracks.get(id).m_end - res_tracks.get(id).m_begin +1;

				Integer count = res_lenHist.get(length);
				res_lenHist.put(length, count == null ? 1 : count+1);

				if (length > maxLength) maxLength = length;

				++res_count;
			}
		}

		//do some overview reports on the situation in the data
		log.info("---");
		log.info("Number of complete cell cycles in reference (ground truth) tracks: "+gt_count);
		log.info("Number of complete cell cycles in computed (result) tracks       : "+res_count);

		if (gt_count == 0)
			throw new IllegalArgumentException("GT tracking data show no complete cell cycle!");

		if (res_count > 0)
		{
			//now, calculate the CCA

			//accumulate sums for both histograms, respectively, so that
			//we can move into "domain of probabilities" from "frequency counts"
			long gt_sum = 0;
			for (Integer count : gt_lenHist.values()) gt_sum += count;

			long res_sum = 0;
			for (Integer count : res_lenHist.values()) res_sum += count;

			//with (for example) gt_lenHist[i] and gt_sum we can construct
			//gt_CDF[i] = ( SUM_j=0..i gt_lenHist[j] ) / gt_sum
			//and measure maximum difference between two such CDFs -- which is 1-CCA

			double maxDiff = 0.0;
			long gt_cumm = 0; //gt_CDF[] on the fly...
			long res_cumm = 0;

			//over all observed lengths
			for (int len = 0; len <= maxLength; ++len)
			{
				//calculate gt_CDF[len]
				Integer c = gt_lenHist.get(len);
				gt_cumm += c == null ? 0 : c;

				c = res_lenHist.get(len);
				res_cumm += c == null ? 0 : c;

				//see the difference between the two CDFs[len]
				double diff  = (double)gt_cumm  / (double)gt_sum;
				       diff -= (double)res_cumm / (double)res_sum;
				diff = Math.abs(diff);

				if (diff > maxDiff) maxDiff = diff;
			}

			cca = 1.0 - maxDiff;
		}
		//else: no complete cell cycle in RES data is defined as CCA = 0

		log.info("CCA: "+cca);
		return (cca);
	}

	/// This is the wrapper CCA calculator, assuring complete re-calculation.
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		return this.calculate(gtPath,resPath,null);
	}
}

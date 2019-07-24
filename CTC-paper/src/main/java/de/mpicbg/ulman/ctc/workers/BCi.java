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
import java.util.Map;
import java.util.HashMap;

import de.mpicbg.ulman.ctc.workers.TrackDataCache.Track;
import de.mpicbg.ulman.ctc.workers.TrackDataCache.TemporalLevel;
import de.mpicbg.ulman.ctc.workers.TrackDataCache.Fork;

public class BCi
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public BCi(final LogService _log)
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


	// ----------- the BCi essentially starts here -----------
	//auxiliary data:

	///the value for which we want to calculate this measure
	private int desiredI = 2;

	public void setI(final int i)
	{
		if (i < 0 || i > 5)
			throw new IllegalArgumentException("BC(i) parameter 'i' must be (inclusive) between 0 and 5!");

		desiredI = i;
	}

	///the to-be-calculated measure value (for 'i' = desiredI)
	private double bcI = 0.0;


	/**
	 * Check if there is a GT branching event (in gt_forks) that can be considered
	 * matching the given input branching event (res_fork) with given temporal
	 * window maxI (in units of number of frame, number of time points).
	 */
	private boolean CorrectFork(final int maxI,
		//examined res branching event
		final Fork res_fork,
		//all GT branching events with "was recovered already" flag
		final Vector<Fork> gt_forks, final boolean[] gt_correct,
		final Map<Integer,Track> gt_tracks,
		final Map<Integer,Track> res_tracks,
		final Vector<TemporalLevel> levels)
	{
		if (gt_forks.size() != gt_correct.length)
			throw new IllegalArgumentException(
				"Arrays of both GT forks and their flags must be of the same length!");

		//scan over all GT forks and find one (for detailed examination) candidate
		//that has not been "recovered" already and has the same number of children
		//as the input/testing RES fork
		for (int i=0; i < gt_correct.length; ++i)
		{
			//shortcut to the examined GT fork
			final Fork gt_fork = gt_forks.get(i);
			if (!gt_correct[i] && gt_fork.m_child_ids.length == res_fork.m_child_ids.length)
			{
				//candidate found...

				//check if parent nodes overlap at the latest time in which both parents existed
				int GTtime = gt_tracks.get( gt_fork.m_parent_id).m_end;
				int Rtime = res_tracks.get(res_fork.m_parent_id).m_end;
				int consideredTime = Math.min(GTtime, Rtime); //the latest common time point

				//check the overlap (temporal distance and spatial overlap)
				boolean match = ( Math.abs(GTtime - Rtime) <= maxI  &&  cache.UniqueMatch(
					gt_fork.m_parent_id, res_fork.m_parent_id, levels.get(consideredTime)) );

				//now, do the same test for all kids
				//(iterate over every GT and ideally always find some RES -- since
				//the number of kids is the same and we test for spatial uniqueness,
				//this (one-way test) suffices to declare GTkids = RESkids, or the opposite)
				//
				//over all GT kids
				for (int k=0; k < gt_fork.m_child_ids.length && match; ++k)
				{
					GTtime = gt_tracks.get(gt_fork.m_child_ids[k]).m_begin;

					//over all RES kids, until a match is found
					match = false;
					for (int l=0; l < res_fork.m_child_ids.length && !match; ++l)
					{
						Rtime = res_tracks.get(res_fork.m_child_ids[l]).m_begin;
						consideredTime = Math.max(GTtime, Rtime); //the earliest common time point

						//check the overlap (temporal distance and spatial overlap)
						match = ( Math.abs(GTtime - Rtime) <= maxI  &&  cache.UniqueMatch(
							gt_fork.m_child_ids[k], res_fork.m_child_ids[l], levels.get(consideredTime)) );
					}
				}

				//has the GT-kids-for-cycle went whole through?
				if (match)
				{
					gt_correct[i] = true;
					return true;
				}
			}
		}

		return false;
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
	 * This function is asked to use, if applicable, such cache data
	 * as the caller believes the given cache is still valid. The measure
	 * can only carry on with the bottom stage then (thus being overall faster
	 * than when computing both stages).
	 *
	 * The class never re-uses its own cache to allow for fresh complete
	 * re-calculation on the (new) data in the same folders.
	 *
	 * This is the main BCi calculator.
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
		//DEBUG//log.info("Computing the BCi bottom part...");
		bcI = 0.0;

		//shadows of the/short-cuts to the cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final HashMap<Integer,Track> res_tracks = cache.res_tracks;
		final Vector<TemporalLevel> levels = cache.levels;

		final Vector<Fork> gt_forks  = cache.gt_forks;
		final Vector<Fork> res_forks = cache.res_forks;

		//some reports... ;)
		final int noGT  = gt_forks.size();
		final int noRES = res_forks.size();
		log.info("---");
		log.info("Number of divisions in reference (ground truth) tracks: "+noGT);
		log.info("Number of divisions in computed (result) tracks       : "+noRES);

		//store F-scores explicitly to be able to report them separately afterwards
		final double[] bcis = new double[6];

		//report for interval for temporal window sizes (the 'i' parameter of BCi)
		for (int maxI = 0; maxI <= 5; ++maxI)
		{
			final boolean[] gt_correct = new boolean[noGT];
			int numCorrect = 0;

			//scan all result branching events for a match
			for (Fork res_fork : res_forks)
				if (CorrectFork(maxI, res_fork, gt_forks, gt_correct, gt_tracks, res_tracks, levels))
					++numCorrect;

			log.info("Number of correctly detected divisions for i="+maxI+"        : "+numCorrect);

			//calculate F-score:
			if (noGT > 0)
			{
				bcis[maxI] = (2.0 * numCorrect) / (double)(noRES + noGT);

				//are we at the desired 'i' parameter value? save it then...
				if (maxI == desiredI) bcI = bcis[maxI];
			}
		}

		//report the F-scores now
		if (noGT > 0)
			for (int maxI = 0; maxI <= 5; ++maxI) log.info("BC("+maxI+"): "+bcis[maxI]);
		else
			log.info("BC(i): Couldn't calculate F-score because there are no GT tracks.");

		//BCi was not reported for some datasets (Unsatisfied Condition - UC) in the paper
		if (noGT < 50)
			log.info("Warning: Reference data contains few branching events.");

		return (bcI);
	}

	/// This is the wrapper BCi calculator, assuring complete re-calculation.
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		return this.calculate(gtPath,resPath,null);
	}
}

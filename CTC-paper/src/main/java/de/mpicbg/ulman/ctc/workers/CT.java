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

public class CT
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public CT(final LogService _log)
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


	// ----------- the CT essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double ct = 0.0;


	///calculate the number of completely correctly reconstructed tracks
	public int NumCorrectPaths(final Vector<TemporalLevel> levels,
		final Map<Integer,Track> gt_tracks,
		final Map<Integer,Track> res_tracks)
	{
		//return value
		int num_correct = 0;

		//serialization of GT tracks to obtain:
		//indicator if given GT track has been correctly reconstructed ...
		final boolean[] gt_correct = new boolean[gt_tracks.size()];
		//... with its corresponding track ID
		final int[] gt_ids = new int[gt_tracks.size()];

		int i = 0;
		for (Integer id : gt_tracks.keySet())
		{
			//NB: enumerates GT IDs in some undefined order
			//NB: (and we need to scan gt_tracks repeatedly, always in the same order)
			gt_correct[i] = false;
			gt_ids[i] = id;
			++i;
		}

		//helper variable
		boolean overlap;

		//now, over all RES tracks and look for appropriate, not yet reconstructed GT track
		for (Track res_track : res_tracks.values())
		{
			//scan over all GT tracks ...
			for (i = 0; i < gt_correct.length; ++i)
			{
				//... to find not reconstructed GT track that starts and ends at the same time point
				if (!gt_correct[i] && gt_tracks.get(gt_ids[i]).m_begin == res_track.m_begin
				   && gt_tracks.get(gt_ids[i]).m_end == res_track.m_end)
				{
					//check spatial overlap at all time points of the track
					overlap = true;
					for (int t=res_track.m_begin; t <= res_track.m_end && overlap; ++t)
						if (!cache.UniqueMatch(gt_ids[i], res_track.m_id, levels.get(t)))
							overlap = false;

					if (overlap == true)
					{
						//overlaps okay in the entire length of the GT track,
						//thus, mark it as reconstructed
						gt_correct[i] = true;
						++num_correct;
						break;
						//NB: breaks the "over all GT" cycle as this res_track
						//    cannot reconstruct another GT track
					}
				}
			}
		}

		return (num_correct);
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
	 * This is the main CT calculator.
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
		//DEBUG//log.info("Computing the CT bottom part...");
		ct = 0.0;

		//shadows of the/short-cuts to the cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final HashMap<Integer,Track> res_tracks = cache.res_tracks;
		final Vector<TemporalLevel> levels = cache.levels;

		//some reports... ;)
		final int noGT  = gt_tracks.size();
		final int noRES = res_tracks.size();
		final int numCorrect = NumCorrectPaths(levels, gt_tracks, res_tracks);
		log.info("---");
		log.info("Number of (reference, ground truth) GT tracks: "+noGT);
		log.info("Number of computed (result) tracks           : "+noRES);
		log.info("Number of completely reconstructed GT tracks : "+numCorrect);

		//calculate F-score:
		if (noGT > 0)
		{
			ct = (2.0 * numCorrect) / (double)(noRES + noGT);
			log.info("CT: "+ct);
		}
		else
			log.info("CT: Couldn't calculate F-score because there are no GT tracks.");

		return (ct);
	}

	/// This is the wrapper CT calculator, assuring complete re-calculation.
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		return this.calculate(gtPath,resPath,null);
	}
}

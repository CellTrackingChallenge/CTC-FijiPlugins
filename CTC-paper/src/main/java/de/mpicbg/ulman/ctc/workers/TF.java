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

public class TF
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public TF(final LogService _log)
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


	// ----------- the TF essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double tf = 0.0;


	///calculate correctly reconstructed fractions of entire tracks
	public void CalcFRs(final Vector<TemporalLevel> levels,
		final Map<Integer,Track> gt_tracks,
		final Map<Integer,Track> res_tracks,
		final Map<Integer,Float> gt_startingRatio,
		final Map<Integer,Float> gt_followedRatio)
	{
		//init the output data
		gt_startingRatio.clear();
		gt_followedRatio.clear();

		//serialization of GT tracks to obtain:
		//indicator if given GT track has been correctly reconstructed ...
		final boolean[] gt_correct = new boolean[gt_tracks.size()];
		//... with its corresponding track ID
		final int[] gt_ids = new int[gt_tracks.size()];

		int i = 0;
		//for (Integer id : gt_tracks.keySet())
		Vector<Integer> sortedIDs = new Vector<>(gt_tracks.keySet());
		sortedIDs.sort(null);
		for (Integer id : sortedIDs)
		{
			//NB: enumerates GT IDs in defined order (from the smallest to the largest)
			//NB: (and we need to scan gt_tracks repeatedly, always in the same order)
			gt_correct[i] = false;
			gt_ids[i] = id;
			gt_startingRatio.put(id,0.0f);
			gt_followedRatio.put(id,0.0f);
			++i;
		}

		//now, over all RES tracks and look for appropriate, not yet reconstructed GT track
		//for (Track res_track : res_tracks.values())
		sortedIDs = new Vector<>(res_tracks.keySet());
		sortedIDs.sort(null);
		for (Integer id : sortedIDs)
		{
			final Track res_track = res_tracks.get(id);

			//scan over all GT tracks ...
			for (i = 0; i < gt_correct.length; ++i)
			{
				//... to find not yet reconstructed GT track ...
				if (!gt_correct[i])
				{
					//... to see how far we can reconstruct it with this RES track
					//
					//so far the best progress
					float bestStartPos = gt_startingRatio.get(gt_ids[i]);
					float bestFraction = gt_followedRatio.get(gt_ids[i]);

					//current progress
					int res_progress = 0;

					//max progress possible
					final int gt_trackLength = gt_tracks.get(gt_ids[i]).m_end
					                         - gt_tracks.get(gt_ids[i]).m_begin +1;
					final int gtStart = gt_tracks.get(gt_ids[i]).m_begin;

					//scan given RES track to see how well it follows the selected GT
					int j = res_track.m_begin;
					while (j <= res_track.m_end)
					{
						if (cache.UniqueMatch(gt_ids[i], res_track.m_id, levels.get(j)))
						{
							//we have a match at time point j
							++res_progress;
						}
						else
						{
							//we do not have a match
							//
							//check this recent following attempt,
							//and possibly update with this attempt
							final float curFraction=(float)res_progress/(float)gt_trackLength;
							if (curFraction > bestFraction)
							{
								//j current time when following got broken, at this moment res_progress frames
								//were discovered... j-res_progress is thus time we started this discovery
								//minus further the gtStart gives distance from the GT track beginning,
								//which is normalized by its (GT) length...
								bestStartPos=(float)(j-gtStart -res_progress)/(float)gt_trackLength;
								bestFraction=curFraction;

								//REMOVE ME, DEBUG
								if (bestStartPos > 1.0f)
								{
									//hmm... something is wrong, debug me
									log.info("m bSP="+bestStartPos+": j="+j
									        +", rP="+res_progress+", b="+res_track.m_begin
									        +", e="+res_track.m_end
									        +", gtLen="+gt_trackLength);
								}
							}

							//reset the current progress
							res_progress=0;
						}

						++j;
					}

					//check and possibly update with this attempt
					final float curFraction=(float)res_progress/(float)gt_trackLength;
					if (curFraction > bestFraction)
					{
						bestStartPos=(float)(j-gtStart -res_progress)/(float)gt_trackLength;
						bestFraction=curFraction;

						//REMOVE ME, DEBUG
						if (bestStartPos > 1.0f)
						{
							log.info("e bSP="+bestStartPos+": j="+j
							        +", rP="+res_progress+", b="+res_track.m_begin
							        +", e="+res_track.m_end
							        +", gtLen="+gt_trackLength);
						}
					}

					if (bestFraction > 0.999f) //just to avoid float-point imprecisions
					{
						//save the (updated) so far the best progress
						gt_startingRatio.put(gt_ids[i],0.f);
						gt_followedRatio.put(gt_ids[i],1.f);
						gt_correct[i] = true;
						//should be commented out to continue searching for other GTs
						//that can this RES track satisfy/discover/reconstruct
						break;
					}
					else
					{
						//save the (updated) so far the best progress
						gt_startingRatio.put(gt_ids[i],bestStartPos);
						gt_followedRatio.put(gt_ids[i],bestFraction);
					}
				}
			}
		}
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
	 * This is the main TF calculator.
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
		//DEBUG//log.info("Computing the TF bottom part...");
		tf = 0.0;

		//shadows of the/short-cuts to the cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final HashMap<Integer,Track> res_tracks = cache.res_tracks;
		final Vector<TemporalLevel> levels = cache.levels;

		final HashMap<Integer,Float> gt_startingRatio = new HashMap<>();
		final HashMap<Integer,Float> gt_followedRatio = new HashMap<>();
		CalcFRs(levels, gt_tracks, res_tracks, gt_startingRatio,gt_followedRatio);

		//scan the discovered ratios and print out (and count how many tracks were detected)
		int partlyRecoveredCounter=0; //partly recovered tracks (PIT)
		int tooShortCounter=0; //PIT but unable to follow to the GT track to the very end
		int correctlyFollowedCounter=0; //PIT and followed exactly the GT track
		//int overlyLongCounter=0; //PIT, followed GT track and did not stop but continued erroneously to follow something

		for (Float gt_fR : gt_followedRatio.values()) //goes essentially over all tracks, see CalcFRs()
		{
			if (gt_fR > 0.f)
			{
				tf+=gt_fR;
				++partlyRecoveredCounter;
				if (gt_fR < 1.0f) ++tooShortCounter;
			}
			if (gt_fR == 1.0f) ++correctlyFollowedCounter;
			//if (gt_fR > 1.0f) ++overlyLongCounter;
		}
		tf/=(double)partlyRecoveredCounter;

		log.info("---");
		log.info("Number of partly followed GT tracks    : "+tooShortCounter);
		log.info("Number of completely followed GT tracks: "+correctlyFollowedCounter);
		log.info("Number of detected GT tracks           : "+partlyRecoveredCounter);
		//overlyLongCounter could be reported too...
		//log.info("Average followed fraction of detected tracks, TF measure: "+tf);

		log.info("TF: "+tf);
		return (tf);
	}

	/// This is the wrapper TF calculator, assuring complete re-calculation.
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		return this.calculate(gtPath,resPath,null);
	}
}

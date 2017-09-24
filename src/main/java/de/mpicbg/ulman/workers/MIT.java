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

import java.io.IOException;

import de.mpicbg.ulman.workers.TrackDataCache;
import de.mpicbg.ulman.workers.TrackDataCache.Track;

public class MIT
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public MIT(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}


	//---------------------------------------------------------------------/
	/// This is the main MIT calculator.
	public double calculate(final String annPath)
	throws IOException
	{
		//a class with helpful data structures and functions
		TrackDataCache cache = new TrackDataCache(log);

		//load our own working data
		cache.LoadTrackFile(annPath+"/TRA/man_track.txt", cache.gt_tracks);
		if (cache.gt_tracks.size() == 0)
			throw new IllegalArgumentException("No reference (GT) track was found!");

		//do the bottom stage
		//DEBUG//log.info("Computing the MIT completely...");
		double mit = 0.0;

		//to detected span of time points: this is believed to give the length of the
		//underlying video provided the video does not begin and/or end with empty frames...
		int minTime = Integer.MAX_VALUE;
		int maxTime = Integer.MIN_VALUE;

		//detect the span
		for (Track t : cache.gt_tracks.values())
		{
			minTime = Math.min(minTime, t.m_begin);
			maxTime = Math.max(maxTime, t.m_end);
		}
		//NB: we do not mind negative time points (can happen only with minTime)

		//check to prevent from having negative length of the video
		if (minTime > maxTime)
			throw new IllegalArgumentException("Reference (GT) tracks have wrong (negative) time span.");

		//calculate the average number of dividing cells per frame, which would be
		//to accumulate numbers of divisions happening in every frame and divide by
		//video length -- but the accumulation amounts to the number of all division
		//across the video
		cache.DetectForks(cache.gt_tracks, cache.gt_forks);
		mit = (double)cache.gt_forks.size() / (double)(maxTime - minTime +1);

		//final report
		//log.info("MIT_debug: span="+(maxTime-minTime+1)+", forks cnt="+cache.gt_forks.size());
		log.info("MIT: "+mit);
		return (mit);
	}
}

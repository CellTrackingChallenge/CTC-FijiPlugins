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

import de.mpicbg.ulman.workers.TrackDataCache;

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


	// ----------- the MIT essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double mit = 0.0;


	//---------------------------------------------------------------------/
	/**
	 * This is the main MIT calculator.
	 */
	public double calculate(final String annPath)
	throws IOException
	{
		//shadows of the/short-cuts to the cache data
		TrackDataCache cache = new TrackDataCache(log);
		final HashMap<Integer,Track> ann_tracks = cache.gt_tracks;

		//load our own working data
		cache.LoadTrackFile(annPath+"/TRA/man_track.txt", ann_tracks);
		if (ann_tracks.size() == 0)
			throw new IllegalArgumentException("No reference (GT) track was found!");

		//do the bottom stage
		//DEBUG//log.info("Computing the MIT completely...");
		mit = 0.0;

		//detected span of time points
		int minTime = Integer.MAX_VALUE;
		int maxTime = Integer.MIN_VALUE;

		//detect the span
		for (Track t : ann_tracks.values())
		{
			minTime = Math.min(minTime, t.m_begin);
			maxTime = Math.max(maxTime, t.m_end);
		}
		//NB: we do not mind negative time points (can happen only with minTime)

		//check before the allocation below
		if (minTime > maxTime)
			throw new IllegalArgumentException("Reference (GT) tracks have wrong (negative) time span.");

		//stores, per frame, number of present cells and number of cells
		//that die here because of division in some next frame
		int[] populationSize     = new int[maxTime-minTime+1];
		int[] populationDividers = new int[maxTime-minTime+1];

		//fill populations per frame
		for (Track t : ann_tracks.values())
			for (int f = t.m_begin; f <= t.m_end; ++f)
				++populationSize[f -minTime];

		//shadows of the/short-cuts to the cache data
		final Vector<Fork> ann_forks = cache.gt_forks;
		cache.DetectForks(ann_tracks, ann_forks);

		//fill the dividers array
		for (Fork f : ann_forks)
			++populationDividers[ann_tracks.get(f.m_parent_id).m_end -minTime];

		//calculate the average number of dividing cells per frame
		//........... work in progress .............










		//go over all FG objects and calc their MITs
		long noFGs = 0;
		//over all time points
		for (int time=0; time < avgFG.size(); ++time)
		{
			//over all objects, in fact use their avg intensities
			for (Double fg : avgFG.get(time).values())
			{
				mit += (fg - avgBG.get(time)) / stdBG.get(time);
				++noFGs;
			}
		}

		//finish the calculation of the average
		if (noFGs > 0)
		{
			mit /= (double)noFGs;
			log.info("MIT: "+mit);
		}
		else
			log.info("MIT: Couldn't calculate average MIT because there are no cells labelled.");

		return (mit);
	}
}

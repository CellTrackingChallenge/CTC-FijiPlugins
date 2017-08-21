/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.util.Collection;
import java.util.Vector;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import de.mpicbg.ulman.workers.TrackDataCache.Track;
import de.mpicbg.ulman.workers.TrackDataCache.TemporalLevel;

public class SEG
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public SEG(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}


	// ----------- the SEG essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double seg = 0.0;


	//---------------------------------------------------------------------/
	/**
	 * This is the main SEG calculator.
	 */
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		//instantiate the cache because it has functions we will use
		final TrackDataCache cache = new TrackDataCache(log);

		//do the bottom stage
		log.info("Computing the SEG bottom part...");
		seg = 0.0;

		//shadows of the/short-cuts to the empty cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final HashMap<Integer,Track> res_tracks = cache.res_tracks;
		final Vector<TemporalLevel> levels = cache.levels;

		//...

		log.info("---");
		log.info("SEG: "+seg);
		return (seg);
	}
}

/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2018 Martin Maška, Vladimír Ulman
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.util.Vector;
import java.util.HashMap;

import de.mpicbg.ulman.workers.TrackDataCache.Track;
import de.mpicbg.ulman.workers.TrackDataCache.TemporalLevel;

public class DET extends TRA
{
	///a constructor requiring connection to Fiji report/log services
	public DET(final LogService _log)
	{
		super(_log);
	}

	@Override
	public double calculate(final String gtPath, final String resPath,
	                        final TrackDataCache _cache)
	throws IOException, ImgIOException
	{
		//due to the above overrides, we can use TRA.calculate() as it is,
		//we only zero all the links-associated weights to be on the safe side...
		this.penalty.m_ed = 0.0;
		this.penalty.m_ea = 0.0;
		this.penalty.m_ec = 0.0;

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
		//DEBUG//log.info("Computing the TRA bottom part...");
		aogm = 0.0;

		logNS.add(String.format("----------Splitting Operations (Penalty=%g)----------", penalty.m_ns));
		logFN.add(String.format("----------False Negative Vertices (Penalty=%g)----------", penalty.m_fn));
		logFP.add(String.format("----------False Positive Vertices (Penalty=%g)----------", penalty.m_fp));

		//shadows of the/short-cuts to the cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final Vector<TemporalLevel> levels = cache.levels;

		//this is: local ClassifyLabels() -- the part that already does some AOGM checks
		//this is: the AOGM-specific last portion of the original FindMatch() C++ function:
		//
		//this is: basically checks matching between all nodes discovered in both GT and RES images
		for (TemporalLevel level : levels)
		{
			//sweep over all gt labels
			for (int i=0; i < level.m_gt_lab.length; ++i)
			{
				//check if we have found corresponding res label
				if (level.m_gt_match[i] == -1)
				{
					//no correspondence -> the gt label represents FN (false negative) case
					aogm += penalty.m_fn;
					logFN.add(String.format("T=%d GT_label=%d",level.m_level,level.m_gt_lab[i]));
				}
			}

			//for every res label, check we have found exactly one corresponding gt label
			int num;
			for (int j=0; j < level.m_res_lab.length; ++j)
			{
				//number of overlapping gt labels
				num = level.m_res_match[j].size();

				if (num == 0)
				{
					//no label -- too few
					aogm += penalty.m_fp;
					logFP.add(String.format("T=%d Label=%d",level.m_level,level.m_res_lab[j]));
				}
				else if (num > 1)
				{
					//to many labels...
					aogm += (num - 1) * penalty.m_ns;
					for (int qq=1; qq < num; ++qq)
						logNS.add(String.format("T=%d Label=%d",level.m_level,level.m_res_lab[j]));
					max_split = num > max_split ? num : max_split;
				}
			}
		}

		// check the minimality condition
		if ((max_split - 1) * penalty.m_ns > (penalty.m_fp + max_split * penalty.m_fn))
			log.info("Warning: The minimality condition broken! (m*="+max_split+")");
		//AOGM calculation ends here

		//should the log reports be printed?
		if (doLogReports)
		{
			reportLog(logNS);
			reportLog(logFN);
			reportLog(logFP);
		}

		//now, the (old) TRA between GT and RES is calculated:
		//the old refers to the un-normalized TRA value, interval [0,infinity)
		// (approx. an energy required to CORRECT tracking result)

		if (doAOGM == false)
		{
			//calculate the (old) TRA when no result is supplied
			// (approx. an energy required to CREATE tracking result from the scratch)
			//
			//how many track links (edges) to add
			int sum = 0;

			for (Integer id : gt_tracks.keySet())
			{
				Track t = gt_tracks.get(id);
				sum += t.m_end - t.m_begin;
			}

			final double aogm_empty = penalty.m_fn * (sum + gt_tracks.size()); //adding nodes

			log.info("---");
			log.info("AOGM-D to curate  the  given  result: "+aogm);
			log.info("AOGM-D to build a new correct result: "+aogm_empty);

			//if correcting is more expensive than creating, we assume user deletes
			//the whole result and starts from the scratch, hence aogm = aogm_empty
			aogm = aogm > aogm_empty ? aogm_empty : aogm;

			//normalization:
			aogm = 1.0 - aogm/aogm_empty;

			log.info("normalized AOGM-D = DET: "+aogm);
		}
		else
		{
			//just report the AOGM as it is...
			log.info("---");
			log.info("AOGM: "+aogm);
		}
		return (aogm);
	}
}

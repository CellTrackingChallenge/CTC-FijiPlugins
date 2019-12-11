/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2018 Martin Maška, Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.workers;

import org.scijava.log.LogService;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.img.ImgIOException;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;

import java.util.Set;
import java.util.Iterator;

import de.mpicbg.ulman.ctc.workers.TrackDataCache.TemporalLevel;

public class DET extends TRA
{
	///a constructor requiring connection to Fiji report/log services
	public DET(final LogService _log)
	{
		super(_log);
	}

	/** Set of time points that caller wishes to process despite the folder
	    with GT contains much more. If this attribute is left uninitialized,
	    the whole GT folder is considered -- this is the SEG's default behaviour.
	    However, occasionally a caller might want to calculate SEG only for a few
	    time points, and this when this attribute becomes useful. */
	public Set<Integer> doOnlyTheseTimepoints = null;

	//---------------------------------------------------------------------/
	///the main DET calculator/calculation pipeline
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

		//if no cache is available after all, compute it,
		//but remember that it cannot be re-used -- see below
		if (cache == null)
		{
			//do the upper stage
			cache = new TrackDataCache(log);
			cache.noOfDigits = noOfDigits;

			log.info(" GT path: "+gtPath+"/TRA");
			log.info("RES path: "+resPath);
			//DEBUG//log.info("Computing the common upper part...");

			//unified approach to either trying filename one by one,
			//or creating filenames from given list of time points
			class FileTryer {
				private final Iterator<Integer> sampler;
				private int time = -1;

				FileTryer(final Set<Integer> set)
				{
					sampler = set != null ? set.iterator() : null;
				}

				int next()
				{
					if (sampler == null)
						++time;
					else
						time = sampler.next();

					return time;
				}

				boolean hasNext()
				{
					if (sampler == null)
						return Files.isReadable(
							new File(String.format("%s/TRA/man_track%0"+noOfDigits+"d.tif",gtPath,time+1)).toPath() );
					else
						return sampler.hasNext();
				}
			}
			final FileTryer fileSampler = new FileTryer(doOnlyTheseTimepoints);

			//iterate through the GT folder and read files, one by one,
			//find the appropriate file in the RES folder,
			//and call ClassifyLabels() for every such pair
			while (fileSampler.hasNext())
			{
				final int time = fileSampler.next();

				//read the image pair
				Img<UnsignedShortType> gt_img
					= cache.ReadImageG16(String.format("%s/TRA/man_track%0"+noOfDigits+"d.tif",gtPath,time));

				Img<UnsignedShortType> res_img
					= cache.ReadImageG16(String.format("%s/mask%0"+noOfDigits+"d.tif",resPath,time));

				cache.ClassifyLabels(gt_img, res_img, time);

				//to be on safe side (with memory)
				gt_img = null;
				res_img = null;
			}

			if (cache.levels.size() == 0)
				throw new IllegalArgumentException("No reference (GT) image was found!");

			//don't update this.gtPath and this.resPath (make it incompatible this way)
			//as the content of this cache is not exactly
			//what it is supposed to be (it is incomplete)
		}

		//do the bottom stage
		//DEBUG//log.info("Computing the TRA bottom part...");
		aogm = 0.0;
		long gtLabelsFound = 0; //for calculating aogm_empty

		if (doLogReports)
		{
			logNS.add(String.format("----------Splitting Operations (Penalty=%g)----------", penalty.m_ns));
			logFN.add(String.format("----------False Negative Vertices (Penalty=%g)----------", penalty.m_fn));
			logFP.add(String.format("----------False Positive Vertices (Penalty=%g)----------", penalty.m_fp));
		}
		if (doMatchingReports)
			logMatch.add(String.format("----------Vertices Matching Status (No Penalty)----------", penalty.m_ns));

		//this is: local ClassifyLabels() -- the part that already does some AOGM checks
		//this is: the AOGM-specific last portion of the original FindMatch() C++ function:
		//
		//this is: basically checks matching between all nodes discovered in both GT and RES images
		for (TemporalLevel level : cache.levels)
		{
			//skip this time point if the list of wished time points exists
			//and the current one is not present in it
			if (doOnlyTheseTimepoints != null && !doOnlyTheseTimepoints.contains(level.m_level)) continue;

			//sweep over all gt labels
			for (int i=0; i < level.m_gt_lab.length; ++i)
			{
				//check if we have found corresponding res label
				if (level.m_gt_match[i] == -1)
				{
					//no correspondence -> the gt label represents FN (false negative) case
					aogm += penalty.m_fn;
					if (doLogReports)
						logFN.add(String.format("T=%d GT_label=%d",level.m_level,level.m_gt_lab[i]));
					if (doMatchingReports)
						logMatch.add(String.format("T=%d GT_label=%d matches none",level.m_level,level.m_gt_lab[i]));
				}
				else
				{
					if (doMatchingReports)
						logMatch.add(String.format("T=%d GT_label=%d matches %d",level.m_level,level.m_gt_lab[i], level.m_res_lab[level.m_gt_match[i]] ));
				}
			}
			gtLabelsFound += level.m_gt_lab.length;

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
					if (doLogReports)
						logFP.add(String.format("T=%d Label=%d",level.m_level,level.m_res_lab[j]));
					if (doMatchingReports)
						logMatch.add(String.format("T=%d Label=%d matches nothing",level.m_level,level.m_res_lab[j]));
				}
				else if (num > 1)
				{
					//too many labels...
					aogm += (num - 1) * penalty.m_ns;
					if (doLogReports)
					{
						for (int qq=1; qq < num; ++qq)
							logNS.add(String.format("T=%d Label=%d",level.m_level,level.m_res_lab[j]));
					}
					max_split = num > max_split ? num : max_split;
					if (doMatchingReports)
						logMatch.add(String.format("T=%d Label=%d matches multiple",level.m_level,level.m_res_lab[j]));
				}
				else //num == 1
				{
					if (doMatchingReports)
						logMatch.add(String.format("T=%d Label=%d matches exactly %d",level.m_level,level.m_res_lab[j], level.m_gt_lab[(int)level.m_res_match[j].toArray()[0]] ));
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
		if (doMatchingReports) reportLog(logMatch);

		//now, the (old) TRA between GT and RES is calculated:
		//the old refers to the un-normalized TRA value, interval [0,infinity)
		// (approx. an energy required to CORRECT tracking result)

		if (doAOGM == false)
		{
			//calculate the DET when no result is supplied
			// (approx. an energy required to CREATE detection result from the scratch)
			//
			final double aogm_empty = penalty.m_fn * (double)gtLabelsFound;

			if (gtLabelsFound == 0)
				throw new IllegalArgumentException("No reference (GT) label was found at all!");

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
			log.info("AOGM-D: "+aogm);
		}
		return (aogm);
	}
}

/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Martin Maška, Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.workers;

import org.scijava.log.LogService;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.Iterator;
import java.util.Set;
import java.util.List;

import de.mpicbg.ulman.ctc.workers.TrackDataCache.TemporalLevel;

public class SEG
{
	///shortcuts to some Fiji services
	private final LogService log;

	///specifies how many digits are to be expected in the input filenames
	public int noOfDigits = 3;

	///a constructor requiring connection to Fiji report/log services
	public SEG(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	/**
	 * Calculation option: do report list of discrepancies between the reference
	 * and computed tracking result.
	 * This is helpful for algorithm developers as it shows what, where and when
	 * was incorrect in their results.
	 */
	public boolean doLogReports = false;

	/** This switches SEG to review all result labels, in contrast to reviewing
	    all GT labels. With this enabled, one can see false positives but don't
	    see false negatives. */
	public boolean doAllResReports = false;

	/** Set of time points that caller wishes to process despite the folder
	    with GT contains much more. If this attribute is left uninitialized,
	    the whole GT folder is considered -- this is the SEG's default behaviour.
	    However, occasionally a caller might want to calculate SEG only for a few
	    time points, and this when this attribute becomes useful. */
	public Set<Integer> doOnlyTheseTimepoints = null;

	/** This switches SEG to complain (and stop calculating)
	    if empty ground-truth or result image was found. */
	public boolean doStopOnEmptyImages = false;

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
		log.info(" GT path: "+gtPath+"/SEG");
		log.info("RES path: "+resPath);

		//instantiate the cache because it has functions we will use
		final TrackDataCache cache = new TrackDataCache(log);
		cache.noOfDigits = noOfDigits;

		//do the bottom stage
		//DEBUG//log.info("Computing the SEG completely...");
		seg = 0.0;
		long counter = 0;
		int imgCounter = 0;

		//scan the SEG folder to get a list of files to process,
		//NB: the processing order of the files is not important
		final PathMatcher fileMatcher
			= FileSystems.getDefault().getPathMatcher("glob:man_seg*");
		@SuppressWarnings("resource")
		final Stream<Path> fileList
			= Files.list(Paths.get(gtPath+"/SEG"));

		//list file by file and process...
		Iterator<Path> files = fileList.iterator();
		while (files.hasNext())
		{
			final Path file = files.next();

			//check the file is of proper file name and not a folder
			if (!fileMatcher.matches(file.getFileName())) continue;
			if (!Files.isRegularFile(file)) continue;

			//we have likely the right file,
			//extract timepoint and possibly also the slice number
			int time  = -1;
			int slice = -1;

			//with or with out a slice information?
			final String filename = file.getFileName().toString();
			if (filename.charAt(7) == '_')
			{
				//with slice info
				time = Integer.parseInt(filename.substring(8, 11));
				if (filename.charAt(11) == '_')
					slice = Integer.parseInt(filename.substring(12, 15));
			}
			else
			{
				//no slice info
				time = Integer.parseInt(filename.substring(7, 10));
			}

			//time point number must have been parsed, or in trouble...
			if (time < 0)
				throw new IllegalArgumentException("Error extracting time point information"
					+" from file "+filename+"!");

			//skip this time point if the list of wished time points exists
			//and the current one is not present in it
			if (doOnlyTheseTimepoints != null && !doOnlyTheseTimepoints.contains(time)) continue;

			//read the image pair
			IterableInterval<UnsignedShortType> gt_img
				= cache.ReadImageG16(file.toString());

			RandomAccessibleInterval<UnsignedShortType> res_img
				= cache.ReadImageG16(String.format("%s/mask%0"+noOfDigits+"d.tif",resPath,time));

			//check that slice "extracting" can make sense (the 3rd dim must be present)
			if (slice > -1 && res_img.numDimensions() <= 2)
				throw new IllegalArgumentException("GT image at time "+time
					+" specifies slice but the image is not 3D.");

			/*
			for (int n=0; n < 2; ++n)
				if (gt_img.dimension(n) != res_img.dimension(n))
					throw new IllegalArgumentException("Image pair at time"+time
						+" does not consist of images of the same x,y size.");
			*/

			//should extract slice? use imglib2 views instead
			if (slice > -1)
			{
				res_img = Views.hyperSlice(res_img, 2, slice);
				log.info("Considering only slice "+slice);
			}

			//now, both images must of the same size...
			for (int n=0; n < gt_img.numDimensions(); ++n)
				if (gt_img.dimension(n) != res_img.dimension(n))
					throw new IllegalArgumentException("Image pair at time"+time
						+" does not consist of images of the same size.");

			cache.ClassifyLabels(gt_img, res_img, doStopOnEmptyImages);
			++imgCounter;

			//after ClassifyLabels(), the voxel-matching info is here:
			final TemporalLevel level = cache.levels.lastElement();

			//calculate Jaccard for matching markers at this 'level'/time point
			if (doLogReports)
				log.info("----------T="+time+" Z="+(slice==-1?0:slice)+"----------");

			//over all GT labels
			final int m_match_lineSize = level.m_gt_lab.length;
			for (int i=0; i < level.m_gt_lab.length; ++i)
			{
				//Jaccard for this GT label at this time point
				double acc = 0.0;

				if (level.m_gt_match[i] > -1)
				{
					//actually, we have a match,
					//update the Jaccard accordingly
					final int intersectSize
						= level.m_match[i + m_match_lineSize*level.m_gt_match[i]];

					acc  = (double)intersectSize;
					acc /= (double)level.m_gt_size[i]
					          + (double)level.m_res_size[level.m_gt_match[i]] - acc;
				}

				//update overall stats
				seg += acc;
				++counter;

				if (doLogReports)
				{
					if (doAllResReports)
						//extended SEG report
						log.info(String.format("GT_label=%d J=%.6g considered_RES_label=", level.m_gt_lab[i], acc)
						  +(level.m_gt_match[i] > -1 ? level.m_res_lab[level.m_gt_match[i]] : "-"));
					else
						//standard SEG report
						log.info(String.format("GT_label=%d J=%.6g", level.m_gt_lab[i], acc));
				}
			}

			//extended SEG report
			if (doLogReports && doAllResReports)
			{
				//report matches from the "RES side"
				for (int j=0; j < level.m_res_lab.length; ++j)
				{
					final int matchCnt
						= level.m_res_match[j] != null ? level.m_res_match[j].size() : -1;

					String matchedGTlabs = "";
					if (matchCnt < 1)
						matchedGTlabs = " -";
					else
						for (Integer i : level.m_res_match[j]) matchedGTlabs = matchedGTlabs.concat(" "+level.m_gt_lab[i]);

					log.info("RES_label="+level.m_res_lab[j]+" matches GT labels:"+matchedGTlabs);
				}
			}

			//to be on safe side (with memory)
			gt_img = null;
			res_img = null;
		}
		fileList.close();

		//complain if necessary, to behave identially as the other measures
		if (imgCounter == 0)
			throw new IllegalArgumentException("No reference (GT) image was found!");
		if (counter == 0)
			throw new IllegalArgumentException("No reference (GT) label was found at all!");

		seg = counter > 0 ? seg/(double)counter : 0.0;

		log.info("---");
		log.info("SEG: "+seg);
		return (seg);
	}


	/**
	 * Calculates pairing of/matching between the segments from the two images,
	 * and returns lists of TP and FP labels from the res_img and FN[0] count
	 * of FN labels from the gt_img. Two segments, one from the gt_img and one
	 * from the res_img, are considered matching/overlapping only if the ratio
	 * of their intersection over the area/volume of the segment from the gt_img
	 * is strictly greater than the overlapRatio parameter.
	 *
	 * The sibling method calculateDetections() with the 'cache' parameter should
	 * be used when runtime performance is considered.
	 */
	public double calculateDetections(final IterableInterval<UnsignedShortType> gt_img,
	                                  final RandomAccessibleInterval<UnsignedShortType> res_img,
	                                  final double overlapRatio,
	                                  final List<Integer> TP, //LIST of good RES hits
	                                  final List<Integer> FP, //LIST of bad RES hits
	                                  final int[] FN)         //COUNT of missed GT ones
	{
		return calculateDetections(gt_img,res_img,null,overlapRatio,TP,FP,FN);
	}

	/**
	 * Calculates pairing of/matching between the segments from the two images,
	 * and returns lists of TP and FP labels from the res_img and FN[0] count
	 * of FN labels from the gt_img. Two segments, one from the gt_img and one
	 * from the res_img, are considered matching/overlapping only if the ratio
	 * of their intersection over the area/volume of the segment from the gt_img
	 * is strictly greater than the overlapRatio parameter.
	 *
	 * The pairing is actually established using the method class.ClassifyLabels()
	 * for which a class object should be given to be re-used. Note that the cache
	 * is always clear before any calculation takes place. If class = null then
	 * the method instantiates one internally.
	 */
	public double calculateDetections(final IterableInterval<UnsignedShortType> gt_img,
	                                  final RandomAccessibleInterval<UnsignedShortType> res_img,
	                                  TrackDataCache cache,
	                                  final double overlapRatio,
	                                  final List<Integer> TP, //LIST of good RES hits
	                                  final List<Integer> FP, //LIST of bad RES hits
	                                  final int[] FN)         //COUNT of missed GT ones
	{
		//if no cache is given, create one
		if (cache == null)
			cache = new TrackDataCache(log);

		//always use the same slot (that represents the first time point) in the cache
		final int fakeTimePoint = 0;
		cache.levels.clear();
		cache.noOfDigits = noOfDigits;

		//does the overlap-based pairing of GT and RES segments
		cache.ClassifyLabels(gt_img, res_img, doStopOnEmptyImages, fakeTimePoint, overlapRatio);

		//retrieve the "pointer" on the pairing information
		final TemporalLevel level = cache.levels.lastElement();

		double seg = 0.0;
		long counter = 0;
		int fnCnt = 0;

		//over all GT labels
		final int m_match_lineSize = level.m_gt_lab.length;
		for (int i=0; i < level.m_gt_lab.length; ++i)
		{
			//Jaccard for this GT label at this time point
			double acc = 0.0;

			if (level.m_gt_match[i] > -1)
			{
				//actually, we have a match,
				//update the Jaccard accordingly
				final int intersectSize
					= level.m_match[i + m_match_lineSize*level.m_gt_match[i]];

				acc  = (double)intersectSize;
				acc /= (double)level.m_gt_size[i]
				          + (double)level.m_res_size[level.m_gt_match[i]] - acc;
			}
			else fnCnt++;

			//update overall stats
			seg += acc;
			++counter;
		}
		if (FN.length > 0) FN[0] = fnCnt;

		//over all RES labels
		for (int j=0; j < level.m_res_lab.length; ++j)
		{
			if (level.m_res_match[j] != null && level.m_res_match[j].size() > 0)
				TP.add( level.m_res_lab[j] );
			else
				FP.add( level.m_res_lab[j] );
		}

		seg = counter > 0 ? seg/(double)counter : 0.0;
		return seg;
	}
}

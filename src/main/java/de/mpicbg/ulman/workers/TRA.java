/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Cursor;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.SCIFIOImgPlus;
import io.scif.img.ImgOpener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.util.Vector;
import java.util.HashSet;
import java.util.HashMap;

import java.io.*;
import java.util.Scanner;

public class TRA
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public TRA(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	// ----------- the TRA essentially starts here -----------
	//helper classes:

	/** Track representation. */
	private class Track
	{
		/** Explicit constructor. */
		Track(final int id, final int begin, final int end, final int parent)
		{
			m_id = id;
			m_begin = begin;
			m_end = end;
			m_parent = parent;
		}

		/** Track identifier. */
		final int m_id;
		/** The number of frame in which the track begins. */
		final int m_begin;
		/** The number of frame in which the track ends. */
		final int m_end;
		/** Identifier of the parent track. */
		final int m_parent;
	}

	/** Temporal level representation. */
	private class TemporalLevel
	{
		/** Constructor. */
		TemporalLevel(final int level)
		{
			m_level = level;
		}

		/** Temporal level -- a particular time point. */
		final int m_level;

		/** List of labels (and their sizes) in the reference image. */
		//HashMap<Integer,Integer> m_gt_lab;
		int[] m_gt_lab = null;
		/** List of sizes of labels in the reference image. */
		int[] m_gt_size = null;

		/** List of labels (and their sizes) in the computed image. */
		//HashMap<Integer,Integer> m_res_lab;
		int[] m_res_lab = null;
		/** List of sizes of labels in the computed image. */
		int[] m_res_size = null;

		/**
		 * Matching matrix, stored as a plain 1D array.
		 *
		 * For every position (j,i) (j-th row and i-th column), it contains
		 * number of voxels in the intersection between m_res_lab[j] label
		 * and m_gt_lab[i] label.
		 */
		int[] m_match = null;

		/**
		 * Indices of reference vertex matching, i.e., it is of the same length
		 * as m_gt_lab and it holds indices into the m_res_lab.
		 *
		 * It is initialized with -1 values. After matching is done,
		 * the value -1 corresponds to a FN vertex.
		 */
		int[] m_gt_match = null;

		/** Sets of indices of computed vertex matching, i.e., it is of the same length
		 * as m_res_lab and it holds sets of indices into the m_gt_lab.
		 *
		 * It is initialized with empty sets. After matching is done,
		 * an empty set corresponds to a FP vertex.
		 */
		HashSet<Integer>[] m_res_match = null;
	}

	/** Penalty configuration representation. */
	public class PenaltyConfig
	{
		/** Constructor. */
		PenaltyConfig(final double ns, final double fn, final double fp,
		              final double ed, final double ea, final double ec)
		{
			m_ns = ns; m_fn = fn; m_fp = fp;
			m_ed = ed; m_ea = ea; m_ec = ec;

			if (ns < 0.0 || fn < 0.0 || fp < 0.0 || ed < 0.0 || ea < 0.0 || ec < 0.0)
				throw new IllegalArgumentException("All weights must be nonnegative numbers!");
		}

		/** The penalty for a splitting operation. */
		double m_ns;
		/** The penalty for a false negative node. */
		double m_fn;
		/** The penalty for a false positive node. */
		double m_fp;
		/** The penalty for a redundant edge. */
		double m_ed;
		/** The penalty for a missing edge. */
		double m_ea;
		/** The penalty for an edge with wrong semantics. */
		double m_ec;
	}

	//---------------------------------------------------------------------/
	//data loading functions:

	private SCIFIOConfig openingRegime = null;
	private ImgOpener imgOpener = null;

	/// Loads the given filename AND checks it has appropriate voxel type.
	@SuppressWarnings("unchecked")
	private Img<UnsignedShortType> ReadImage(final String fname)
	throws ImgIOException
	{
		//init the "storing regime" of the input images and the loader object
		if (openingRegime == null)
		{
			openingRegime = new SCIFIOConfig();
			openingRegime.imgOpenerSetImgModes(ImgMode.ARRAY);
			imgOpener = new ImgOpener();
		}

		//the image to be loaded
		SCIFIOImgPlus<?> img = null;

		//open it
		try {
			img = imgOpener.openImgs(fname,openingRegime).get(0);
		}
		catch (ImgIOException e) {
			log.error("Error reading file: "+fname);
			throw new ImgIOException("Unable to read input file.");
		}

		//check input files for types and sizes
		if (!(img.firstElement() instanceof UnsignedShortType))
		{
			log.error("Error reading file: "+fname);
			throw new ImgIOException("Images are expected to have 16-bit gray voxels.");
		}

		log.info("loaded image: "+fname);
		return ((Img<UnsignedShortType>)img);
	}


	private void LoadTrackFile(final String fname, HashMap<Integer,Track> track_list)
	throws IOException
	{
		Scanner s = null;
		int id=-1,begin,end,parent;

		try {
			s = new Scanner(new BufferedReader(new FileReader(fname)));
			//TODO: read the file line by line, so that "missing element" errors are detected earlier

			while (s.hasNext())
			{
				//read track data
				id = Integer.parseInt(s.next());
				//NB: id should be hopefully available as hasNext() has passed
				//NB: the remaining 3 calls are not "guarded" like that
				begin = Integer.parseInt(s.next());
				end = Integer.parseInt(s.next());
				parent = Integer.parseInt(s.next());

				//check for duplicities
				if (track_list.containsKey(id))
					throw new IOException("Detected multiple occurrence of the same track.");

				//check the track has reasonable time stamps
				if (begin > end)
					throw new IOException("Detected track with wrong time stamps.");

				//store the track
				track_list.put(id,new Track(id,begin,end,parent));
			}
		} catch (IOException e) {
			log.error("Error reading track with ID="+id);
			throw e;
		} finally {
			if (s != null)
			{
				s.close();
			}
		}

		log.info("loaded track file: "+fname);
	}

	/**
	 * the un-normalized TRA value, interval [0,infinity)
	 * (approx. an energy required to correct tracking result)
	 */
	private double aogm = 0.0;

	///some helper value...
	private int max_split = 1;

	private void ClassifyLabels(Img<UnsignedShortType> gt_img, Img<UnsignedShortType> res_img,
		Vector<TemporalLevel> levels, PenaltyConfig penalty)
	{
		//create output TemporalLevel to which we gonna save our findings about both images
		TemporalLevel level = new TemporalLevel(levels.size());

		//helper frequency histogram of discovered labels
		HashMap<Integer,Integer> hist = new HashMap<Integer,Integer>();
		//helper variables
		int label = -1;        //marker value = label
		Integer count = null;  //marker presence counter

		//sweep the gt image
		Cursor<UnsignedShortType> c = gt_img.cursor();
		while (c.hasNext())
		{
			//update the histogram of found value
			label = c.next().getInteger();
			count = hist.get(label);
			hist.put(label, count == null ? 1 : count+1);
		}

		//copy the histogram to the level data class
		level.m_gt_lab = new int[hist.size()-1];
		level.m_gt_size = new int[hist.size()-1];
		level.m_gt_match = new int[hist.size()-1];

		int idx = 0; //label's index in the arrays
		for (Integer lbl : hist.keySet())
		//NB: should be true: hist.get(lbl) > 0
		if (lbl > 0)
		{
			level.m_gt_lab[idx] = lbl;
			level.m_gt_size[idx] = hist.get(lbl);
			level.m_gt_match[idx] = -1;
			++idx;
		}

		//now, the same for the res image
		//
		//sweep the res image
		hist.clear();
		c = res_img.cursor();
		while (c.hasNext())
		{
			//update the histogram of found value
			label = c.next().getInteger();
			count = hist.get(label);
			hist.put(label, count == null ? 1 : count+1);
		}

		//copy the histogram to the level data class
		level.m_res_lab = new int[hist.size()-1];
		level.m_res_size = new int[hist.size()-1];
		level.m_res_match = (HashSet<Integer>[])new HashSet<?>[hist.size()-1];

		idx = 0; //label's index in the arrays
		for (Integer lbl : hist.keySet())
		if (lbl > 0)
		{
			level.m_res_lab[idx] = lbl;
			level.m_res_size[idx] = hist.get(lbl);
			level.m_res_match[idx] = new HashSet<Integer>();
			++idx;
		}

		if (level.m_gt_lab.length == 0)
			throw new IllegalArgumentException("GT image has no markers!");
		if (level.m_res_lab.length == 0)
			throw new IllegalArgumentException("RES image has no markers!");

		//we don't need this one anymore
		hist.clear();
		hist = null;

		/*
		NB: the code so far represented the following passage in the C++ implementation:
		i3d::Histogram gt_hist, res_hist;
		i3d::IntensityHist(gt_img, gt_hist);
		i3d::IntensityHist(res_img, res_hist);

		TemporalLevel<T> level(levels.size());
		CreateLabels(gt_hist, level.m_gt_lab, levels.size());
		CreateLabels(res_hist, level.m_res_lab, levels.size());


		NB: the code that follows maps this original:
		CreateMatch(gt_img, res_img, level.m_gt_lab, level.m_res_lab, level.m_match);
		FindMatch(level, penalty, aogm, max_split, log);
		levels.push_back(level);
		*/




		//finally, "save" the level data
		levels.add(level);
	}

	//---------------------------------------------------------------------/
	///the main TRA calculator/calculation pipeline
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		log.info(" GT path: "+gtPath);
		log.info("RES path: "+resPath);

		PenaltyConfig penalty = new PenaltyConfig(5.0, 10.0, 1.0, 1.0, 1.5, 1.0);
		aogm = 0.0;

		//representation of tracks
		HashMap<Integer,Track> gt_tracks  = new HashMap<Integer,Track>();
		HashMap<Integer,Track> res_tracks = new HashMap<Integer,Track>();

		//fill the tracks data
		LoadTrackFile( gtPath+"/TRA/man_track.txt", gt_tracks);
		LoadTrackFile(resPath+"/res_track.txt", res_tracks);

		//representation of "label coverage" at temporal points
		Vector<TemporalLevel> levels = new Vector<TemporalLevel>(1000,100);

		//iterate through the GT folder and read files, one by one
		//find the appropriate file in the RES folder
		//and call ClassifyLabels() for every such pair
		int time = 0;
		while (Files.isReadable(
			new File(String.format("%s/TRA/man_track%03d.tif",gtPath,time)).toPath()))
		{
			Img<UnsignedShortType> gt_img
				= ReadImage(String.format("%s/TRA/man_track%03d.tif",gtPath,time));

			Img<UnsignedShortType> res_img
				= ReadImage(String.format("%s/mask%03d.tif",resPath,time));

			//check the sizes of the images
			if (gt_img.numDimensions() != res_img.numDimensions())
				throw new IllegalArgumentException("Image pair at time "+time
					+" does not consist of images of the same dimensionality.");

			for (int n=0; n < gt_img.numDimensions(); ++n)
				if (gt_img.dimension(n) != res_img.dimension(n))
					throw new IllegalArgumentException("Image pair at time"+time
						+" does not consist of images of the same size.");

			ClassifyLabels(gt_img, res_img, levels, penalty);
			++time;
		}

		return (aogm);
	}
}

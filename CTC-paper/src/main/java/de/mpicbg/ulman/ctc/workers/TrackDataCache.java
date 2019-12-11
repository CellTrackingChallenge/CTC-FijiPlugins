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

import net.imglib2.img.Img;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import sc.fiji.simplifiedio.SimplifiedIO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Scanner;

import java.util.Vector;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class TrackDataCache
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public TrackDataCache(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	///specifies how many digits are to be expected in the input filenames
	public int noOfDigits = 3;

	///GT and RES paths combination for which this cache is valid, null means invalid
	private String gtPath = null;
	///GT and RES paths combination for which this cache is valid, null means invalid
	private String resPath = null;

	///reference-based-only check if the parameters are those on which this cache was computed
	public boolean validFor(final String _gtPath, final String _resPath)
	{
		return ( gtPath != null &&  resPath != null
		     && _gtPath != null && _resPath != null
		     && gtPath == _gtPath
		     && resPath == _resPath);
	}


	// ----------- the common upper stage essentially starts here -----------
	//auxiliary data:

	/**
	 * Record of just one track. It stores exactly all attributes that
	 * are used in the text file that accompanies the image data. This
	 * file typically contains suffix track.txt, e.g. man_track.txt is
	 * used for ground truth data.
	 */
	public static class Track
	{
		/** Track identifier (ID), this value one should find in the image data.
		    The value must be strictly positive. */
		final int m_id;

		/** The number of time point (frame) in which the track begins.
		    The track is supposed to exist since this time point (inclusive). */
		final int m_begin;

		/** The number of time point (frame) in which the track ends.
		    The track is supposed to exist up to this time point (inclusive). */
		int m_end;

		/** Identifier (ID) of the parent track, leave 0 if no parent exists. */
		final int m_parent;

		/** Explicit constructor. */
		Track(final int id, final int begin, final int end, final int parent)
		{
			m_id = id;
			m_begin = begin;
			m_end = end;
			m_parent = parent;
		}

		/** Starts up a new, one-time-point-long track record. */
		public
		Track(final int ID, final int curTime, final int parentID)
		{
			this.m_id = ID;
			this.m_begin = curTime;
			this.m_end   = curTime;
			this.m_parent = parentID;
		}

		/** Updates the life span of a track record up to the given time point.
		    The method checks that track time span should be a continuous
		    interval and throws RuntimeException if 'curTime' would introduce
		    a hole in the interval. */
		public
		void prolongTrack(final int curTime)
		{
			if (curTime != m_end && curTime != m_end+1)
				throw new RuntimeException("Attempted to prolong the track "+m_id
				                          +" from time "+m_end+" to time "+curTime+".");
			this.m_end = curTime;
		}

		/** Exports tab-delimited four-column string: ID m_begin m_end parentID */
		public
		String exportToString()
		{
			return ( m_id+" "+m_begin+" "+m_end+" "+m_parent );
		}

		/** Exports tab-delimited four-column string: ID m_begin m_end parentID,
		    but report begin and end time adjusted (incremented) with the 'timeShift' param. */
		public
		String exportToString(final int timeShift)
		{
			return ( m_id+" "+(m_begin+timeShift)+" "+(m_end+timeShift)+" "+m_parent );
		}
	}

	/** Fork representation. */
	public class Fork
	{
		/** Explicit constructor. */
		Fork(final int parent_id, final Vector<Integer> child_ids)
		{
			m_parent_id = parent_id;
			m_child_ids = new int[child_ids.size()];

			//copy from dynamic to static (and lightweight) data container
			int i = 0;
			for (Integer id : child_ids) m_child_ids[i++] = id;
		}

		/** Parent identificator. */
		final int m_parent_id;

		/** Child identificators. */
		final int[] m_child_ids;

		///returns index of the input GT label, or -1 if label was not found
		public int findChildLabel(final int label)
		{
			for (int i=0; i < m_child_ids.length; ++i)
				if (m_child_ids[i] == label) return (i);

			return (-1);
		}
	}

	/** Temporal level representation. */
	public class TemporalLevel
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

		///returns index of the input GT label
		public int gt_findLabel(final int label)
		{
			for (int i=0; i < m_gt_lab.length; ++i)
				if (m_gt_lab[i] == label) return (i);

			throw new IllegalArgumentException("Label not found!");
		}

		///returns index of the input RES label
		public int res_findLabel(final int label)
		{
			for (int i=0; i < m_res_lab.length; ++i)
				if (m_res_lab[i] == label) return (i);

			throw new IllegalArgumentException("Label not found!");
		}

		/**
		 * Matching matrix, stored as a plain 1D array.
		 *
		 * For every position (j,i) (j-th row and i-th column), it contains
		 * number of voxels in the intersection between m_res_lab[j] label
		 * and m_gt_lab[i] label.
		 */
		int[] m_match = null;

		/// prints out the current matching matrix on a terminal
		public void PrintMatchingMatrix()
		{
			//first, read-out all GT labels and sort them
			Vector<Integer> GTlabs = new Vector<>(m_gt_lab.length);
			for (int l : m_gt_lab) GTlabs.add(l);
			GTlabs.sort(null);

			//second, the same for RES labels
			Vector<Integer> RESlabs = new Vector<>(m_res_lab.length);
			for (int l : m_res_lab) RESlabs.add(l);
			RESlabs.sort(null);

			//print the first "header" line
			System.out.print(this.m_level+":\t");
			for (int gt : GTlabs)
				System.out.print(gt+"\t");
			System.out.println();

			//print for every RES:
			final int m_match_lineSize = m_gt_lab.length;
			for (int res : RESlabs)
			{
				System.out.print(res+":\t");
				for (int gt : GTlabs)
				{
					System.out.print(
						m_match[ gt_findLabel(gt) + m_match_lineSize*res_findLabel(res) ]
						+"\t");
				}
				System.out.println();
			}

			//put "separation" empty line
			System.out.println();
		}

		/// prints out the current matching matrix on a terminal
		public void PrintMatchingMatrixWithAddrs()
		{
			//first, read-out all GT labels and sort them
			//(not really necessary overhere...)
			Vector<Integer> GTlabs = new Vector<>(m_gt_lab.length);
			for (int l : m_gt_lab) GTlabs.add(l);
			GTlabs.sort(null);

			//second, the same for RES labels
			//(not really necessary overhere...)
			Vector<Integer> RESlabs = new Vector<>(m_res_lab.length);
			for (int l : m_res_lab) RESlabs.add(l);
			RESlabs.sort(null);

			//print for every RES:
			final int m_match_lineSize = m_gt_lab.length;
			for (int res : RESlabs)
			{
				for (int gt : GTlabs)
				{
					System.out.println("time "+this.m_level
						+" GT ID "+gt+" RES ID "+res+" overlap "
						+m_match[ gt_findLabel(gt) + m_match_lineSize*res_findLabel(res) ]);
				}
			}
		}

		/// prints out the current matching matrix on a terminal
		public void PrintDetectedMatches()
		{
			//first, read-out all GT labels and sort them
			//(not really necessary overhere...)
			Vector<Integer> GTlabs = new Vector<>(m_gt_lab.length);
			for (int l : m_gt_lab) GTlabs.add(l);
			GTlabs.sort(null);

			for (int gt : GTlabs)
			{
				final int g = gt_findLabel(gt);
				if (m_gt_match[g] > -1)
					System.out.println("time "+this.m_level
						+" GT ID "+gt+" RES ID "+m_res_lab[m_gt_match[g]]);
			}
		}

		/**
		 * Indices of reference vertex matching, i.e., it is of the same length
		 * as m_gt_lab and it holds indices into the m_res_lab.
		 *
		 * It is initialized with -1 values. After matching is done,
		 * the value -1 corresponds to a FN vertex.
		 */
		int[] m_gt_match = null;

		/**
		 * Sets of indices of computed vertex matching, i.e., it is of the same length
		 * as m_res_lab and it holds sets of indices into the m_gt_lab.
		 *
		 * It is initialized with empty sets. After matching is done,
		 * an empty set corresponds to a FP vertex.
		 */
		HashSet<Integer>[] m_res_match = null;
	}

	//representation of tracks
	public final HashMap<Integer,Track> gt_tracks  = new HashMap<>();
	public final HashMap<Integer,Track> res_tracks = new HashMap<>();

	//representation of "label coverage" at temporal points
	public final Vector<TemporalLevel> levels = new Vector<>(1000,100);

	//representation of branching events
	public final Vector<Fork> gt_forks  = new Vector<>(1000);
	public final Vector<Fork> res_forks = new Vector<>(1000);

	//---------------------------------------------------------------------/
	//data loading functions:

	/// Loads the given filename AND checks it has appropriate GRAY16 voxel type.
	@SuppressWarnings("unchecked")
	public Img<UnsignedShortType> ReadImageG16(final String fname)
	throws IOException
	{
		Img<?> img = ReadImage(fname);

		//check input file for the appropriate type
		if (!(img.firstElement() instanceof UnsignedShortType))
		{
			log.error("Error reading file: "+fname);
			throw new IOException("Images are expected to have 16-bit gray voxels.");
		}

		return ((Img<UnsignedShortType>)img);
	}

	/// Loads the given filename AND checks it has appropriate GRAY8 voxel type.
	@SuppressWarnings("unchecked")
	public Img<UnsignedByteType> ReadImageG8(final String fname)
	throws IOException
	{
		Img<?> img = ReadImage(fname);

		//check input file for the appropriate type
		if (!(img.firstElement() instanceof UnsignedByteType))
		{
			log.error("Error reading file: "+fname);
			throw new IOException("Images are expected to have 8-bit gray voxels.");
		}

		return ((Img<UnsignedByteType>)img);
	}

	/// helper loader of images of any voxel type
	public Img<?> ReadImage(final String fname)
	throws IOException
	{
		Img<?> img = SimplifiedIO.openImage(fname);
		if (img == null)
		{
			log.error("Error reading file: "+fname);
			throw new IOException("Unable to read input file.");
		}

		log.info("Loaded image: "+fname);
		return (img);
	}


	public void LoadTrackFile(final String fname, final Map<Integer,Track> track_list)
	throws IOException
	{
		LoadTrackFile(fname, track_list, log);
	}

	public static
	void LoadTrackFile(final String fname, final Map<Integer,Track> track_list,
	                   final LogService log)
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
			//TRA-related report only if something was read in
			if (id > -1) log.error("Error reading track with ID="+id);

			//anyway, send the original error message further
			throw e;
		} finally {
			if (s != null)
			{
				s.close();
			}
		}

		log.info("Loaded track file: "+fname);
	}

	//---------------------------------------------------------------------/
	//aux data fillers -- merely a node data processors and classifiers

	public void ClassifyLabels(IterableInterval<UnsignedShortType> gt_img,
	                           RandomAccessibleInterval<UnsignedShortType> res_img)
	{
		//default behavior is to be very strict:
		//  complain whenever empty result or GT image is found
		ClassifyLabels(gt_img,res_img, true, levels.size(), 0.5);
	}

	public void ClassifyLabels(IterableInterval<UnsignedShortType> gt_img,
	                           RandomAccessibleInterval<UnsignedShortType> res_img,
	                           final boolean shouldComplainOnEmptyImages)
	{
		ClassifyLabels(gt_img,res_img, shouldComplainOnEmptyImages, levels.size(), 0.5);
	}

	public void ClassifyLabels(IterableInterval<UnsignedShortType> gt_img,
	                           RandomAccessibleInterval<UnsignedShortType> res_img,
	                           final int time)
	{
		//default behavior is to be very strict:
		//  complain whenever empty result or GT image is found
		ClassifyLabels(gt_img,res_img, true, time, 0.5);
	}

	public void ClassifyLabels(IterableInterval<UnsignedShortType> gt_img,
	                           RandomAccessibleInterval<UnsignedShortType> res_img,
	                           final boolean shouldComplainOnEmptyImages,
	                           final int time)
	{
		//default behavior is to be very strict:
		//  complain whenever empty result or GT image is found
		ClassifyLabels(gt_img,res_img, shouldComplainOnEmptyImages, time, 0.5);
	}

	@SuppressWarnings("unchecked")
	public void ClassifyLabels(IterableInterval<UnsignedShortType> gt_img,
	                           RandomAccessibleInterval<UnsignedShortType> res_img,
	                           final boolean shouldComplainOnEmptyImages,
	                           final int time,
	                           final double overlapRatio)
	{
		//check the sizes of the images
		if (gt_img.numDimensions() != res_img.numDimensions())
			throw new IllegalArgumentException("Image pair does not consist"
				+" of images of the same dimensionality.");

		for (int n=0; n < gt_img.numDimensions(); ++n)
			if (gt_img.dimension(n) != res_img.dimension(n))
				throw new IllegalArgumentException("Image pair does not consist"
					+" of images of the same size.");

		//create output TemporalLevel to which we gonna save our findings about both images
		TemporalLevel level = new TemporalLevel(time);

		//helper frequency histogram of discovered labels
		HashMap<Integer,Integer> gt_hist = new HashMap<>();
		HashMap<Integer,Integer> res_hist = new HashMap<>();
		//helper variables
		int label = -1;        //marker value = label
		Integer count = null;  //marker presence counter

		//sweep the gt image
		Cursor<UnsignedShortType> c = gt_img.localizingCursor();
		RandomAccess<UnsignedShortType> c2 = res_img.randomAccess();
		while (c.hasNext())
		{
			//update the GT histogram of found values/labels
			label = c.next().getInteger();
			count = gt_hist.get(label);
			gt_hist.put(label, count == null ? 1 : count+1);

			//update the RES histogram of found values/labels
			c2.setPosition(c);
			label = c2.get().getInteger();
			count = res_hist.get(label);
			res_hist.put(label, count == null ? 1 : count+1);
		}

		//we want to skip background bin, is there some?
		int zeroBinPresence = gt_hist.get(0) == null? 0 : 1;

		//copy the histogram to the level data class
		level.m_gt_lab = new int[gt_hist.size()-zeroBinPresence];
		level.m_gt_size = new int[gt_hist.size()-zeroBinPresence];
		level.m_gt_match = new int[gt_hist.size()-zeroBinPresence];

		int idx = 0; //label's index in the arrays
		for (Integer lbl : gt_hist.keySet())
		//NB: should be true: gt_hist.get(lbl) > 0
		if (lbl > 0)
		{
			level.m_gt_lab[idx] = lbl;
			level.m_gt_size[idx] = gt_hist.get(lbl);
			level.m_gt_match[idx] = -1;
			++idx;
		}

		//we want to skip background bin, is there some?
		zeroBinPresence = res_hist.get(0) == null? 0 : 1;

		//now, the same for the res image
		//copy the histogram to the level data class
		level.m_res_lab = new int[res_hist.size()-zeroBinPresence];
		level.m_res_size = new int[res_hist.size()-zeroBinPresence];
		level.m_res_match = (HashSet<Integer>[])new HashSet<?>[res_hist.size()-zeroBinPresence];

		idx = 0; //label's index in the arrays
		for (Integer lbl : res_hist.keySet())
		if (lbl > 0)
		{
			level.m_res_lab[idx] = lbl;
			level.m_res_size[idx] = res_hist.get(lbl);
			level.m_res_match[idx] = new HashSet<Integer>();
			++idx;
		}

		//check the images are not completely blank
		if (shouldComplainOnEmptyImages && level.m_res_lab.length == 0)
			throw new IllegalArgumentException("RES image has no markers!");
		if (shouldComplainOnEmptyImages && level.m_gt_lab.length == 0)
			throw new IllegalArgumentException("GT image has no markers!");

		//we don't need this one anymore
		gt_hist.clear();
		gt_hist = null;
		res_hist.clear();
		res_hist = null;

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

		//init the matching matrix
		final int m_match_lineSize = level.m_gt_lab.length;
		level.m_match = new int[m_match_lineSize * level.m_res_lab.length];

		//helper values: the label itself
		int gtLbl, resLbl;

		//sweep both images simultaneously and calculate intersection sizes
		c.reset();
		while (c.hasNext())
		{
			c.next();
			c2.setPosition(c);

			gtLbl  = c.get().getInteger();
			resLbl = c2.get().getInteger();

			//intersection?
			if (gtLbl > 0 && resLbl > 0)
				++level.m_match[ level.gt_findLabel(gtLbl) + m_match_lineSize*level.res_findLabel(resLbl) ];
		}

		//now that gt_, res_ and "gt_vs_res_" histograms are calculated,
		//determine the label correspondence attributes (m_gt_match and m_res_match)
		//(FindMatch())

		//for every gt label, find some res label that overlaps with it "significantly"
		double overlap;
		//sweep over all gt labels
		for (int i=0; i < level.m_gt_lab.length; ++i)
		{
			//sweep over all res labels
			for (int j=0; j < level.m_res_lab.length; ++j)
			{
				//check the overlap size
				overlap = (double)level.m_match[i + m_match_lineSize*j];
				overlap /= (double)level.m_gt_size[i];
				if (overlap > overlapRatio)
				{
					//we have significant overlap between i-th gt label and j-th res label
					level.m_gt_match[i] = j;
					level.m_res_match[j].add(i);

					//no need to scan further within res overlaps (due to >0.5 test)
					break;
				}
			}
		}

		//finally, "save" the level data
		levels.add(level);
	}


	/**
	 * Detect forks in a given acyclic oriented graph,
	 * which is to use the 'tracks' (that is the graph) and extract
	 * all forking events (any situation when mother track ends and
	 * continues with its two or more daughters) and save them
	 * in the 'forks'.
	 */
	public void DetectForks(final Map<Integer,Track> tracks, final Vector<Fork> forks)
	{
		//prepare the output structure
		forks.clear();

		//scan through tracks and note who am I a children of
		//NB: tracks know their parents, parents do not know explicitly their children
		//
		//       parent   list_of_kids
		HashMap<Integer,Vector<Integer>> families = new HashMap<>();

		for (Track track : tracks.values())
		{
			//does the current track have a parent?
			if (track.m_parent > 0)
			{
				//retrieve current list of kids of this track's parent
				Vector<Integer> kids = families.get(track.m_parent);
				if (kids == null)
				{
					//hmm, we are adding the first kid (and assume up to 3 kids)
					kids = new Vector<>(3);
					families.put(track.m_parent,kids);
				}

				kids.add(track.m_id);
			}
		}

		//now that we have (piece-by-piece) collected Fork-like data,
		//fill the output variable finally
		for (Integer parent : families.keySet())
		{
			//retrieve final list of kids of this parent
			Vector<Integer> kids = families.get(parent);
			//NB: should always hold: kids != null

			//enough kids for a fork?
			if (kids.size() > 1)
			{
				//yes, create the fork then
				forks.add( new Fork(parent,kids) );
			}
		}
	}

	//---------------------------------------------------------------------/
	/**
	 * Measure calculation happens in two stages. The first/upper stage does
	 * data pre-fetch and calculations to populate the TrackDataCache.
	 * TrackDataCache.calculate() actually does this job. The sort of
	 * calculations is such that the other measures could benefit from
	 * it and re-use it (instead of loading images again and doing same
	 * calculations again), and the results are stored in the cache.
	 * The second/bottom stage is measure-specific. It basically finishes
	 * the measure calculation procedure, possible using data from the cache.
	 *
	 * This function computes the common upper stage of measures.
	 */
	public void calculate(final String gtPath, final String resPath)
	throws IOException
	{
		log.info(" GT path: "+gtPath+"/TRA");
		log.info("RES path: "+resPath);
		//DEBUG//log.info("Computing the common upper part...");

		//fill the tracks data
		LoadTrackFile( gtPath+"/TRA/man_track.txt", gt_tracks);
		LoadTrackFile(resPath+"/res_track.txt", res_tracks);

		//iterate through the GT folder and read files, one by one,
		//find the appropriate file in the RES folder,
		//and call ClassifyLabels() for every such pair
		int time = 0;
		while (Files.isReadable(
			new File(String.format("%s/TRA/man_track%0"+noOfDigits+"d.tif",gtPath,time)).toPath()))
		{
			//read the image pair
			Img<UnsignedShortType> gt_img
				= ReadImageG16(String.format("%s/TRA/man_track%0"+noOfDigits+"d.tif",gtPath,time));

			Img<UnsignedShortType> res_img
				= ReadImageG16(String.format("%s/mask%0"+noOfDigits+"d.tif",resPath,time));

			ClassifyLabels(gt_img, res_img);
			++time;

			//to be on safe side (with memory)
			gt_img = null;
			res_img = null;
		}

		if (levels.size() == 0)
			throw new IllegalArgumentException("No reference (GT) image was found!");

		if (gt_tracks.size() == 0)
			throw new IllegalArgumentException("No reference (GT) track was found!");

		//calculate all forks -- branching events
		DetectForks(gt_tracks,  gt_forks);
		DetectForks(res_tracks, res_forks);

		//now that we got here, note for what data
		//this cache is valid, see validFor() above
		this.gtPath  = gtPath;
		this.resPath = resPath;
	}


	///checks whether given two nodes matches 1:1 in the given time point
	public boolean UniqueMatch(final int gt, final int res, final TemporalLevel level)
	{
		//check both nodes exist at the given time
		int gt_idx, res_idx;
		try {
			gt_idx  = level.gt_findLabel(gt);
			res_idx = level.res_findLabel(res);
		}
		catch (IllegalArgumentException e) {
			//if we got here, means some of the nodes is not available at the time point
			return false;
		}

		//both nodes are available, check they have 1:1 matching
		//see what matches the RES node has
		HashSet<Integer> match = level.m_res_match[res_idx];

		//check the RES node has exactly 1 match with some GT
		if (match.size() != 1) return false;

		//check that the one match is the requested GT node
		if (match.iterator().next() != gt_idx) return false;

		//all test passed, must be unique match then :)
		return true;
	}
}

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

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.util.Collection;
import java.util.Vector;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import de.mpicbg.ulman.ctc.workers.TrackDataCache.Track;
import de.mpicbg.ulman.ctc.workers.TrackDataCache.TemporalLevel;

public class TRA
{
	///shortcuts to some Fiji services
	protected final LogService log;

	///specifies how many digits are to be expected in the input filenames
	public int noOfDigits = 3;

	///a constructor requiring connection to Fiji report/log services
	public TRA(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	///reference on cache that we used recently
	protected TrackDataCache cache = null;

	///to provide the cache to others/to share it with others
	public TrackDataCache getCache()
	{ return (cache); }

	/**
	 * Calculation option: do consistency checks before TRA calculation.
	 * This may prevent from error messages later, e.g.
	 * from '[ERROR] TRA problem: Array index out of range: 3'
	 *
	 * Default CTC papers behaviour was not to do consistency checking.
	 * But this should be changed soon...
	 */
	public boolean doConsistencyCheck = false;

	/**
	 * Calculation option: do report list of discrepancies between the reference
	 * and computed tracking result.
	 * This is helpful for algorithm developers as it shows what, where and when
	 * was incorrect in their results.
	 */
	public boolean doLogReports = false;

	/**
	 * Calculation option: do report on how matching is being established between
	 * the reference and detected (computed) tracking segments (aka tracking nodes).
	 * This is helpful for algorithm developers as it shows what, where and when
	 * was incorrect in their results.
	 */
	public boolean doMatchingReports = false;

	/**
	 * This flag, when set to true, changes the default calculation mode, that is,
	 * the AOGM will be calculated instead of the TRA (which is essentially
	 * a normalized AOGM).
	 */
	public boolean doAOGM = false;

	// ----------- the TRA essentially starts here -----------
	//auxiliary data:

	/** Penalty configuration representation. */
	public class PenaltyConfig
	{
		/** Constructor. */
		public PenaltyConfig(final double ns, final double fn, final double fp,
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

	///the default (CellTrackingChallenge) weights used for AOGM/TRA
	public PenaltyConfig penalty
		= new PenaltyConfig(5.0, 10.0, 1.0, 1.0, 1.5, 1.0);

	///the to-be-calculated TRA value (based on the AOGM measure)
	protected double aogm = 0.0;

	///the largest incorrect split detected
	protected int max_split = 1;

	///logs to note discrepancies between GT and RES tracks
	public List<String> logNS = new LinkedList<>();
	public List<String> logFN = new LinkedList<>();
	public List<String> logFP = new LinkedList<>();
	public List<String> logED = new LinkedList<>();
	public List<String> logEA = new LinkedList<>();
	public List<String> logEC = new LinkedList<>();
	public List<String> logMatch = new LinkedList<>();

	///convenience function to report given log -- one of the above
	public void reportLog(final List<String> log)
	{
		for (String msg : log)
			this.log.info(msg);
	}

	//---------------------------------------------------------------------/
	//aux data fillers -- merely a node data processors and classifiers

	/**
	 * Internal test of GT data sanity mainly to alleviate for heavy bound checking etc.
	 * during the TRA/AOGM calculation.
	 */
	public void CheckConsistency(final Vector<TemporalLevel> levels,
		final Map<Integer,Track> tracks,
		final boolean isGTcheck)
	{
		//a helper string for messaging
		final String DS = isGTcheck? " GT " : " RES ";
		log.info("Testing the"+DS+"data for consistency...");

		//check that all tracks metadata (tracks) are sane and have a counterpart in the images (levels)
		//therefore, over all tracks
		for (Track track : tracks.values())
		{
			//check for track bounds: do they fall within the temporal interval of loaded images
			if (track.m_begin < 0 || track.m_begin >= levels.size()
			   || track.m_end < 0 || track.m_end   >= levels.size())
				throw new IllegalArgumentException("The"+DS+"track with label "
					+track.m_id+" begins or ends outside the image sequence!");

			if (track.m_end < track.m_begin)
				throw new IllegalArgumentException("The"+DS+"track with label "
					+track.m_id+" is declared to end before it begins!");

			//check that we have located the track's label in the images in the whole track temporal span
			for (int t = track.m_begin; t <= track.m_end; ++t)
			{
				try {
					if (isGTcheck)
						levels.get(t).gt_findLabel(track.m_id);
					else
						levels.get(t).res_findLabel(track.m_id);
					//NB: level.get(t) should work because of the previous test
				}
				catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("The"+DS+"track with label "
						+track.m_id+" was not found in the image at time point "+t+"!");
				}
			}

			//do we have a mother?
			if (track.m_parent > 0)
			{
				//yes, is she listed among the available tracks?
				if (!tracks.containsKey(track.m_parent))
					throw new IllegalArgumentException("Reference to unavailable parent track "
						+track.m_parent+" in the"+DS+"track with label "+track.m_id+"!");

				//check if daughter track does not start earlier than mother track ends
				if (track.m_begin <= tracks.get(track.m_parent).m_end)
					throw new IllegalArgumentException("Invalid parent connection for the"
						+DS+"track with label "+track.m_id+"!");
			}
		}

		//check that all labels discovered in images (levels) have a counterpart in tracks metadata (tracks)
		//therefore, iterate over all time points:
		for (int t = 0; t < levels.size(); ++t)
		{
			//over all labels found in an image at time t
			final int[] idArray = isGTcheck ? levels.get(t).m_gt_lab : levels.get(t).m_res_lab;
			for (int id : idArray)
			{
				//find it in the track metadata   //if (!tracks.containsKey(id))
				final Track track = tracks.get(id);

				//do we have such a track at all?
				if (track == null)
					throw new IllegalArgumentException("The"+DS+"track with label "+id
						+" found in image at time point "+t+" is not declared (in tracks.txt) at all!");

				//if we do, does the current image fall into the range declared in the metadata?
				if (t < track.m_begin || t > track.m_end)
					throw new IllegalArgumentException("The"+DS+"track with label "+id
						+" found in image at time point "+t+" is not declared (in tracks.txt) to be found here!");
			}
		}
	}


	/**
	 * Returns index of RES label that matches with given GT lbl,
	 * or -1 if no such label was found.
	 */
	protected int GetGTMatch(final TemporalLevel level, final int lbl)
	{
		return ( level.m_gt_match[level.gt_findLabel(lbl)] );
	}


	/**
	 * Returns collection of indices of GT labels that matches with given RES lbl,
	 * or collection with single item (of -1 value) if no such label was found.
	 */
	protected Collection<Integer> GetResMatch(final TemporalLevel level, final int lbl)
	{
		final int idx = level.res_findLabel(lbl);
		if (idx != -1)
		{
			return (level.m_res_match[idx]);
		}
		else
		{
			//return "not-found" set
			HashSet<Integer> tmp = new HashSet<>();
			tmp.add(-1);
			return (tmp);
		}
	}

	//---------------------------------------------------------------------/
	//aux data fillers -- merely an edge data classifiers

	/**
	 * Check if there is an edge of a given type between given
	 * temporal levels in the reference tracks.
	 */
	protected boolean ExistGTEdge(final Vector<TemporalLevel> levels,
		final int start_level,
		final int start_index,
		final int end_level,
		final int end_index,
		final Map<Integer,Track> tracks,
		boolean[] parental) //an output variable...
	{
		//TODO: test if start_level and end_level are sane...

		//reasonable label indices? existing labels?
		if (start_index != -1 && end_index != -1)
		{
			// get labels at given times at given indices
			final int start_label = levels.get(start_level).m_gt_lab[start_index];
			final int end_label = levels.get(end_level).m_gt_lab[end_index];

			//check the type of the edge
			if (start_label == end_label)
			{
				// the edge connects nodes from the same track,
				// are the nodes temporal consecutive? is it really an edge?
				if ((start_level + 1) == end_level)
				{
					parental[0] = false; //same track, can't be a parental link
					return true;
				}
			}
			else
			{
				// the edge connects two tracks, get them...
				final Track parent = tracks.get(start_label);
				final Track child = tracks.get(end_label);

				//is the edge correctly connecting two tracks?
				if (parent.m_end == start_level && child.m_begin == end_level
				    && child.m_parent == start_label)
				{
					parental[0] = true;
					return true;
				}
			}
		}

		return (false);
	}


	/**
	 * Check if there is an edge of a given type between given
	 * temporal levels in the computed tracks.
	 */
	protected boolean ExistResEdge(final Vector<TemporalLevel> levels,
		final int start_level,
		final int start_index,
		final int end_level,
		final int end_index,
		final Map<Integer,Track> tracks)
	{
		//TODO: test if start_level and end_level are sane...

		//reasonable label indices? existing labels?
		//do start and end labels/nodes have 1:1 matching?
		if (start_index != -1 && end_index != -1
		    && levels.get(start_level).m_res_match[start_index].size() == 1
			 && levels.get(end_level).m_res_match[end_index].size() == 1)
		{
			// get labels at given times at given indices
			final int start_label = levels.get(start_level).m_res_lab[start_index];
			final int end_label = levels.get(end_level).m_res_lab[end_index];

			//check the type of the edge
			if (start_label == end_label)
			{
				// the edge connects nodes from the same track,
				// are the nodes temporal consecutive? is it really an edge?
				return ((start_level + 1) == end_level);
			}
			else
			{
				// the edge connects two tracks, get them...
				final Track parent = tracks.get(start_label);
				final Track child = tracks.get(end_label);

				//is the edge correctly connecting two tracks?
				return (parent.m_end == start_level && child.m_begin == end_level
				        && child.m_parent == start_label);
			}
		}

		return (false);
	}


	/** Find edges in the computed tracks that must be removed or altered. */
	protected void FindEDAndECEdges(final Vector<TemporalLevel> levels,
		final Map<Integer,Track> gt_tracks,
		final Map<Integer,Track> res_tracks)
	{
		final boolean[] parent = new boolean[1];
		int start_level, end_level;
		Collection<Integer> start_match, end_match;

		//over all tracks/labels present in the result data
		for (Integer res_track_id : res_tracks.keySet())
		{
			//short-cut to the track data
			final Track res_track = res_tracks.get(res_track_id);

			// A) check the edge between the first node of the current track
			// B) and the last one of the parent track
			// A):
			end_level = res_track.m_begin;
			end_match = GetResMatch(levels.get(end_level), res_track_id);

			//does this track have a parent?
			if (res_track.m_parent > 0)
			{
				//yes, it does
				// B):
				start_level = res_tracks.get(res_track.m_parent).m_end;
				start_match = GetResMatch(levels.get(start_level), res_track.m_parent);

				//*_match contain lists of indices of GT labels that matches
				if (start_match.size() == 1 && end_match.size() == 1)
				{
					//right number of matches, deal with this RES edge:
					if (ExistGTEdge(levels, start_level, start_match.iterator().next(),
					                end_level, end_match.iterator().next(), gt_tracks, parent))
					{
						//corresponding edge exists in GT, does it connect two different tracks too?
						if (parent[0] == false)
						{
							//it does not connect different tracks, that's an error
							aogm += penalty.m_ec;
							if (doLogReports)
								logEC.add(String.format("[T=%d Label=%d] -> [T=%d Label=%d]",
									start_level, res_track.m_parent, end_level, res_track_id));
						}
					}
					else
					{
						//there is no corresponding edge in GT, that's an error
						aogm += penalty.m_ed;
						if (doLogReports)
							logED.add(String.format("[T=%d Label=%d] -> [T=%d Label=%d]",
								start_level, res_track.m_parent, end_level, res_track_id));
					}
				}
			}

			// check edges within the current track
			for (int t = res_track.m_begin; t < res_track.m_end; ++t)
			{
				//define temporal consecutive nodes
				start_level = end_level;
				start_match = end_match;
				end_level = t + 1;
				end_match = GetResMatch(levels.get(end_level), res_track_id);

				//*_match contain lists of indices of GT labels that matches
				if (start_match.size() == 1 && end_match.size() == 1)
				{
					//we have a reasonable edge here, deal with this RES edge:
					if (ExistGTEdge(levels, start_level, start_match.iterator().next(),
					                end_level, end_match.iterator().next(), gt_tracks, parent))
					{
						//corresponding edge exists in GT, should not be parental link however
						if (parent[0] == true)
						{
							//it is parental, that's an error
							aogm += penalty.m_ec;
							if (doLogReports)
								logEC.add(String.format("[T=%d Label=%d] -> [T=%d Label=%d]",
									start_level, res_track_id, end_level, res_track_id));
						}
					}
					else
					{
						//there is no corresponding edge in GT, that's an error
						aogm += penalty.m_ed;
						if (doLogReports)
							logED.add(String.format("[T=%d Label=%d] -> [T=%d Label=%d]",
								start_level, res_track_id, end_level, res_track_id));
					}
				}
			}
		}
	}


	/** Find edges in the reference tracks that must be added. */
	protected void FindEAEdges(final Vector<TemporalLevel> levels,
		final Map<Integer,Track> gt_tracks,
		final Map<Integer,Track> res_tracks)
	{
		int start_level, end_level;
		int start_index, end_index;

		for (Integer gt_track_id : gt_tracks.keySet())
		{
			//short-cut to the track data
			final Track gt_track = gt_tracks.get(gt_track_id);

			// A) check the edge between the first node of the current track
			// B) and the last one of the parent track
			// A):
			end_level = gt_track.m_begin;
			end_index = GetGTMatch(levels.get(end_level), gt_track_id);

			//does this track have a parent?
			if (gt_track.m_parent > 0)
			{
				//yes, it does
				// B):
				start_level = gt_tracks.get(gt_track.m_parent).m_end;
				start_index = GetGTMatch(levels.get(start_level), gt_track.m_parent);
				//*_index contain indices of RES labels that matches ...

				if (!ExistResEdge(levels, start_level, start_index, end_level, end_index, res_tracks))
				{
					//... but there is no edge between them, that's an error
					aogm += penalty.m_ea;
					if (doLogReports)
						logEA.add(String.format("[T=%d GT_label=%d] -> [T=%d GT_label=%d]",
							start_level, gt_track.m_parent, end_level, gt_track_id));
				}
			}

			// check edges within the current track
			for (int t = gt_track.m_begin; t < gt_track.m_end; ++t)
			{
				//define temporal consecutive nodes
				start_level = end_level;
				start_index = end_index;
				end_level = t + 1;
				end_index = GetGTMatch(levels.get(end_level), gt_track_id);
				//*_index contain indices of RES labels that matches ...

				if (!ExistResEdge(levels, start_level, start_index, end_level, end_index, res_tracks))
				{
					//... but there is no edge between them, that's an error
					aogm += penalty.m_ea;
					if (doLogReports)
						logEA.add(String.format("[T=%d GT_label=%d] -> [T=%d GT_label=%d]",
							start_level, gt_track_id, end_level, gt_track_id));
				}
			}
		}
	}

	//---------------------------------------------------------------------/
	///the main TRA calculator/calculation pipeline
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
			cache.noOfDigits = noOfDigits;
			cache.calculate(gtPath,resPath);
		}

		//do the bottom stage
		//DEBUG//log.info("Computing the TRA bottom part...");
		aogm = 0.0;

		if (doLogReports)
		{
			logNS.add(String.format("----------Splitting Operations (Penalty=%g)----------", penalty.m_ns));
			logFN.add(String.format("----------False Negative Vertices (Penalty=%g)----------", penalty.m_fn));
			logFP.add(String.format("----------False Positive Vertices (Penalty=%g)----------", penalty.m_fp));
			logED.add(String.format("----------Redundant Edges To Be Deleted (Penalty=%g)----------", penalty.m_ed));
			logEA.add(String.format("----------Edges To Be Added (Penalty=%g)----------", penalty.m_ea));
			logEC.add(String.format("----------Edges with Wrong Semantics (Penalty=%g)----------", penalty.m_ec));
		}
		if (doMatchingReports)
			logMatch.add(String.format("----------Vertices Matching Status (No Penalty)----------", penalty.m_ns));

		//shadows of the/short-cuts to the cache data
		final HashMap<Integer,Track> gt_tracks  = cache.gt_tracks;
		final HashMap<Integer,Track> res_tracks = cache.res_tracks;
		final Vector<TemporalLevel> levels = cache.levels;

		if (doConsistencyCheck)
		{
			CheckConsistency(levels,  gt_tracks, true);
			CheckConsistency(levels, res_tracks, false);
		}

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

		FindEDAndECEdges(levels, gt_tracks, res_tracks);
		FindEAEdges(levels, gt_tracks, res_tracks);
		//AOGM calculation ends here

		//should the log reports be printed?
		if (doLogReports)
		{
			reportLog(logNS);
			reportLog(logFN);
			reportLog(logFP);
			reportLog(logED);
			reportLog(logEA);
			reportLog(logEC);
		}
		if (doMatchingReports) reportLog(logMatch);

		//now, the (old) TRA between GT and RES is calculated:
		//the old refers to the un-normalized TRA value, interval [0,infinity)
		// (approx. an energy required to CORRECT tracking result)

		if (doAOGM == false)
		{
			//calculate the (old) TRA when no result is supplied
			// (approx. an energy required to CREATE tracking result from the scratch)
			//
			//how many parental links to add
			int num_par = 0;
			//how many track links (edges) to add
			int sum = 0;

			for (Integer id : gt_tracks.keySet())
			{
				Track t = gt_tracks.get(id);
				sum += t.m_end - t.m_begin;

				if (t.m_parent > 0) ++num_par;
			}

			final double aogm_empty = penalty.m_fn * (sum + gt_tracks.size()) //adding nodes
											+ penalty.m_ea * (sum + num_par);         //adding edges

			log.info("---");
			log.info("AOGM to curate  the  given  result: "+aogm);
			log.info("AOGM to build a new correct result: "+aogm_empty);

			//if correcting is more expensive than creating, we assume user deletes
			//the whole result and starts from the scratch, hence aogm = aogm_empty
			aogm = aogm > aogm_empty ? aogm_empty : aogm;

			//normalization:
			aogm = 1.0 - aogm/aogm_empty;

			log.info("normalized AOGM = TRA: "+aogm);
		}
		else
		{
			//just report the AOGM as it is...
			log.info("---");
			log.info("AOGM: "+aogm);
		}
		return (aogm);
	}

	/// This is the wrapper TRA calculator, assuring complete re-calculation.
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		return this.calculate(gtPath,resPath,null);
	}
}

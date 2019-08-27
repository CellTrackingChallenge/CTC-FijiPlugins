package de.mpicbg.ulman.ctc.workers;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;

import org.scijava.log.LogService;

import de.mpicbg.ulman.ctc.workers.TrackDataCache.Track;

/**
 * Collection of individual tracks available for a time lapse data.
 * Every track is represented with the embedded class TrackDataCache.Track.
 *
 * @author Vladimir Ulman, 2018
 */
public class TrackRecords
{
	/** All tracks are stored here. Must hold: tracks.get(id).ID == id */
	protected HashMap<Integer,Track> tracks = new HashMap<>();
	//------------------------------------------------------------------------


	/** Declares existence of a new track starting from time point 'curTime'
	    with 'parentID', returns unique ID of this new track. */
	public int startNewTrack(final int curTime, final int parentID)
	{
		final int ID = this.getNextAvailTrackID();
		tracks.put(ID, new Track(ID, curTime, parentID));
		return ID;
	}

	/** Declares existence of a new track starting from time point 'curTime'
	    with no parent, returns unique ID of this new track. */
	public int startNewTrack(final int curTime)
	{
		return this.startNewTrack(curTime,0);
	}

	/** Updates the last time point of the track 'ID'.
	    A track becomes finished/closed by stopping updating it. */
	public void updateTrack(final int ID, final int curTime)
	{
		tracks.get(ID).prolongTrack(curTime);
	}

	public int getParentOfTrack(int ID)
	{
		return ( tracks.get(ID) != null ? tracks.get(ID).m_parent : 0 );
	}

	public int getStartTimeOfTrack(int ID)
	{
		return ( tracks.get(ID) != null ? tracks.get(ID).m_begin : 0 );
	}

	public int getEndTimeOfTrack(int ID)
	{
		return ( tracks.get(ID) != null ? tracks.get(ID).m_end : 0 );
	}

	/** Removes entire record about the track. */
	public void removeTrack(final int ID)
	{
		tracks.remove(ID);
	}
	//------------------------------------------------------------------------


	public void exportToConsole()
	{
	    exportToConsole(0);
	}

	public void exportToConsole(final int timeShift)
	{
		for (final Track t : tracks.values())
			System.out.println(t.exportToString(timeShift));
	}

	/** Writes the current content into 'outFileName', possibly overwriting it.
	    The method can throw RuntimeException if things go wrong. */
	public void exportToFile(final String outFileName)
	{
		exportToFile(outFileName,0);
	}

	/** Writes the current content into 'outFileName', possibly overwriting it.
	    The reported times are adjusted (incremented) with 'timeShift'.
	    The method can throw RuntimeException if things go wrong. */
	public void exportToFile(final String outFileName, final int timeShift)
	{
		try
		{
			final BufferedWriter f = new BufferedWriter( new FileWriter(outFileName) );
			for (final Track t : tracks.values())
			{
				f.write(t.exportToString(timeShift));
				f.newLine();
			}
			f.close();
		}
		catch (IOException e) {
			//just forward the exception to whom it may concern
			throw new RuntimeException(e);
		}
	}
	//------------------------------------------------------------------------


	public void loadTrackFile(final String fname, final LogService log)
	throws IOException
	{
		TrackDataCache.LoadTrackFile(fname, tracks, log);
	}
	//------------------------------------------------------------------------


	/** Helper track ID tracker... */
	private int lastUsedTrackID = 0;

	/** Returns next available non-colliding track ID. */
	public int getNextAvailTrackID()
	{
		++lastUsedTrackID;
		return lastUsedTrackID;
	}
};

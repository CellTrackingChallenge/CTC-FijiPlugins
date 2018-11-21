package de.mpicbg.ulman.workers;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;

import org.scijava.log.LogService;

import de.mpicbg.ulman.workers.TrackDataCache.Track;

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

	/** Removes entire record about the track. */
	public void removeTrack(final int ID)
	{
		tracks.remove(ID);
	}
	//------------------------------------------------------------------------


	public void exportToConsole()
	{
		for (final Track t : tracks.values())
			System.out.println(t.exportToString());
	}

	/** Writes the current content into 'outFileName', possibly overwriting it.
	 * The method can throw RuntimeException if things go wrong.
	 */
	public void exportToFile(final String outFileName)
	{
		try
		{
			final BufferedWriter f = new BufferedWriter( new FileWriter(outFileName) );
			for (final Track t : tracks.values())
			{
				f.write(t.exportToString());
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

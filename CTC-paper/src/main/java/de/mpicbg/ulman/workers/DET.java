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

import java.util.Collection;
import java.util.Vector;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import de.mpicbg.ulman.workers.TrackDataCache.Track;
import de.mpicbg.ulman.workers.TrackDataCache.TemporalLevel;

public class DET extends TRA
{
	///a constructor requiring connection to Fiji report/log services
	public DET(final LogService _log)
	{
		super(_log);
	}

}

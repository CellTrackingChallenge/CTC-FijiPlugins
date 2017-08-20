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
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.SCIFIOImgPlus;
import io.scif.img.ImgOpener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.util.Collection;
import java.util.Vector;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Scanner;

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

	///GT and RES paths combination for which this cache is valid, null means invalid
	private String gtPath = null;
	///GT and RES paths combination for which this cache is valid, null means invalid
	private String resPath = null;

	///reference-based-only check if the parameters are those on which this cache was computed
	public boolean validFor(final String _gtPath, final String _resPath)
	{
		return (gtPath == _gtPath && resPath == _resPath);
	}


	// ----------- the common upper stage essentially starts here -----------
	//auxiliary data:


	//---------------------------------------------------------------------/
	/**
	 * Measure calculation happens in two stages. The first/upper stage does
	 * data pre-fetch and calculations to populate the TrackDataCache.
	 * TrackDataCache.calculate() actually does this job. The sort of
	 * calculations is such that the other measures could benefit from
	 * it and re-use it (instead of loading images again and doing some
	 * calculations again), and the results are stored in the cache.
	 * The second/bottom stage is measure-specific. It basically finishes
	 * the measure calculation procedure, possible using data from the cache.
	 *
	 * This function computes the common upper stage of measures.
	 */
	public void calculate(final String gtPath, final String resPath)
	{
		log.info(" GT path: "+gtPath);
		log.info("RES path: "+resPath);
		log.info("Computing the common upper part...");

		//......

		//now that we got here, note for what data
		//this cache is valid, see validFor() above
		this.gtPath  = gtPath;
		this.resPath = resPath;
	}
}

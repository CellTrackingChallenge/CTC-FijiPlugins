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
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.SCIFIOImgPlus;
import io.scif.img.ImgOpener;
import io.scif.img.ImgSaver;
import net.imglib2.exception.IncompatibleTypeException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.Vector;
import java.util.Collection;
import java.util.List;

import java.io.*;
import java.util.Scanner;

public class TRA
{
	///shortcuts to some Fiji services
	final LogService log;

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

	//helper functions:

	///the main TRA calculator/calculation pipeline
	public float calculate(final String gtPath, final String resPath)
	{
		log.info(" GT path: "+gtPath);
		log.info("RES path: "+resPath);


		return (0.02f);
	}
}

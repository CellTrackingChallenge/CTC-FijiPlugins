/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;
import net.imagej.ops.OpService;

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

import java.util.Vector;

import de.mpicbg.ulman.waitingRoom.DefaultCombineGTsViaMarkers;

public class machineGTViaMarkers_Worker
{
	///shortcuts to some Fiji services
	final OpService ops;
	final LogService log;

	///shortcut to future mainstream imagej-ops function
	final DefaultCombineGTsViaMarkers<?> myOps;

	///a convenience constructor requiring connection to some Fiji services
	@SuppressWarnings("rawtypes")
	public machineGTViaMarkers_Worker(final OpService _ops, final LogService _log)
	{
		//TODO: check that non-null was given for _ops and _log!
		ops = _ops;
		log = _log;

		myOps = new DefaultCombineGTsViaMarkers();
		//TODO: check myOps is not null, BTW: could it possibly be?
	}

	///prevent from creating the class without any connection
	@SuppressWarnings("unused")
	private machineGTViaMarkers_Worker()
	{ ops = null; log = null; myOps = null; } //this is to get rid of some warnings

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void work(final String... args) throws ImgIOException
	{
		//check the minimum number of input parameters, should be odd number
		if (args.length < 5 || (args.length&1)==0)
		{
			//print help
			log.info("Usage: img1 weight1 ... TRAimg threshold outImg");
			log.info("All img1 (path to an image file) are TRA marker-wise combined into output outImg.");
			throw new ImgIOException("At least one input image, exactly one marker image and one treshold plus one output image are expected.");
		}

		//the number of input pairs, the test above enforces it is nicely divisible by 2
		final int inputImagesCount = (args.length-3) / 2;

		//container to store the input images
		final Vector<RandomAccessibleInterval<?>> inImgs
			= new Vector<RandomAccessibleInterval<?>>(inputImagesCount);

		//container to store the input weights
		final Vector<Float> inWeights
			= new Vector<Float>(inputImagesCount);

		//marker image
		Img<UnsignedShortType> markerImg = null;

		//now, try to load the input images
		final SCIFIOConfig openingRegime = new SCIFIOConfig();
		openingRegime.imgOpenerSetImgModes(ImgMode.ARRAY);
		SCIFIOImgPlus<?> img = null;

		//load all of them
		for (int i=0; i < inputImagesCount+1; ++i)
		{
			try {
				//load the image
				log.info("Reading pair: "+args[2*i]+" "+args[2*i +1]);
				ImgOpener imgOpener = new ImgOpener();
				img = imgOpener.openImgs(args[2*i],openingRegime).get(0);

				//check the type of the image (the combineGTs plug-in requires RealType<>)
				//TODO this code does not assure that all input images are of the same type
				if (!(img.firstElement() instanceof RealType<?>))
					throw new ImgIOException("Input image voxels must be scalars.");

				//check the dimensions, against the first loaded image
				//(if processing second or later image already)
				for (int d=0; i > 0 && d < img.numDimensions(); ++d)
					if (img.dimension(d) != inImgs.get(0).dimension(d))
						throw new ImgIOException((i+1)+"th image has different size in the "
								+d+"th dimension than the first image.");

				//all is fine, add this one into the input list
				if (i < inputImagesCount) inImgs.add(img);
				//or, if loading the last image, remember it as the marker image 
				else markerImg = (Img<UnsignedShortType>)img;

				//also parse and store the weight
				if (i < inputImagesCount)
					inWeights.add( Float.parseFloat(args[2*i +1]) );
			}
			catch (ImgIOException e) {
				log.error("Error reading file: "+args[2*i]);
				log.error("Error msg: "+e);
				throw new ImgIOException("Unable to read input file.");
			}
		}

		//parse threshold value
		final float threshold = Float.parseFloat(args[args.length-2]);

		//create the output image (TODO: need to use copy() of the first input image?)
		SCIFIOImgPlus<UnsignedShortType> outImg
			= new SCIFIOImgPlus<UnsignedShortType>(
					ops.create().img(inImgs.get(0), new UnsignedShortType()));

		//setup the debug image filename
		String newName = args[args.length-1];
		final int dotSeparatorIdx = newName.lastIndexOf(".");
		newName = new String(newName.substring(0, dotSeparatorIdx)+"__DBG"+newName.substring(dotSeparatorIdx));

		//NB: we have checked that images are of RealType<?> in the loading loop,
		//    so we know we can cast to raw type to be able to call the combineGTs()
		System.out.println("calling general convenience CombineGTsViaMarkers with threshold="+threshold);
		//ops.images().combineGTsViaMarkers((Vector)inImgs, markerImg, threshold, outImg);
		//ops.images().combineGTsViaMarkers((Vector)inImgs, markerImg, threshold, outImg, newName);
		myOps.setParams(inWeights, threshold, newName);
		myOps.compute((Vector)inImgs, markerImg, outImg);

		try {
			log.info("Saving file: "+args[args.length-1]);
			ImgSaver imgSaver = new ImgSaver();
			imgSaver.saveImg(args[args.length-1], outImg);
		}
		catch (ImgIOException | IncompatibleTypeException e) {
			log.error("Error writing file: "+args[args.length-1]);
			log.error("Error msg: "+e);
			throw new ImgIOException("Unable to write output file.");
		}
	}
}

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
import net.imagej.ops.OpService;

import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Vector;

public class machineGTViaMarkers_Worker
{
	///shortcuts to some Fiji services
	final LogService log;

	///shortcut to future mainstream imagej-ops function
	final DefaultCombineGTsViaMarkers<?> myOps;

	///a convenience constructor requiring connection to some Fiji services
	@SuppressWarnings("rawtypes")
	public machineGTViaMarkers_Worker(final OpService _ops, final LogService _log)
	{
		if (_ops == null || _log == null)
			throw new RuntimeException("Please, give me existing OpService and LogService.");

		log = _log;
		myOps = new DefaultCombineGTsViaMarkers(_ops);
	}

	///prevent from creating the class without any connection
	@SuppressWarnings("unused")
	private machineGTViaMarkers_Worker()
	{ log = null; myOps = null; } //this is to get rid of some warnings

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void work(final String... args)
	{
		//check the minimum number of input parameters, should be odd number
		if (args.length < 5 || (args.length&1)==0)
		{
			//print help
			log.info("Usage: img1 weight1 ... TRAimg threshold outImg");
			log.info("All img1 (path to an image file) are TRA marker-wise combined into output outImg.");
			throw new RuntimeException("At least one input image, exactly one marker image and one treshold plus one output image are expected.");
		}

		//the number of input pairs, the test above enforces it is nicely divisible by 2
		final int inputImagesCount = (args.length-3) / 2;

		//container to store the input images
		final Vector<RandomAccessibleInterval<?>> inImgs = new Vector<>(inputImagesCount);

		//container to store the input weights
		final Vector<Float> inWeights = new Vector<>(inputImagesCount);

		//marker image
		Img<UnsignedShortType> markerImg = null;

		//now, try to load the input images
		Img<?> img = null;
		Object firstImgVoxelType = null;
		String firstImgVoxelTypeString = null;

		//load all of them
		for (int i=0; i < inputImagesCount+1; ++i)
		{
			//load the image
			log.info("Reading pair: "+args[2*i]+" "+args[2*i +1]);
			img = SimplifiedIO.openImage(args[2*i]);

			//check the type of the image (the combineGTs plug-in requires RealType<>)
			if (!(img.firstElement() instanceof RealType<?>))
				throw new RuntimeException("Input image voxels must be scalars.");

			//check that all input images are of the same type
			//NB: the check excludes the tracking markers image
			if (firstImgVoxelType == null)
			{
				firstImgVoxelType = img.firstElement();
				firstImgVoxelTypeString = firstImgVoxelType.getClass().getSimpleName();
			}
			else if (i < inputImagesCount && !(img.firstElement().getClass().getSimpleName().startsWith(firstImgVoxelTypeString)))
			{
				log.info("first  image  voxel type: "+firstImgVoxelType.getClass().getName());
				log.info("current image voxel type: "+img.firstElement().getClass().getName());
				throw new RuntimeException("Voxel types of all input images must be the same.");
			}

			//check the dimensions, against the first loaded image
			//(if processing second or later image already)
			for (int d=0; i > 0 && d < img.numDimensions(); ++d)
				if (img.dimension(d) != inImgs.get(0).dimension(d))
					throw new RuntimeException((i+1)+"th image has different size in the "
							+d+"th dimension than the first image.");

			//all is fine, add this one into the input list
			if (i < inputImagesCount) inImgs.add(img);
			//or, if loading the last image, remember it as the marker image
			else
			{
				if (!(img.firstElement() instanceof UnsignedShortType))
					throw new RuntimeException("Markers must be stored in 16bits gray image.");
				markerImg = (Img<UnsignedShortType>)img;
			}

			//also parse and store the weight
			if (i < inputImagesCount)
				inWeights.add( Float.parseFloat(args[2*i +1]) );
		}

		//parse threshold value
		final float threshold = Float.parseFloat(args[args.length-2]);

		//since the simplifiedIO() returns actually always ImgPlus,
		//we better strip away the "plus" extras to make it pure Img<>
		if (markerImg instanceof ImgPlus)
			markerImg = ((ImgPlus<UnsignedShortType>) markerImg).getImg();

		//setup the debug image filename
		/*
		String newName = args[args.length-1];
		final int dotSeparatorIdx = newName.lastIndexOf(".");
		newName = new String(newName.substring(0, dotSeparatorIdx)+"__DBG"+newName.substring(dotSeparatorIdx));
		*/
		final String newName = null;

		//NB: we have checked that images are of RealType<?> in the loading loop,
		//    so we know we can cast to raw type to be able to call the combineGTs()
		System.out.println("calling CombineGTsViaMarkers with threshold="+threshold);
		//ops.images().combineGTsViaMarkers((Vector)inImgs, markerImg, threshold, outImg);
		//ops.images().combineGTsViaMarkers((Vector)inImgs, markerImg, threshold, outImg, newName);
		myOps.setParams(inWeights, threshold, newName);

		//obtain an output image (that happens to be of the same size and type as the markerImg)
		Img<UnsignedShortType> outImg = myOps.compute((Vector)inImgs, markerImg);

		log.info("Saving file: "+args[args.length-1]);
		SimplifiedIO.saveImage(outImg, args[args.length-1]);
	}
}

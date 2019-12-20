/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.silverGT;

import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Vector;

/**
 * This class essentially takes care of the IO burden. One provides it with
 * a weighted voting fusion algorithm and a "formatted" job specification
 * as a list of strings:
 *
 * image1_asPathAndFilename, image1_asWeightAsRealNumber,
 * image2_asPathAndFilename, image2_asWeightAsRealNumber,
 * ...
 * imageN_asPathAndFilename, imageN_asWeightAsRealNumber,
 * imageMarker_PathAndFilename, ThresholdAsRealNumber,
 * imageOutput_asPathAndFilename
 *
 * The class then reads the respective images, complements them with
 * extracted weights and the threshold, calls the fusion algorithm,
 * and saves the output image.
 */
public
class WeightedVotingFusionFeeder<IT extends RealType<IT>, LT extends IntegerType<LT>>
{
	///prevent from creating the class without any connection
	@SuppressWarnings("unused")
	private WeightedVotingFusionFeeder()
	{ log = null; } //this is to get rid of some warnings

	private final LogService log;

	public
	WeightedVotingFusionFeeder(final LogService _log)
	{
		if (_log == null)
			throw new RuntimeException("Please, give me existing LogService.");

		log = _log;
	}

	public
	WeightedVotingFusionFeeder<IT,LT> setAlgorithm(final WeightedVotingFusionAlgorithm<IT,LT> alg)
	{
		if (alg == null)
			throw new RuntimeException("Please, give me an existing weighted voting algorithm.");

		algorithm = alg;
		return this;
	}

	private WeightedVotingFusionAlgorithm<IT,LT> algorithm;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public
	void processJob(final String... args)
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		//check the minimum number of input parameters, should be odd number
		if (args.length < 5 || (args.length&1)==0)
		{
			//print help
			log.info("Usage: img1 weight1 ... imgN weightN TRAimg threshold outImg");
			log.info("All img1 (path to an image file) are TRA marker-wise combined into output outImg.");
			throw new RuntimeException("At least one input image, exactly one marker image and one treshold plus one output image are expected.");
		}

		//the number of input pairs, the test above enforces it is nicely divisible by 2
		final int inputImagesCount = (args.length-3) / 2;

		//container to store the input images
		final Vector<RandomAccessibleInterval<IT>> inImgs = new Vector<>(inputImagesCount);

		//container to store the input weights
		final Vector<Double> inWeights = new Vector<>(inputImagesCount);

		//marker image
		Img<LT> markerImg = null;

		//now, try to load the input images
		Img<IT> img = null;
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
				if (!(img.firstElement() instanceof IntegerType<?>))
					throw new RuntimeException("Markers must be stored in an integer-type image, e.g., 8bits or 16bits gray image.");
				markerImg = (Img<LT>)img;
			}

			//also parse and store the weight
			if (i < inputImagesCount)
				inWeights.add( Double.parseDouble(args[2*i +1]) );
		}

		//parse threshold value
		final float threshold = Float.parseFloat(args[args.length-2]);

		//since the simplifiedIO() returns actually always ImgPlus,
		//we better strip away the "plus" extras to make it pure Img<>
		if (markerImg instanceof ImgPlus)
			markerImg = ((ImgPlus<LT>)markerImg).getImg();

		//setup the debug image filename
		/*
		String newName = args[args.length-1];
		final int dotSeparatorIdx = newName.lastIndexOf(".");
		newName = new String(newName.substring(0, dotSeparatorIdx)+"__DBG"+newName.substring(dotSeparatorIdx));
		*/

		log.info("calling weighted voting algorithm with threshold="+threshold);
		algorithm.setWeights(inWeights);
		algorithm.setThreshold(threshold);
		final Img<LT> outImg = algorithm.fuse(inImgs, markerImg);

		log.info("Saving file: "+args[args.length-1]);
		SimplifiedIO.saveImage(outImg, args[args.length-1]);
	}
}

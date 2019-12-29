package de.mpicbg.ulman.ctc;

import de.mpicbg.ulman.ctc.util.Jaccard;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;

public class testJaccard
{
	public static void main(final String... args)
	{
		final String fileImgA = "/home/ulman/data/MAC20/02_bestIndiv_CVUT-CZ/mask035.tif";
		final String fileImgB = "/home/ulman/data/MAC20/02_manAnnot/man_seg035.tif";

		Img<?> imgA = ImageJFunctions.wrap( new ImagePlus( fileImgA ));
		Img<?> imgB = ImageJFunctions.wrap( new ImagePlus( fileImgB ));

		// ------ next test ------
		System.out.println("Jaccard   is " + Jaccard.Jaccard(
			(RandomAccessibleInterval)imgA,881,
			(RandomAccessibleInterval)imgB,17) );

		System.out.println("JaccardLB is " + Jaccard.JaccardLB(
			(RandomAccessibleInterval)imgA,881,
			(RandomAccessibleInterval)imgB,17) );

		// ------ next test ------
		System.out.println("Jaccard   is " + Jaccard.Jaccard(
			(RandomAccessibleInterval)imgA,373,
			(RandomAccessibleInterval)imgB,3) );

		System.out.println("JaccardLB is " + Jaccard.JaccardLB(
			(RandomAccessibleInterval)imgA,373,
			(RandomAccessibleInterval)imgB,3) );
	}
}

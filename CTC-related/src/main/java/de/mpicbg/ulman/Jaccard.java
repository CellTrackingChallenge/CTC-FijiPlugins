package de.mpicbg.ulman;

import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;

public class Jaccard {
    static public < T extends IntegerType<T> >
    double Jaccard( final RandomAccessibleInterval<T> imgA, final RandomAccessibleInterval<T> imgB )
    {
        //sanity checks first: images must be of the same size
        if (imgA.numDimensions() != imgB.numDimensions())
            throw new IllegalArgumentException("Both input images have to be of the same dimension.");

        final int n = imgA.numDimensions();
        int i = 0;
        while ( i < n && imgA.dimension(i) ==  imgB.dimension(i) ) ++i;
        if (i < n)
            throw new IllegalArgumentException("Images differ in size in dimension "+i+".");

        //Jaccard is size of an intersection over size of an union,
        //and we consider binary images...
        long interSize = 0;
        long unionSize = 0;

        //sweeping stuff
        final Cursor<T> cA = Views.iterable(imgA).localizingCursor();
        final RandomAccess<T> cB = imgB.randomAccess();

        while (cA.hasNext())
        {
            final boolean isFGA = cA.next().getInteger() > 0;

            cB.setPosition( cA );
            final boolean isFGB = cB.get().getInteger() > 0;

            interSize += isFGA && isFGB ? 1 : 0;
            unionSize += isFGA || isFGB ? 1 : 0;
        }

        return ( (double)interSize / (double)unionSize );
    }


    public static void main(final String... args)
    {
        final String fileImgA = "/tmp/000117.raw.tif";
        final String fileImgB = "/tmp/000117.raw.tif";

        Img<?> imgA = ImageJFunctions.wrap( new ImagePlus( fileImgA ));
        Img<?> imgB = ImageJFunctions.wrap( new ImagePlus( fileImgB ));
        System.out.println("Jaccard is " + Jaccard((RandomAccessibleInterval)imgA,(RandomAccessibleInterval)imgB) );
    }
}

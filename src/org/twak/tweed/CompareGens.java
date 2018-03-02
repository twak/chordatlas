package org.twak.tweed;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.gen.BlockGen;
import org.twak.tweed.gen.SkelGen;
import org.twak.utils.Cache;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.ui.Show;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;

public class CompareGens {

	public CompareGens( SkelGen skelGen, BlockGen blockGen ) {
		
		new Thread() {
			@Override
			public void run() {
				go (skelGen, blockGen);
			}
		}.start();
	}		
	
	private void go( SkelGen skelGen, BlockGen blockGen ) {
		
		LoopL<Point2d> pts = Loopz.toXZLoop( blockGen.polies );
		
		double[] minMax = Loopz.minMax2d( pts );

		double skirt = 20;
		minMax[0] -= skirt;
		minMax[1] += skirt;
		minMax[2] -= skirt;
		minMax[3] += skirt;
		
		double sample = 0.5;

		int xMin = (int) (minMax[0] / sample),
			yMin = (int) (minMax[2] / sample),
			xRange = (int)  Math.ceil( minMax[1] / sample ) - xMin,
			yRange = (int)  Math.ceil( minMax[3] / sample ) - yMin;
		
		double[][] dists = new double[xRange][yRange];
		
		double 
				minD = Double.MAX_VALUE, 
				maxD = -Double.MAX_VALUE;
		
		double mse = 0;
		int ptCount = 0;
		
		for (int xi = 0; xi < xRange; xi++) {
			System.out.println( xi + "/" + xRange );
			for (int yi = 0; yi < yRange; yi++) {
		
				double x = ( xi + xMin ) * sample;
				double y = ( yi + yMin ) * sample;
				
				Point2d p2d = new Point2d( x, y );
				
//				if ( Loopz.inside( p2d, pts ) ) {

					CollisionResults resultsB = new CollisionResults();
					blockGen.gNode.collideWith( new Ray( Jme3z.toJmeV( x, 0, y ), Jme3z.UP ), resultsB );
					CollisionResult crB = resultsB.getFarthestCollision();
					
					CollisionResults resultsS = new CollisionResults();
					skelGen.gNode.collideWith( new Ray( Jme3z.toJmeV( x, 0, y ), Jme3z.UP ), resultsS );
					CollisionResult crS = resultsS.getFarthestCollision();

					if ( crB != null && crS != null ) {
						
						double dist = Math.abs( crB.getDistance()-crS.getDistance() );
						minD = Math.min( minD, dist );
						maxD = Math.max( maxD, dist );
						dists[xi][yi] = dist;
						
						mse += dist * dist;
						ptCount ++;
						
					} else
						dists[xi][yi] = Double.NaN;
				}
			}
		
		BufferedImage render = new BufferedImage( xRange, yRange, BufferedImage.TYPE_3BYTE_BGR );
		WritableRaster raster = render.getRaster();
		
		for (int xi = 0; xi < xRange; xi++)
			for (int yi = 0; yi < yRange; yi++) {
				double d = dists[xi][yi];
				if (Double.isNaN( d ))
					raster.setPixel( xi, yi, new int[] {0,0,0} );
				else {
					d = (d - minD) / (maxD - minD);
					raster.setPixel( xi, yi, colourMap(d) );
				}
			}
		
		double range = 150;
		for (int y = 0; y < range; y++) {
			for (int x = 0; x < 5; x++) 
				raster.setPixel( x, y, colourMap( y / range ) );
		}
		
		try {
			ImageIO.write( render, "png", new File( Tweed.SCRATCH + "distanceMap" ) );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
		
		
		System.out.println ( "min : " + minD + "max : " + maxD + " mse: " + (mse/ptCount) );
		
		new Show( render );
	}
	
	private int[] colourMap( double d ) {
		
		int c = Color.HSBtoRGB( (float)( d * 0.8 ), 1, 1f );
		
		int r = c & 0xFF;
		int g = (c & 0xFF00) >> 8;
		int b = (c & 0xFF0000) >> 16;
		
		return new int[] {r,g,b};
	}
}

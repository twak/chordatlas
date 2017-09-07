package org.twak.tweed.tools;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.dbg.Imagez;
import org.twak.tweed.gen.BlockGen;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.gen.GISGen.Mode;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.Pano;
import org.twak.tweed.gen.PanoGen;
import org.twak.tweed.gen.PlaneGen;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.ObjRead;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.FacadeFinder.FacadeMode;
import org.twak.viewTrace.FacadeFinder.ToProjMega;
import org.twak.viewTrace.FacadeFinder.ToProject;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;

public class FacadeTool extends SelectTool {

	public static final String LINE_XML = "line.xml";
	long lastClick = 0;
//	FacadeFinder ff;

	public FacadeTool( Tweed tweed ) {
		super( tweed );
	}

	@Override
	public void clickedOn( Spatial target, Vector3f vector3f, Vector2f cursorPosition ) {
		if ( System.currentTimeMillis() - lastClick > 500 ) {

			Object[] directHandler = target.getUserData( ClickMe.class.getSimpleName() );

			if ( directHandler != null )
				( (ClickMe) directHandler[ 0 ] ).clicked( vector3f );

			lastClick = System.currentTimeMillis();
		}
	}

	@Override
	public String getName() {
		return "render panoramas";
	}

	@Override
	public void selected( LoopL<Point3d> list ) {
		// fixme, we don't use this code path anymore
	}

	public void facadeSelected( LoopL<Point3d> list, BlockGen block ) {

		//		if (block == null) {
		//			JOptionPane.showMessageDialog( null, "generate mesh for block first" );
		//			return;
		//		}
		
		double[] minMax = Loopz.minMax( list );// Polygonz.findBounds( polies, min, max );

		Loopz.expand( minMax, 30 );

		Map<Point2d, Pano> panos = new HashMap<>();

		for ( Gen gen : tweed.frame.gens( PanoGen.class ) )
			for ( Pano pg : ( (PanoGen) gen ).getPanos() ) {
				Point2d pt = new Point2d( pg.location.x, pg.location.z );

				if ( pt.x > minMax[ 0 ] && pt.x < minMax[ 1 ] && pt.y > minMax[ 4 ] && pt.y < minMax[ 5 ] )

					panos.put( pt, pg );
			}

		List<Point3d> objPoints = null;
		if ( block != null ) {
			objPoints = new ObjRead( block.getCroppedFile() ).points();
		}

		FacadeFinder ff = new FacadeFinder( Loopz.toXZLoop( list ), panos, objPoints, block );

		Point2d cen = Loopz.average( Loopz.to2dLoop( list, 1, null ) );
		
		renderFacades( block == null ? null : block.gNode, cen.x+"_"+cen.y, ff );
	}

//	private static String FACADE_FOLDER = ;

	private static final float pixelsPerMeter = 40f;
	
	private void renderFacades(Node gNode, String blockName, FacadeFinder ff) {

//		if ( GMLGen.mode == Mode.RENDER_SELECTED_FACADE )
//			tweed.enqueue( new Runnable() {
//				@Override
//				public void run() {
//					tweed.frame.removeGens(PlaneGen.class);
//				}
//			} );

		Thread thread = new Thread() {
			@Override
			public void run() {

				File blockFile = new File (Tweed.DATA + File.separator + "features_rendered"+File.separator+blockName );
						
				if ( GISGen.mode == Mode.RENDER_SELECTED_FACADE )
					try {
						FileUtils.deleteDirectory( blockFile );
					} catch ( IOException e1 ) {
						e1.printStackTrace();
					}

				
				for ( int mfi = 0; mfi < ff.results.size(); mfi++ ) {

					ToProjMega tpm = ff.results.get( mfi );

					File megaFolder = new File( blockFile, ""+ mfi );
					megaFolder.mkdirs();

					try {
						if (tpm.masses != null)
							new XStream().toXML( tpm.masses, new FileOutputStream( new File( megaFolder, "masses.xml" ) ) );
						
						new XStream().toXML( tpm.megafacade, new FileOutputStream( new File( megaFolder, LINE_XML ) ) );

					} catch ( FileNotFoundException e ) {
						e.printStackTrace();
					}

					// print a list of panoramas on this side.


					List<Double> rots = new ArrayList();
					
					for (ToProject tp : tpm)
					for ( Pano pano : tp.toProject ) {
						PlaneGen pg = new PlaneGen( tweed, (float) tp.e.x, (float) tp.e.y, (float) tp.s.x, (float) tp.s.y, (float) tp.minHeight, (float) tp.maxHeight, tp.toProject );
						pg.fudgeToDepth( pixelsPerMeter, pano, rots );
					}
					
					double rot = 0;
					if (!rots.isEmpty())
						rot = biggestClusterMean(rots);// rots.stream().mapToDouble( x -> x ).average().getAsDouble();
					
					System.out.println ( "avg rot was "+rot );

					List<BufferedImage> images = new ArrayList<>();
					
					for ( int fc = 0; fc < tpm.size(); fc++ ) {

//						if (mfi != 2 || fc != 0)
//							continue;
						
						ToProject tp = tpm.get( fc );

						//					if (!tp.toProject.iterator().next().name.contains( "hfhGoIsR24hezjXpuIqklw" ))
						//						continue;

						System.out.println( "mega " + mfi + " pano " + fc );
						File imageFolder;
						String imageFilename = null;

						if ( GISGen.mode == Mode.RENDER_SELECTED_FACADE ) {
							imageFolder = new File( megaFolder, "" + fc );
							imageFilename = "orthographic";
						}
						else
							imageFolder = new File( blockName );

						imageFolder.mkdirs();

						if ( tp.toProject.size() != 1 )
							throw new Error();

						PlaneGen pg = new PlaneGen( tweed, (float) tp.e.x, (float) tp.e.y, (float) tp.s.x, (float) tp.s.y, (float) tp.minHeight, (float) tp.maxHeight, tp.toProject );
						tweed.frame.addGen( pg, true );
						
						for ( Pano pano_ : tp.toProject ) {

							Pano pano = new Pano (pano_);
							pano.set( pano.oa1 - (float) rot, pano.oa2, pano.oa3 );

							if (imageFilename == null)
								imageFilename = new File (pano.name).getName() + "_" + tpm.megafacade.start + "_" + tpm.megafacade.end;
							
							images.add ( pg.render( imageFolder, pixelsPerMeter, pano, tpm.megafacade, imageFilename ) );

							try {

								
								FileWriter out = new FileWriter( new File( imageFolder, "meta.txt" ) );

								out.write( pixelsPerMeter * 10 + " " + ( tp.s.distance( tp.e ) * pixelsPerMeter - pixelsPerMeter * 20 ) + " " + ( tp.maxHeight - tp.minHeight ) * pixelsPerMeter + "\n" );
								out.write( pg.toString() + "\n" );
								//							out.write ( fudged.toString()+"\n" );
								out.write( pano.orig.getName() + "\n" );
								
								Point2d cen = tpm.megafacade.project( new Point2d (pano.location.x, pano.location.z ) , false );
								
								out.write( 
										tp.s.x +" " + tp.s.y + " " +  
										tp.e.x +" " + tp.e.y+ " " +
										cen.x +" "		+
										cen.y +" "		+
										pano.location.x +" "+
										pano.location.z + "\n" ); 

								out.close();

							} catch ( Throwable th ) {
								th.printStackTrace();
							}
						}
					}
					
					if (GISGen.mode == Mode.RENDER_SELECTED_FACADE)
						Imagez.writeSummary (new File (megaFolder, "summary.png"), images);
				}
			}

			

		};

		if ( GISGen.mode == Mode.RENDER_SELECTED_FACADE )
			thread.start();
		else
			thread.run();
	}
	private static double biggestClusterMean( List<Double> rots ) {
		
		if (rots.isEmpty())
			return 0;
		
		class Wrapper implements Clusterable {

			double[] rot;
			
			public Wrapper(double r) {
				this.rot = new double[] {r};
			}
			
			@Override
			public double[] getPoint() {
				return rot;
			}
			
		}
		
		List<Wrapper> toCluster = rots.stream().map(x -> new Wrapper(x) ).collect( Collectors.toList() );

		List<CentroidCluster<Wrapper>> results = new KMeansPlusPlusClusterer<Wrapper>( 5, 1000 ).cluster( toCluster );
//
//		DBSCANClusterer<Wrapper> cr = new MultiKM DBSCANClusterer<>( 0.05, 100 );
//		List<Cluster<Wrapper>> results = cr.cluster( toCluster );
		CentroidCluster<Wrapper> biggest = results.stream().max(
				( a, b ) -> Double.compare( a.getPoints().size(), b.getPoints().size() ) ).get();
		
		return biggest.getCenter().getPoint()[0];// getPoints().stream().mapToDouble( x -> x.rot[0] ).average().getAsDouble();
	}

	@Override
	public void getUI( JPanel panel ) {

		JComboBox<GISGen.Mode> allOne = new JComboBox<>();

		allOne.addItem( GISGen.Mode.RENDER_ALL_FACADES );
		allOne.addItem( GISGen.Mode.RENDER_SELECTED_FACADE );
		allOne.addItem( GISGen.Mode.RENDER_SAT );
		allOne.setSelectedItem( GISGen.mode );
		allOne.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				GISGen.mode = (Mode) allOne.getSelectedItem();
			}
		} );
		
		JComboBox<FacadeFinder.FacadeMode> granularity = new JComboBox<>();
		
		granularity.addItem( FacadeMode.PER_GIS         );
		granularity.addItem( FacadeMode.PER_MEGA        );
		granularity.addItem( FacadeMode.PER_CAMERA      );
		granularity.setSelectedItem( FacadeFinder.facadeMode );
		granularity.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				FacadeFinder.facadeMode = (FacadeMode) granularity.getSelectedItem();
			}
		} );
		
		
//		JButton render = new JButton( "render" );
//		render.addActionListener( new ActionListener() {
//			@Override
//			public void actionPerformed( ActionEvent e ) {
//				renderFacades(null);
//			}
//		} );
//		panel.add( render );

		panel.add(allOne);
		panel.add(granularity);
	}
}

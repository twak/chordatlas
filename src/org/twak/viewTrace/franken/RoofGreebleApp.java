package org.twak.viewTrace.franken;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point2d;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.tweed.gen.skel.RoofGreeble;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RoofGreebleApp extends App {

//	private RoofTexApp child;
	private MiniRoof mr;
	public String greebleTex;
	
	public RoofGreebleApp( MiniRoof mr ) {
		super( );
		this.mr = mr;
	}

	public RoofGreebleApp( RoofGreebleApp o ) {

		super( (App) o );
		
		this.mr = o.mr;
	}

	@Override
	public App copy() {
		return new RoofGreebleApp( this );
	}

	@Override
	public App getUp() {
		return mr.superFace.buildingApp;
	}

	@Override
	public MultiMap<String, App> getDown() {
		MultiMap out = new MultiMap<>();
		
		out.put ("roof textures", mr.roofTexApp );
		
		return out;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {

		
		NetInfo ni = NetInfo.get(this);
		Pix2Pix p2 = new Pix2Pix( ni );
		
		int resolution = ni.resolution;

		List<MiniRoof> toProcess = new ArrayList<>();
		
		for (App a : batch) {
			
			if ( a.appMode != TextureMode.Net ) {
//				for (FCircle fc : ((RoofGreebleApp)a).mr.greebles.valueList() )
//					fc.
				continue;
			}
			
			MiniRoof mr = ((RoofGreebleApp) a ).mr;
			mr.clearGreebles();
			toProcess.add(mr);
		}
		
		RoofTexApp.addCoarseRoofInputs( toProcess, p2, resolution, true );
		
		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {
						MiniRoof mr = (MiniRoof)e.getKey();
						
						mr.roofGreebleApp.greebleTex = Pix2Pix.importTexture( e.getValue(), -1, null, null, null, new BufferedImage[3] );
						
						createGreebles(mr, new File (e.getValue().getParentFile(), e.getValue().getName()+"_circles" ) );
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				} finally {
					whenDone.run();
				}
			}

		} ) );		
	}
	
    private final static ObjectMapper om = new ObjectMapper();

    private void createGreebles( MiniRoof mr, File file ) {

		if ( file.exists() ) {

			JsonNode root;
			try {

//				m.mf.featureGen = new FeatureGenerator( m.mf );

				String string = FileUtils.readFileToString( file );
				
				System.out.println(string);
				
				root = om.readTree( string );
				
				NetInfo ni = NetInfo.get( this );
				DRectangle imRect = new DRectangle(0,0,ni.resolution, ni.resolution);

				for ( RoofGreeble f : RoofGreeble.values() ) {

					JsonNode node = root.get( f.name().toLowerCase() );

					if ( node == null )
						continue;

					for ( int i = 0; i < node.size(); i++ ) {

						JsonNode circle = node.get( i );

						double 
								x = circle.get(0).asDouble(), 
								y = ni.resolution - circle.get(1).asDouble(), 
								r = circle.get( 2 ).asDouble();
						
						RoofTexApp rta = mr.roofTexApp;
						
						Point2d worldXY = rta.textureRect.transform( imRect.normalize( new Point2d(x, y) ) );
						
						r = r * rta.textureRect.height / ni.resolution;
						
						mr.addFeature ( f, r, worldXY );
						
//						m.mf.featureGen.add( f, m.mfBounds.transform( m.mask.normalize( r ) ) );

					}
				}

			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}		
	}
}

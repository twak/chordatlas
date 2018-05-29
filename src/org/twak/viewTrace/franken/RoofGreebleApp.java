package org.twak.viewTrace.franken;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.collections.MultiMap;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RoofGreebleApp extends App implements HasApp {

	private RoofTexApp parent;

	public RoofGreebleApp( RoofTexApp parent ) {
		super( (HasApp) null );
		this.hasA = this;
		this.parent = parent;
	}

	public RoofGreebleApp( RoofGreebleApp o ) {

		super( (App) o );
		
		this.parent = o.parent;
	}

	@Override
	public App copy() {
		return new RoofGreebleApp( this );
	}

	@Override
	public App getUp() {
		return parent;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		NetInfo ni = NetInfo.get(this);
		Pix2Pix p2 = new Pix2Pix( ni );
		
		int resolution = ni.resolution;
		
		RoofTexApp.addCoarseRoofInputs( batch, p2, resolution );

		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {
						MiniRoof mr = (MiniRoof)e.getKey();
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

//				for ( Feature f : toGenerate ) {
//
//					JsonNode node = root.get( f.name().toLowerCase() );
//
//					if ( node == null )
//						continue;
//
//					for ( int i = 0; i < node.size(); i++ ) {
//
//						JsonNode rect = node.get( i );
//
//						DRectangle r = new DRectangle( rect.get( 0 ).asDouble(), NetInfo.get( this ).resolution - rect.get( 3 ).asDouble(), rect.get( 1 ).asDouble() - rect.get( 0 ).asDouble(), rect.get( 3 ).asDouble() - rect.get( 2 ).asDouble() );
//
//						m.mf.featureGen.add( f, m.mfBounds.transform( m.mask.normalize( r ) ) );
//
//					}
//				}

			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}		
	}
}
